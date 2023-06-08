// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmanager.h"
#include "minimumusedbitstracker.h"
#include <iomanip>
#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/common/global_bucket_space_distribution_converter.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/storage/config/config-stor-server.h>
#include <vespa/storage/storageutil/distributorstatecache.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/storageframework/generic/status/xmlstatusreporter.h>
#include <vespa/storageframework/generic/thread/thread.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <ranges>
#include <vespa/config/helper/configgetter.hpp>
#include <chrono>
#include <thread>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".storage.bucketdb.manager");

using document::BucketSpace;
using namespace std::chrono_literals;

namespace storage {

BucketManager::BucketManager(const config::ConfigUri & configUri, ServiceLayerComponentRegister& compReg)
    : StorageLink("Bucket manager"),
      framework::StatusReporter("bucketdb", "Bucket database"),
      _configUri(configUri),
      _workerLock(),
      _workerCond(),
      _clusterStateLock(),
      _queueProcessingLock(),
      _queuedReplies(),
      _last_cluster_state_version_initiated(0),
      _last_cluster_state_version_completed(0),
      _doneInitialized(false),
      _requestsCurrentlyProcessing(0),
      _component(compReg, "bucketmanager"),
      _metrics(std::make_shared<BucketManagerMetrics>(_component.getBucketSpaceRepo())),
      _simulated_processing_delay(0)
{
    _component.registerStatusPage(*this);
    _component.registerMetric(*_metrics);
    _component.registerMetricUpdateHook(*this, 300s);

        // Initialize min used bits to default value used here.
    NodeStateUpdater::Lock::SP lock(_component.getStateUpdater().grabStateChangeLock());
    lib::NodeState ns(*_component.getStateUpdater().getReportedNodeState());
    ns.setMinUsedBits(58);
    _component.getStateUpdater().setReportedNodeState(ns);

    auto server_config = config::ConfigGetter<vespa::config::content::core::StorServerConfig>::getConfig(configUri.getConfigId(), configUri.getContext());
    _simulated_processing_delay = std::chrono::milliseconds(std::max(0, server_config->simulatedBucketRequestLatencyMsec));
}

BucketManager::~BucketManager()
{
    if (_thread) {
        LOG(error, "BucketManager deleted without calling close() first");
        onClose();
    }
    LOG(debug, "Deleting link %s.", toString().c_str());
    closeNextLink();
}

void BucketManager::onClose()
{
    // Stop internal thread such that we don't send any more messages down.
    if (_thread) {
        _thread->interruptAndJoin(_workerCond);
        _thread.reset();
    }
}

void
BucketManager::print(std::ostream& out, bool ,const std::string& ) const
{
    out << "BucketManager()";
}

namespace {

template<bool log>
class DistributorInfoGatherer
{
    using ResultArray = api::RequestBucketInfoReply::EntryVector;

    DistributorStateCache                      _state;
    std::unordered_map<uint16_t, ResultArray>& _result;
    const document::BucketIdFactory&           _factory;
    std::shared_ptr<const lib::Distribution>  _storageDistribution;

public:
    DistributorInfoGatherer(
            const lib::ClusterState& systemState,
            std::unordered_map<uint16_t, ResultArray>& result,
            const document::BucketIdFactory& factory,
            std::shared_ptr<const lib::Distribution> distribution)
        : _state(*distribution, systemState),
          _result(result),
          _factory(factory),
          _storageDistribution(std::move(distribution))
    {
    }

    StorBucketDatabase::Decision operator()(uint64_t bucketId,const StorBucketDatabase::Entry& data)
    {
        document::BucketId b(document::BucketId::keyToBucketId(bucketId));
        try{
            uint16_t i = _state.getOwner(b);
            auto it = _result.find(i);
            if constexpr (log) {
                LOG(spam, "Bucket %s (reverse %" PRIu64 "), should be handled by distributor %u which we are %sgenerating state for.",
                    b.toString().c_str(), bucketId, i, it == _result.end() ? "not " : "");
            }
            if (it != _result.end()) {
                api::RequestBucketInfoReply::Entry entry;
                entry._bucketId = b;
                entry._info = data.getBucketInfo();
                it->second.push_back(entry);
            }
        } catch (lib::TooFewBucketBitsInUseException& e) {
            LOGBP(warning, "Cannot assign bucket %s to a distributor as bucket only specifies %u bits.",
                  b.toString().c_str(), b.getUsedBits());
        } catch (lib::NoDistributorsAvailableException& e) {
            LOGBP(warning, "No distributors available while processing request bucket info. Distribution hash: %s, cluster state: %s",
                  _state.getDistribution().getNodeGraph().getDistributionConfigHash().c_str(), _state.getClusterState().toString().c_str());
        }
        return StorBucketDatabase::Decision::CONTINUE;
    }

};

struct MetricsUpdater {
    struct Count {
        uint64_t docs;
        uint64_t bytes;
        uint64_t buckets;
        uint64_t active;
        uint64_t ready;

        constexpr Count() noexcept : docs(0), bytes(0), buckets(0), active(0), ready(0) {}
    };

    Count    count;
    uint32_t lowestUsedBit;

    constexpr MetricsUpdater() noexcept
        : count(), lowestUsedBit(58) {}

    void operator()(document::BucketId::Type bucketId,
                    const StorBucketDatabase::Entry& data) noexcept
    {
        document::BucketId bucket(document::BucketId::keyToBucketId(bucketId));

        if (data.valid()) {
            ++count.buckets;
            if (data.getBucketInfo().isActive()) {
                ++count.active;
            }
            if (data.getBucketInfo().isReady()) {
                ++count.ready;
            }
            count.docs += data.getBucketInfo().getDocumentCount();
            count.bytes += data.getBucketInfo().getTotalDocumentSize();

            if (bucket.getUsedBits() < lowestUsedBit) {
                lowestUsedBit = bucket.getUsedBits();
            }
        }
    };

    void add(const MetricsUpdater& rhs) noexcept {
        auto& d = count;
        const auto& s = rhs.count;
        d.buckets += s.buckets;
        d.docs    += s.docs;
        d.bytes   += s.bytes;
        d.ready   += s.ready;
        d.active  += s.active;
    }
};

}   // End of anonymous namespace

StorBucketDatabase::Entry
BucketManager::getBucketInfo(const document::Bucket &bucket) const
{
    StorBucketDatabase::WrappedEntry entry(_component.getBucketDatabase(bucket.getBucketSpace()).get(bucket.getBucketId(), "BucketManager::getBucketInfo"));
    return *entry;
}

void
BucketManager::updateMetrics(bool updateDocCount)
{
    LOG(debug, "Iterating bucket database to update metrics%s%s",
        updateDocCount ? "" : ", minusedbits only",
        _doneInitialized ? "" : ", server is not done initializing");

    if (!updateDocCount || _doneInitialized) {
        MetricsUpdater total;
        for (const auto& space : _component.getBucketSpaceRepo()) {
            MetricsUpdater m;
            auto guard = space.second->bucketDatabase().acquire_read_guard();
            guard->for_each(std::ref(m));
            total.add(m);
            if (updateDocCount) {
                auto bm = _metrics->bucket_spaces.find(space.first);
                assert(bm != _metrics->bucket_spaces.end());
                bm->second->buckets_total.set(m.count.buckets);
                bm->second->docs.set(m.count.docs);
                bm->second->bytes.set(m.count.bytes);
                bm->second->active_buckets.set(m.count.active);
                bm->second->ready_buckets.set(m.count.ready);
            }
        }
        if (updateDocCount) {
            auto & dest = *_metrics->disk;
            const auto & src = total.count;
            dest.buckets.addValue(src.buckets);
            dest.docs.addValue(src.docs);
            dest.bytes.addValue(src.bytes);
            dest.active.addValue(src.active);
            dest.ready.addValue(src.ready);
        }
    }
    update_bucket_db_memory_usage_metrics();
}

void BucketManager::update_bucket_db_memory_usage_metrics() {
    for (const auto& space : _component.getBucketSpaceRepo()) {
        auto bm = _metrics->bucket_spaces.find(space.first);
        bm->second->bucket_db_metrics.memory_usage.update(space.second->bucketDatabase().detailed_memory_usage());
    }
}

void BucketManager::updateMinUsedBits()
{
    MetricsUpdater m;
    _component.getBucketSpaceRepo().for_each_bucket(std::ref(m));
    // When going through to get sizes, we also record min bits
    MinimumUsedBitsTracker& bitTracker(_component.getMinUsedBitsTracker());
    if (bitTracker.getMinUsedBits() != m.lowestUsedBit) {
        NodeStateUpdater::Lock::SP lock(_component.getStateUpdater().grabStateChangeLock());
        lib::NodeState ns(*_component.getStateUpdater().getReportedNodeState());
        bitTracker.setMinUsedBits(m.lowestUsedBit);
        ns.setMinUsedBits(m.lowestUsedBit);
        _component.getStateUpdater().setReportedNodeState(ns);
    }
}

// Responsible for sending on messages that was previously queued
void BucketManager::run(framework::ThreadHandle& thread)
{
    constexpr vespalib::duration CHECK_MINUSEDBITS_INTERVAL = 30s;
    vespalib::steady_time timeToCheckMinUsedBits = vespalib::steady_time::min();
    while (!thread.interrupted()) {
        bool didWork = false;
        BucketInfoRequestMap infoReqs;
        {
            std::lock_guard guard(_workerLock);
            infoReqs.swap(_bucketInfoRequests);
        }

        for (auto &req : infoReqs) {
            didWork |= processRequestBucketInfoCommands(req.first, req.second);
        }

        {
            std::unique_lock guard(_workerLock);
            for (const auto &req : infoReqs) {
                assert(req.second.empty());
            }
            if (!didWork) {
                _workerCond.wait_for(guard, 1s);
                thread.registerTick(framework::WAIT_CYCLE);
            } else {
                thread.registerTick(framework::PROCESS_CYCLE);
            }
        }
        if (timeToCheckMinUsedBits < _component.getClock().getMonotonicTime()) {
            updateMinUsedBits();
            timeToCheckMinUsedBits = _component.getClock().getMonotonicTime() + CHECK_MINUSEDBITS_INTERVAL;
        }
    }
}

vespalib::string
BucketManager::getReportContentType(const framework::HttpUrlPath& path) const
{
    bool showAll = path.hasAttribute("showall");
    if (showAll) {
        return "application/xml";
    } else {
        return "text/html";
    }
}

namespace {
    class BucketDBDumper {
        vespalib::XmlOutputStream& _xos;
    public:
        explicit BucketDBDumper(vespalib::XmlOutputStream& xos) : _xos(xos) {}

        void operator()(uint64_t bucketId, const StorBucketDatabase::Entry& info) {
            using namespace vespalib::xml;
            document::BucketId bucket(
                    document::BucketId::keyToBucketId(bucketId));

            std::ostringstream ost;
            ost << "0x" << std::hex << std::setw(16)
                << std::setfill('0') << bucket.getId();

            _xos << XmlTag("bucket")
                 << XmlAttribute("id", ost.str());
            info.getBucketInfo().printXml(_xos);
            _xos << XmlEndTag();
        };
    };
}

bool
BucketManager::reportStatus(std::ostream& out,
                            const framework::HttpUrlPath& path) const
{
    bool showAll = path.hasAttribute("showall");
    if (showAll) {
        framework::PartlyXmlStatusReporter xmlReporter(*this, out, path);

        using vespalib::xml::XmlTag;
        using vespalib::xml::XmlEndTag;
        using vespalib::xml::XmlAttribute;

        xmlReporter << vespalib::xml::XmlTag("buckets");
        for (const auto& space : _component.getBucketSpaceRepo()) {
            xmlReporter << XmlTag("bucket-space")
                        << XmlAttribute("name", document::FixedBucketSpaces::to_string(space.first));
            BucketDBDumper dumper(xmlReporter.getStream());
            auto guard = _component.getBucketSpaceRepo().get(space.first).bucketDatabase().acquire_read_guard();
            guard->for_each(std::ref(dumper));
            xmlReporter << XmlEndTag();
        }
        xmlReporter << XmlEndTag();
    } else {
        framework::PartlyHtmlStatusReporter htmlReporter(*this);
        htmlReporter.reportHtmlHeader(out, path);
        // Print menu
        out << "<font size=\"-1\">[ <a href=\"../\">Back to top</a>"
            << " | <a href=\"?showall\">Show all buckets</a> ]</font>";
        htmlReporter.reportHtmlFooter(out, path);
    }
    return true;
}

void
BucketManager::dump(std::ostream& out) const
{
    vespalib::XmlOutputStream xos(out);
    BucketDBDumper dumper(xos);
    _component.getBucketSpaceRepo().for_each_bucket(std::ref(dumper));
}


void BucketManager::onOpen()
{
    if (!_configUri.empty()) {
        startWorkerThread();
    }
}

void BucketManager::startWorkerThread()
{
    _thread = _component.startThread(*this, 30s, 1s);
}

// --------- Commands --------- //

bool BucketManager::onRequestBucketInfo(
            const std::shared_ptr<api::RequestBucketInfoCommand>& cmd)
{
    LOG(debug, "Got request bucket info command %s", cmd->toString().c_str());
    if (cmd->getBuckets().empty() && cmd->hasSystemState()) {

        std::lock_guard guard(_workerLock);
        _bucketInfoRequests[cmd->getBucketSpace()].push_back(cmd);
        _workerCond.notify_all();
        LOG(spam, "Scheduled request bucket info request for retrieval");
        return true;
    }

    ScopedQueueDispatchGuard queueGuard(*this);

    BucketSpace bucketSpace(cmd->getBucketSpace());
    api::RequestBucketInfoReply::EntryVector info;
    if (!cmd->getBuckets().empty()) {
        for (auto bucketId : cmd->getBuckets()) {
            for (const auto & entry : _component.getBucketDatabase(bucketSpace).getAll(bucketId, "BucketManager::onRequestBucketInfo")) {
                info.emplace_back(entry.first, entry.second->getBucketInfo());
            }
        }
    } else {
        LOG(error, "We don't support fetching bucket info without bucket list or system state");
        assert(false);
    }
    _metrics->simpleBucketInfoRequestSize.addValue(info.size());
    auto reply = std::make_shared<api::RequestBucketInfoReply>(*cmd);
    reply->getBucketInfo().swap(info);
    LOG(spam, "Sending %s", reply->toString().c_str());

    LOG(spam, "Returning list of checksums:");
    for (const auto & entry : reply->getBucketInfo()) {
        LOG(spam, "%s: %s", entry._bucketId.toString().c_str(), entry._info.toString().c_str());
    }
    sendUp(reply);
    // Remaining replies dispatched by queueGuard upon function exit.
    return true;
}

BucketManager::ScopedQueueDispatchGuard::ScopedQueueDispatchGuard(BucketManager& mgr)
    : _mgr(mgr)
{
    _mgr.enterQueueProtectedSection();
}

BucketManager::ScopedQueueDispatchGuard::~ScopedQueueDispatchGuard()
{
    _mgr.leaveQueueProtectedSection(*this);
}

void
BucketManager::enterQueueProtectedSection()
{
    std::lock_guard guard(_queueProcessingLock);
    ++_requestsCurrentlyProcessing;
}

void
BucketManager::leaveQueueProtectedSection(ScopedQueueDispatchGuard& queueGuard)
{
    (void) queueGuard; // Only used to enforce guard is held while calling.
    std::lock_guard guard(_queueProcessingLock);
    assert(_requestsCurrentlyProcessing > 0);
    // Full bucket info fetches may be concurrently interleaved with bucket-
    // specific fetches outside of the processing thread. We only allow queued
    // messages to go through once _all_ of these are done, since we do not
    // keep per-bucket info request queues and thus cannot know which replies
    // may alter the relevant state.
    --_requestsCurrentlyProcessing;
    if (_requestsCurrentlyProcessing == 0) {
        for (const auto& qr : _queuedReplies) {
            sendUp(qr);
        }
        _queuedReplies.clear();
        _conflictingBuckets.clear();
    }
}

bool
BucketManager::processRequestBucketInfoCommands(document::BucketSpace bucketSpace,
                                                BucketInfoRequestList &reqs)
{
    if (reqs.empty()) return false;

    ScopedQueueDispatchGuard queueGuard(*this);

    // - Fail all but the latest request for each node.
    // - Fail all requests to a cluster state that after unification differs
    //   from the current cluster state.

    std::set<uint16_t> seenDistributors;
    using RBISP = std::shared_ptr<api::RequestBucketInfoCommand>;
    std::map<uint16_t, RBISP> requests;

    auto distribution(_component.getBucketSpaceRepo().get(bucketSpace).getDistribution());
    auto clusterStateBundle(_component.getStateUpdater().getClusterStateBundle());
    assert(clusterStateBundle);
    lib::ClusterState::CSP clusterState(clusterStateBundle->getDerivedClusterState(bucketSpace));
    assert(clusterState.get());

    const auto our_hash = distribution->getNodeGraph().getDistributionConfigHash();

    LOG(debug, "Processing %zu queued request bucket info commands for bucket space %s. "
        "Using cluster state '%s' and distribution hash '%s'",
        reqs.size(), bucketSpace.toString().c_str(), clusterState->toString().c_str(), our_hash.c_str());

    std::lock_guard clusterStateGuard(_clusterStateLock);
    for (auto it = reqs.rbegin(); it != reqs.rend(); it++) {
        const auto & req = *it;
        // Currently small requests should not be forwarded to worker thread
        assert(req->hasSystemState());
        const auto their_hash = req->getDistributionHash();

        std::ostringstream error;
        if ((clusterState->getVersion() != _last_cluster_state_version_initiated) ||
            (_last_cluster_state_version_initiated != _last_cluster_state_version_completed))
        {
            // Calling onSetSystemState() on _this_ component and actually switching over
            // to another cluster state version does not happen atomically. Detect and
            // gracefully deal with the case where we're not internally in sync.
            error << "Inconsistent internal cluster state on node during transition; "
                  << "failing request from distributor " << req->getDistributor()
                  << " so it can be retried. Node version is " << clusterState->getVersion()
                  << ", last version seen by the bucket manager is " << _last_cluster_state_version_initiated
                  << ", last internally converged version is " << _last_cluster_state_version_completed;
        } else if (req->getSystemState().getVersion() != _last_cluster_state_version_initiated) {
            error << "Ignoring bucket info request for cluster state version "
                  << req->getSystemState().getVersion() << " as newest "
                  << "version we know of is " << _last_cluster_state_version_initiated;
        } else if (!their_hash.empty() && their_hash != our_hash) {
            // Mismatching config hash indicates nodes are out of sync with their config generations
            error << "Distributor config hash is not equal to our own; must reject request (our hash: "
                  << our_hash.c_str() << ", their hash: " << their_hash.c_str() << ")";
        }
        if (error.str().empty()) {
            auto result = seenDistributors.insert(req->getDistributor());
            if (result.second) {
                requests[req->getDistributor()] = req;
                continue;
            } else {
                error << "There is already a newer bucket info request for this"
                      << " node from distributor " << req->getDistributor();
            }
        }
        
    	// If we get here, message should be failed
        auto reply = std::make_shared<api::RequestBucketInfoReply>(*req);
        reply->setResult(api::ReturnCode(api::ReturnCode::REJECTED, error.str()));
        LOG(debug, "Rejecting request from distributor %u: %s",
            req->getDistributor(),
            error.str().c_str());
        sendUp(reply);
    }

    if (requests.empty()) {
        reqs.clear();
        return true; // No need to waste CPU when no requests are left.
    }

    std::ostringstream distrList;
    std::unordered_map<uint16_t, api::RequestBucketInfoReply::EntryVector> result;
    for (const auto& nodeAndCmd : requests) {
        result[nodeAndCmd.first];
        if (LOG_WOULD_LOG(debug)) {
            distrList << ' ' << nodeAndCmd.first;
        }
    }

    if (_simulated_processing_delay.count() > 0) {
        LOG(info, "Simulating bucket processing delay for %" PRIu64 " ms", _simulated_processing_delay.count());
        std::this_thread::sleep_for(_simulated_processing_delay);
    }

    _metrics->fullBucketInfoRequestSize.addValue(requests.size());
    LOG(debug, "Processing %zu bucket info requests for distributors %s, using system state %s",
        requests.size(), distrList.str().c_str(), clusterState->toString().c_str());
   framework::MilliSecTimer runStartTime(_component.getClock());
    // Don't allow logging to lower performance of inner loop.
    // Call other type of instance if logging
    const document::BucketIdFactory& idFac(_component.getBucketIdFactory());
    if (LOG_WOULD_LOG(spam)) {
        DistributorInfoGatherer<true> builder(*clusterState, result, idFac, distribution);
        _component.getBucketDatabase(bucketSpace).for_each_chunked(std::ref(builder),
                        "BucketManager::processRequestBucketInfoCommands-1");
    } else {
        DistributorInfoGatherer<false> builder(*clusterState, result, idFac, distribution);
        _component.getBucketDatabase(bucketSpace).for_each_chunked(std::ref(builder),
                        "BucketManager::processRequestBucketInfoCommands-2");
    }
    _metrics->fullBucketInfoLatency.addValue(runStartTime.getElapsedTimeAsDouble());
    for (const auto& nodeAndCmd : requests) {
        auto reply(std::make_shared<api::RequestBucketInfoReply>(*nodeAndCmd.second));
        reply->getBucketInfo().swap(result[nodeAndCmd.first]);
        sendUp(reply);
    }

    reqs.clear();

    // Remaining replies dispatched by queueGuard upon function exit.
    return true;
}

size_t
BucketManager::bucketInfoRequestsCurrentlyProcessing() const noexcept
{
    std::lock_guard<std::mutex> guard(_queueProcessingLock);
    return _requestsCurrentlyProcessing;
}

bool
BucketManager::onUp(const std::shared_ptr<api::StorageMessage>& msg)
{
    if (!StorageLink::onUp(msg)) {
        sendUp(msg);
    }
    return true;
}

bool
BucketManager::verifyAndUpdateLastModified(api::StorageCommand& cmd,
                                           const document::Bucket &bucket,
                                           uint64_t lastModified)
{
    LOG(spam, "Received operation %s with modification timestamp %" PRIu64,
        cmd.toString().c_str(),
        lastModified);

    uint64_t prevLastModified = 0;

    {
        StorBucketDatabase::WrappedEntry entry(_component.getBucketDatabase(bucket.getBucketSpace()).get(bucket.getBucketId(), "BucketManager::verify"));

        if (entry.exists()) {
            prevLastModified = entry->info.getLastModified();

            if (lastModified > prevLastModified) {
                entry->info.setLastModified(lastModified);
                entry.write();
                return true;
            }
        } else {
            return true;
        }
    }

    api::StorageReply::UP reply = cmd.makeReply();
    reply->setResult(api::ReturnCode(api::ReturnCode::STALE_TIMESTAMP,
                             vespalib::make_string(
                                     "Received command %s with a lower/equal timestamp (%" PRIu64 ") than the last "
                                     "operation received for bucket %s, with timestamp %" PRIu64,
                                     cmd.toString().c_str(), lastModified, bucket.toString().c_str(), prevLastModified)));


    sendUp(api::StorageMessage::SP(reply.release()));
    return false;
}

bool
BucketManager::onSetSystemState(const std::shared_ptr<api::SetSystemStateCommand>& cmd)
{
    LOG(debug, "onSetSystemState(%s)", cmd->toString().c_str());
    const lib::ClusterState& state(cmd->getSystemState());
    std::lock_guard lock(_clusterStateLock);
    // At this point, the incoming cluster state version has not yet been enabled on this node;
    // it's on its merry way down to the StateManager which handles internal transitions.
    // We must take note of the fact that it will _soon_ be enabled, and must avoid processing
    // any bucket info requests until any and all side effects caused by this process are
    // visible in the bucket DB. Failure to enforce this may cause bucket DB iterations to happen
    // concurrently with transition-induced mutations, causing inconsistent results.
    // We will not allow processing bucket info fetches until we've observed the _reply_ of the
    // state command. Such replies are always ordered _after_ side effects of state enabling have
    // become visible in the bucket DB. See onSetSystemStateReply().
    _last_cluster_state_version_initiated = state.getVersion();
    return false;
}

bool
BucketManager::onSetSystemStateReply(const std::shared_ptr<api::SetSystemStateReply>& reply)
{
    LOG(debug, "onSetSystemStateReply(%s)", reply->toString().c_str());
    std::lock_guard lock(_clusterStateLock);
    // This forms part 2 of the cluster state visibility barrier that was initiated when
    // we first observed the new cluster state version in onSetSystemState(). After this
    // point, bucket info requests may be processed (assuming no new cluster state versions
    // have arrived concurrently). In the case of racing cluster states, internal FIFO ordering
    // of messages shall ensure we eventually end up with a consistent view of observed versions.
    _last_cluster_state_version_completed = reply->getSystemState().getVersion();
    return false;
}

bool
BucketManager::onCreateBucket(const api::CreateBucketCommand::SP& cmd)
{
    MinimumUsedBitsTracker& bitTracker(_component.getMinUsedBitsTracker());
    if (bitTracker.update(cmd->getBucketId())) {
        NodeStateUpdater::Lock::SP lock(_component.getStateUpdater().grabStateChangeLock());
        lib::NodeState ns(*_component.getStateUpdater().getReportedNodeState());
        ns.setMinUsedBits(bitTracker.getMinUsedBits());
        _component.getStateUpdater().setReportedNodeState(ns);
    }

    return false;
}

bool
BucketManager::onMergeBucket(const api::MergeBucketCommand::SP& cmd)
{
    MinimumUsedBitsTracker& bitTracker(_component.getMinUsedBitsTracker());
    if (bitTracker.update(cmd->getBucketId())) {
        NodeStateUpdater::Lock::SP lock(_component.getStateUpdater().grabStateChangeLock());
        lib::NodeState ns(*_component.getStateUpdater().getReportedNodeState());
        ns.setMinUsedBits(bitTracker.getMinUsedBits());
        _component.getStateUpdater().setReportedNodeState(ns);
    }
    return false;
}

bool
BucketManager::onRemove(const api::RemoveCommand::SP& cmd)
{
    return !verifyAndUpdateLastModified(*cmd, cmd->getBucket(), cmd->getTimestamp());
}

bool
BucketManager::onRemoveReply(const api::RemoveReply::SP& reply)
{
    return enqueueIfBucketHasConflicts(reply);
}

bool
BucketManager::onPut(const api::PutCommand::SP& cmd)
{
    return !verifyAndUpdateLastModified(*cmd, cmd->getBucket(), cmd->getTimestamp());
}

bool
BucketManager::onPutReply(const api::PutReply::SP& reply)
{
    return enqueueIfBucketHasConflicts(reply);
}

bool
BucketManager::onUpdate(const api::UpdateCommand::SP& cmd)
{
    return !verifyAndUpdateLastModified(*cmd, cmd->getBucket(), cmd->getTimestamp());
}

bool
BucketManager::onUpdateReply(const api::UpdateReply::SP& reply)
{
    return enqueueIfBucketHasConflicts(reply);
}

bool
BucketManager::onNotifyBucketChangeReply(const api::NotifyBucketChangeReply::SP&)
{
    // Handling bucket change replies is a no-op.
    return true;
}

bool
BucketManager::enqueueIfBucketHasConflicts(const api::BucketReply::SP& reply)
{
    // Should very rarely contend, since persistence replies are all sent up
    // via a single dispatcher thread.
    std::lock_guard guard(_queueProcessingLock);
    if (_requestsCurrentlyProcessing == 0) {
        return false; // Nothing to do here; pass through reply.
    }
    if (replyConflictsWithConcurrentOperation(*reply)) {
        LOG(debug,
            "Reply %s conflicted with a bucket that has been concurrently "
            "modified while a RequestBucketInfo was active; enqueuing it.",
            reply->toString().c_str());
        _queuedReplies.push_back(reply);
        return true;
    }
    return false; // No conflicting ops in queue.
}

bool
BucketManager::replyConflictsWithConcurrentOperation(
        const api::BucketReply& reply) const
{
    if (bucketHasConflicts(reply.getBucketId())) {
        return true;
    }
    // A Put (or Update/Remove) scheduled towards a bucket that is split or
    // joined will be "remapped" to a new bucket id that is the _result_ of
    // said operation. This means that the bucket id for a split reply and
    // a put reply originally for that bucket will differ and just checking
    // on getBucketId() would not capture all true conflicts. However, replies
    // know whether they've been remapped and we can get the non-remapped
    // bucket from it (the "original" bucket).
    return (reply.hasBeenRemapped()
            && bucketHasConflicts(reply.getOriginalBucketId()));
}

bool
BucketManager::enqueueAsConflictIfProcessingRequest(
        const api::StorageReply::SP& reply)
{
    std::lock_guard guard(_queueProcessingLock);
    if (_requestsCurrentlyProcessing != 0) {
        LOG(debug, "Enqueued %s due to concurrent RequestBucketInfo", reply->toString().c_str());
        _queuedReplies.push_back(reply);
        _conflictingBuckets.insert(reply->getBucketId());
        return true;
    }
    return false;
}

bool
BucketManager::onSplitBucketReply(const api::SplitBucketReply::SP& reply)
{
    return enqueueAsConflictIfProcessingRequest(reply);
}

bool
BucketManager::onJoinBucketsReply(const api::JoinBucketsReply::SP& reply)
{
    return enqueueAsConflictIfProcessingRequest(reply);
}

bool
BucketManager::onDeleteBucketReply(const api::DeleteBucketReply::SP& reply)
{
    return enqueueAsConflictIfProcessingRequest(reply);
}

} // storage

