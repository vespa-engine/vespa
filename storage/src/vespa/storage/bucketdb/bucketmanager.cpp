// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmanager.h"
#include "distribution_hash_normalizer.h"
#include "minimumusedbitstracker.h"
#include "lockablemap.hpp"
#include <iomanip>
#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/common/cluster_state_bundle.h>
#include <vespa/storage/storageutil/distributorstatecache.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/storageframework/generic/status/xmlstatusreporter.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <vespa/storageapi/message/multioperation.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/config/config.h>
#include <chrono>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".storage.bucketdb.manager");

using document::BucketSpace;
using namespace std::chrono_literals;

namespace storage {

BucketManager::BucketManager(const config::ConfigUri & configUri,
                             ServiceLayerComponentRegister& compReg)
    : StorageLinkQueued("Bucket manager", compReg),
      framework::StatusReporter("bucketdb", "Bucket database"),
      _configUri(configUri),
      _workerLock(),
      _workerCond(),
      _clusterStateLock(),
      _queueProcessingLock(),
      _queuedReplies(),
      _firstEqualClusterStateVersion(0),
      _lastClusterStateSeen(0),
      _lastUnifiedClusterState(""),
      _metrics(new BucketManagerMetrics),
      _doneInitialized(false),
      _requestsCurrentlyProcessing(0),
      _component(compReg, "bucketmanager")
{
    _metrics->setDisks(_component.getDiskCount());
    _component.registerStatusPage(*this);
    _component.registerMetric(*_metrics);
    _component.registerMetricUpdateHook(*this, framework::SecondTime(300));

        // Initialize min used bits to default value used here.
    NodeStateUpdater::Lock::SP lock(
            _component.getStateUpdater().grabStateChangeLock());
    lib::NodeState ns(
            *_component.getStateUpdater().getReportedNodeState());
    ns.setMinUsedBits(58);
    _component.getStateUpdater().setReportedNodeState(ns);
}

BucketManager::~BucketManager()
{
    if (_thread.get() != 0) {
        LOG(error, "BucketManager deleted without calling close() first");
        onClose();
    }
    LOG(debug, "Deleting link %s.", toString().c_str());
    closeNextLink();
}

void BucketManager::onClose()
{
    // Stop internal thread such that we don't send any more messages down.
    if (_thread.get() != 0) {
        _thread->interruptAndJoin(_workerLock, _workerCond);
        _thread.reset(0);
    }
    StorageLinkQueued::onClose();
}

void
BucketManager::print(std::ostream& out, bool verbose,
                     const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "BucketManager()";
}

namespace {

    template<bool log>
    class DistributorInfoGatherer
    {
        typedef api::RequestBucketInfoReply::EntryVector ResultArray;
        DistributorStateCache _state;
        std::unordered_map<uint16_t, ResultArray>& _result;
        const document::BucketIdFactory& _factory;
        std::shared_ptr<const lib::Distribution> _storageDistribution;

    public:
        DistributorInfoGatherer(
                const lib::ClusterState& systemState,
                std::unordered_map<uint16_t, ResultArray>& result,
                const document::BucketIdFactory& factory,
                std::shared_ptr<const lib::Distribution> distribution)
            : _state(*distribution, systemState),
              _result(result),
              _factory(factory),
              _storageDistribution(distribution)
        {
        }

        StorBucketDatabase::Decision operator()(uint64_t bucketId,
                                                StorBucketDatabase::Entry& data)
        {
            document::BucketId b(document::BucketId::keyToBucketId(bucketId));
            try{
                uint16_t i = _state.getOwner(b);
                auto it = _result.find(i);
                    // Template parameter. This block should not be included
                    // in version not logging.
                if (log) {
                    LOG(spam, "Bucket %s (reverse %" PRIu64 "), should be handled"
                              " by distributor %u which we are %sgenerating "
                              "state for.",
                        b.toString().c_str(), bucketId, i,
                        it == _result.end() ? "not " : "");
                }
                if (it != _result.end()) {
                    api::RequestBucketInfoReply::Entry entry;
                    entry._bucketId = b;
                    entry._info = data.getBucketInfo();
                    it->second.push_back(entry);
                }
            } catch (lib::TooFewBucketBitsInUseException& e) {
                LOGBP(warning, "Cannot assign bucket %s to a distributor "
                               " as bucket only specifies %u bits.",
                      b.toString().c_str(),
                      b.getUsedBits());
            } catch (lib::NoDistributorsAvailableException& e) {
                LOGBP(warning, "No distributors available while processing "
                               "request bucket info. Distribution hash: %s, "
                               "cluster state: %s",
                      _state.getDistribution().getNodeGraph()
                      .getDistributionConfigHash().c_str(),
                      _state.getClusterState().toString().c_str());
            }
            return StorBucketDatabase::CONTINUE;
        }

    };

    struct MetricsUpdater {
        struct Count {
            uint64_t docs;
            uint64_t bytes;
            uint64_t buckets;
            uint64_t active;
            uint64_t ready;

            Count() : docs(0), bytes(0), buckets(0), active(0), ready(0) {}
        };

        uint16_t diskCount;
        std::vector<Count> disk;
        uint32_t lowestUsedBit;

        MetricsUpdater(uint16_t diskCnt)
            : diskCount(diskCnt), disk(diskCnt), lowestUsedBit(58) {}

        StorBucketDatabase::Decision operator()(
                document::BucketId::Type bucketId,
                StorBucketDatabase::Entry& data)
        {
            document::BucketId bucket(
                    document::BucketId::keyToBucketId(bucketId));

            if (data.valid()) {
                assert(data.disk < diskCount);
                ++disk[data.disk].buckets;
                if (data.getBucketInfo().isActive()) {
                    ++disk[data.disk].active;
                }
                if (data.getBucketInfo().isReady()) {
                    ++disk[data.disk].ready;
                }
                disk[data.disk].docs += data.getBucketInfo().getDocumentCount();
                disk[data.disk].bytes += data.getBucketInfo().getTotalDocumentSize();

                if (bucket.getUsedBits() < lowestUsedBit) {
                    lowestUsedBit = bucket.getUsedBits();
                }
            }

            return StorBucketDatabase::CONTINUE;
        };
    };

}   // End of anonymous namespace

StorBucketDatabase::Entry
BucketManager::getBucketInfo(const document::Bucket &bucket) const
{
    StorBucketDatabase::WrappedEntry entry(
            _component.getBucketDatabase(bucket.getBucketSpace()).get(bucket.getBucketId(), "BucketManager::getBucketInfo"));
    return *entry;
}

void
BucketManager::updateMetrics(bool updateDocCount)
{
    LOG(debug, "Iterating bucket database to update metrics%s%s",
        updateDocCount ? "" : ", minusedbits only",
        _doneInitialized ? "" : ", server is not done initializing");

    uint32_t diskCount = _component.getDiskCount();
    if (!updateDocCount || _doneInitialized) {
        MetricsUpdater m(diskCount);
        _component.getBucketSpaceRepo().forEachBucketChunked(
                m, "BucketManager::updateMetrics");
        if (updateDocCount) {
            for (uint16_t i = 0; i< diskCount; i++) {
                _metrics->disks[i]->buckets.addValue(m.disk[i].buckets);
                _metrics->disks[i]->docs.addValue(m.disk[i].docs);
                _metrics->disks[i]->bytes.addValue(m.disk[i].bytes);
                _metrics->disks[i]->active.addValue(m.disk[i].active);
                _metrics->disks[i]->ready.addValue(m.disk[i].ready);
            }
        }
    }
}

void BucketManager::updateMinUsedBits()
{
    MetricsUpdater m(_component.getDiskCount());
    _component.getBucketSpaceRepo().forEachBucketChunked(
            m, "BucketManager::updateMetrics");
    // When going through to get sizes, we also record min bits
    MinimumUsedBitsTracker& bitTracker(_component.getMinUsedBitsTracker());
    if (bitTracker.getMinUsedBits() != m.lowestUsedBit) {
        NodeStateUpdater::Lock::SP lock(
                _component.getStateUpdater().grabStateChangeLock());
        lib::NodeState ns(
                *_component.getStateUpdater().getReportedNodeState());
        bitTracker.setMinUsedBits(m.lowestUsedBit);
        ns.setMinUsedBits(m.lowestUsedBit);
        _component.getStateUpdater().setReportedNodeState(ns);
    }
}

// Responsible for sending on messages that was previously queued
void BucketManager::run(framework::ThreadHandle& thread)
{
    const int64_t CHECK_MINUSEDBITS_INTERVAL = 1000*30;
    framework::MilliSecTime timeToCheckMinUsedBits(0);
    while (!thread.interrupted()) {
        bool didWork = false;
        BucketInfoRequestMap infoReqs;
        {
            std::lock_guard<std::mutex> guard(_workerLock);
            infoReqs.swap(_bucketInfoRequests);
        }

        for (auto &req : infoReqs) {
            didWork |= processRequestBucketInfoCommands(req.first, req.second);
        }

        {
            std::unique_lock<std::mutex> guard(_workerLock);
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
        if (timeToCheckMinUsedBits < _component.getClock().getTimeInMillis()) {
            updateMinUsedBits();
            timeToCheckMinUsedBits = _component.getClock().getTimeInMillis();
            timeToCheckMinUsedBits += framework::MilliSecTime(CHECK_MINUSEDBITS_INTERVAL);
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
        BucketDBDumper(vespalib::XmlOutputStream& xos) : _xos(xos) {}

        StorBucketDatabase::Decision operator()(
                uint64_t bucketId, StorBucketDatabase::Entry& info)
        {
            using namespace vespalib::xml;
            document::BucketId bucket(
                    document::BucketId::keyToBucketId(bucketId));

            std::ostringstream ost;
            ost << "0x" << std::hex << std::setw(16)
                << std::setfill('0') << bucket.getId();

            _xos << XmlTag("bucket")
                 << XmlAttribute("id", ost.str());
            info.getBucketInfo().printXml(_xos);
            _xos << XmlAttribute("disk", info.disk);
            _xos << XmlEndTag();
            return StorBucketDatabase::CONTINUE;
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
        for (auto& space : _component.getBucketSpaceRepo()) {
            xmlReporter << XmlTag("bucket-space")
                        << XmlAttribute("name", document::FixedBucketSpaces::to_string(space.first));
            BucketDBDumper dumper(xmlReporter.getStream());
            _component.getBucketSpaceRepo().get(space.first).bucketDatabase().chunkedAll(
                    dumper, "BucketManager::reportStatus");
            xmlReporter << XmlEndTag();
        }
        xmlReporter << XmlEndTag();
    } else {
        framework::PartlyHtmlStatusReporter htmlReporter(*this);
        htmlReporter.reportHtmlHeader(out, path);
            // Print menu
        out << "<font size=\"-1\">[ <a href=\"/\">Back to top</a>"
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
    _component.getBucketSpaceRepo().forEachBucketChunked(dumper, "BucketManager::dump");
}


void BucketManager::onOpen()
{
    if (!_configUri.empty()) {
        startWorkerThread();
    }
}

void BucketManager::startWorkerThread()
{
    framework::MilliSecTime maxProcessingTime(30 * 1000);
    framework::MilliSecTime waitTime(1000);
    _thread = _component.startThread(*this, maxProcessingTime, waitTime);
}

void BucketManager::onFlush(bool downwards)
{
    StorageLinkQueued::onFlush(downwards);
}

// --------- Commands --------- //

bool BucketManager::onRequestBucketInfo(
            const std::shared_ptr<api::RequestBucketInfoCommand>& cmd)
{
    LOG(debug, "Got request bucket info command");
    if (cmd->getBuckets().size() == 0 && cmd->hasSystemState()) {

        std::lock_guard<std::mutex> guard(_workerLock);
        _bucketInfoRequests[cmd->getBucketSpace()].push_back(cmd);
        _workerCond.notify_all();
        LOG(spam, "Scheduled request bucket info request for retrieval");
        return true;
    }

    ScopedQueueDispatchGuard queueGuard(*this);

    BucketSpace bucketSpace(cmd->getBucketSpace());
    api::RequestBucketInfoReply::EntryVector info;
    if (cmd->getBuckets().size()) {
        typedef std::map<document::BucketId,
                         StorBucketDatabase::WrappedEntry> BucketMap;
        for (uint32_t i = 0; i < cmd->getBuckets().size(); i++) {
            BucketMap entries(_component.getBucketDatabase(bucketSpace).getAll(
                                    cmd->getBuckets()[i],
                                    "BucketManager::onRequestBucketInfo"));
            for (BucketMap::iterator it = entries.begin();
                 it != entries.end(); ++it)
            {
                info.push_back(api::RequestBucketInfoReply::Entry(
                            it->first, it->second->getBucketInfo()));
            }
        }
    } else {
        LOG(error, "We don't support fetching bucket info without bucket "
                   "list or system state");
        assert(false);
    }
    _metrics->simpleBucketInfoRequestSize.addValue(info.size());
    auto reply = std::make_shared<api::RequestBucketInfoReply>(*cmd);
    reply->getBucketInfo().swap(info);
    LOG(spam, "Sending %s", reply->toString().c_str());

    LOG(spam, "Returning list of checksums:");
    for (const auto & entry : reply->getBucketInfo()) {
        LOG(spam, "%s: %s",
            entry._bucketId.toString().c_str(),
            entry._info.toString().c_str());
    }
    dispatchUp(reply);
    // Remaining replies dispatched by queueGuard upon function exit.
    return true;
}

namespace {
    std::string unifyState(const lib::ClusterState& state) {
        std::vector<char> distributors(
                state.getNodeCount(lib::NodeType::DISTRIBUTOR), 'd');

        uint32_t length = 0;
        for (uint32_t i = 0; i < distributors.size(); ++i) {
            const lib::NodeState& ns(state.getNodeState(
                    lib::Node(lib::NodeType::DISTRIBUTOR, i)));
            if (ns.getState().oneOf("uirm")) {
                distributors[i] = 'u';
                length = i + 1;
            }
        }
        return std::string(&distributors[0], length);
    }
}

BucketManager::ScopedQueueDispatchGuard::ScopedQueueDispatchGuard(
        BucketManager& mgr)
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
    std::lock_guard<std::mutex> guard(_queueProcessingLock);
    ++_requestsCurrentlyProcessing;
}

void
BucketManager::leaveQueueProtectedSection(ScopedQueueDispatchGuard& queueGuard)
{
    (void) queueGuard; // Only used to enforce guard is held while calling.
    std::lock_guard<std::mutex> guard(_queueProcessingLock);
    assert(_requestsCurrentlyProcessing > 0);
    // Full bucket info fetches may be concurrently interleaved with bucket-
    // specific fetches outside of the processing thread. We only allow queued
    // messages to go through once _all_ of these are done, since we do not
    // keep per-bucket info request queues and thus cannot know which replies
    // may alter the relevant state.
    --_requestsCurrentlyProcessing;
    if (_requestsCurrentlyProcessing == 0) {
        for (auto& qr : _queuedReplies) {
            dispatchUp(qr);
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
    typedef std::shared_ptr<api::RequestBucketInfoCommand> RBISP;
    std::map<uint16_t, RBISP> requests;

    auto distribution(_component.getBucketSpaceRepo().get(bucketSpace).getDistribution());
    auto clusterStateBundle(_component.getStateUpdater().getClusterStateBundle());
    assert(clusterStateBundle);
    lib::ClusterState::CSP clusterState(clusterStateBundle->getDerivedClusterState(bucketSpace));
    assert(clusterState.get());

    DistributionHashNormalizer normalizer;

    const auto our_hash = normalizer.normalize(
            distribution->getNodeGraph().getDistributionConfigHash());

    LOG(debug, "Processing %" PRIu64 " queued request bucket info commands. "
        "Using cluster state '%s' and distribution hash '%s'",
        reqs.size(),
        clusterState->toString().c_str(),
        our_hash.c_str());

    std::lock_guard<std::mutex> clusterStateGuard(_clusterStateLock);
    for (auto it = reqs.rbegin(); it != reqs.rend(); ++it) {
        // Currently small requests should not be forwarded to worker thread
        assert((*it)->hasSystemState());
        const auto their_hash = normalizer.normalize(
                (*it)->getDistributionHash());

        std::ostringstream error;
        if ((*it)->getSystemState().getVersion() > _lastClusterStateSeen) {
            error << "Ignoring bucket info request for cluster state version "
                  << (*it)->getSystemState().getVersion() << " as newest "
                  << "version we know of is " << _lastClusterStateSeen;
        } else if ((*it)->getSystemState().getVersion()
                    < _firstEqualClusterStateVersion)
        {
            error << "Ignoring bucket info request for cluster state version "
                  << (*it)->getSystemState().getVersion() << " as versions "
                  << "from version " << _firstEqualClusterStateVersion
                  << " differs from this state.";
        } else if (!their_hash.empty() && their_hash != our_hash) {
            // Empty hash indicates request from 4.2 protocol or earlier
            error << "Distribution config has changed since request.";
        }
        if (error.str().empty()) {
            std::pair<std::set<uint16_t>::iterator, bool> result(
                    seenDistributors.insert((*it)->getDistributor()));
            if (result.second) {
                requests[(*it)->getDistributor()] = *it;
                continue;
            } else {
                error << "There is already a newer bucket info request for this"
                      << " node from distributor " << (*it)->getDistributor();
            }
        }
        
    	// If we get here, message should be failed
        auto reply = std::make_shared<api::RequestBucketInfoReply>(**it);
        reply->setResult(api::ReturnCode(
                    api::ReturnCode::REJECTED, error.str()));
        LOG(debug, "Rejecting request from distributor %u: %s",
            (*it)->getDistributor(),
            error.str().c_str());
        dispatchUp(reply);
    }

    if (requests.empty()) {
        reqs.clear();
        return true; // No need to waste CPU when no requests are left.
    }

    std::ostringstream distrList;
    std::unordered_map<
        uint16_t,
        api::RequestBucketInfoReply::EntryVector
    > result;
    for (auto& nodeAndCmd : requests) {
        result[nodeAndCmd.first];
        if (LOG_WOULD_LOG(debug)) {
            distrList << ' ' << nodeAndCmd.first;
        }
    }

    _metrics->fullBucketInfoRequestSize.addValue(requests.size());
    LOG(debug, "Processing %" PRIu64 " bucket info requests for "
               "distributors %s, using system state %s",
        requests.size(), distrList.str().c_str(),
        clusterState->toString().c_str());
   framework::MilliSecTimer runStartTime(_component.getClock());
        // Don't allow logging to lower performance of inner loop.
        // Call other type of instance if logging
    const document::BucketIdFactory& idFac(_component.getBucketIdFactory());
    if (LOG_WOULD_LOG(spam)) {
        DistributorInfoGatherer<true> builder(
                *clusterState, result, idFac, distribution);
        _component.getBucketDatabase(bucketSpace).chunkedAll(builder,
                        "BucketManager::processRequestBucketInfoCommands-1");
    } else {
        DistributorInfoGatherer<false> builder(
                *clusterState, result, idFac, distribution);
        _component.getBucketDatabase(bucketSpace).chunkedAll(builder,
                        "BucketManager::processRequestBucketInfoCommands-2");
    }
    _metrics->fullBucketInfoLatency.addValue(
            runStartTime.getElapsedTimeAsDouble());
    for (auto& nodeAndCmd : requests) {
        auto reply(std::make_shared<api::RequestBucketInfoReply>(
                *nodeAndCmd.second));
        reply->getBucketInfo().swap(result[nodeAndCmd.first]);
        dispatchUp(reply);
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
        dispatchUp(msg);
    }
    return true;
}

bool
BucketManager::verifyAndUpdateLastModified(api::StorageCommand& cmd,
                                           const document::Bucket &bucket,
                                           uint64_t lastModified)
{
    LOG(spam, "Received operation %s with modification timestamp %zu",
        cmd.toString().c_str(),
        lastModified);

    uint64_t prevLastModified = 0;

    {
        StorBucketDatabase::WrappedEntry entry(
                _component.getBucketDatabase(bucket.getBucketSpace()).get(bucket.getBucketId(), "BucketManager::verify"));

        if (entry.exist()) {
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
    reply->setResult(api::ReturnCode(
                             api::ReturnCode::STALE_TIMESTAMP,
                             vespalib::make_string(
                                     "Received command %s with a lower/equal timestamp "
                                     " (%zu) than the last operation received for "
                                     "bucket %s, with timestamp %zu",
                                     cmd.toString().c_str(),
                                     lastModified,
                                     bucket.toString().c_str(),
                                     prevLastModified)));


    sendUp(api::StorageMessage::SP(reply.release()));
    return false;
}

bool
BucketManager::onSetSystemState(
            const std::shared_ptr<api::SetSystemStateCommand>& cmd)
{
    LOG(debug, "onSetSystemState(%s)", cmd->toString().c_str());
    const lib::ClusterState& state(cmd->getSystemState());
    std::string unified(unifyState(state));
    std::lock_guard<std::mutex> lock(_clusterStateLock);
    if (unified != _lastUnifiedClusterState
        || state.getVersion() != _lastClusterStateSeen + 1)
    {
        _lastUnifiedClusterState = unified;
        _firstEqualClusterStateVersion = state.getVersion();
    }
    _lastClusterStateSeen = state.getVersion();
    return false;
}

bool
BucketManager::onCreateBucket(const api::CreateBucketCommand::SP& cmd)
{
    MinimumUsedBitsTracker& bitTracker(_component.getMinUsedBitsTracker());
    if (bitTracker.update(cmd->getBucketId())) {
        NodeStateUpdater::Lock::SP lock(
                _component.getStateUpdater().grabStateChangeLock());
        lib::NodeState ns(
                *_component.getStateUpdater().getReportedNodeState());
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
        NodeStateUpdater::Lock::SP lock(
                _component.getStateUpdater().grabStateChangeLock());
        lib::NodeState ns(
                *_component.getStateUpdater().getReportedNodeState());
        ns.setMinUsedBits(bitTracker.getMinUsedBits());
        _component.getStateUpdater().setReportedNodeState(ns);
    }
    return false;
}

bool
BucketManager::onRemove(const api::RemoveCommand::SP& cmd)
{
    if (!verifyAndUpdateLastModified(*cmd,
                                     cmd->getBucket(),
                                     cmd->getTimestamp())) {
        return true;
    }

    return false;
}

bool
BucketManager::onRemoveReply(const api::RemoveReply::SP& reply)
{
    return enqueueIfBucketHasConflicts(reply);
}

bool
BucketManager::onPut(const api::PutCommand::SP& cmd)
{
    if (!verifyAndUpdateLastModified(*cmd,
                                     cmd->getBucket(),
                                     cmd->getTimestamp())) {
        return true;
    }

    return false;
}

bool
BucketManager::onPutReply(const api::PutReply::SP& reply)
{
    return enqueueIfBucketHasConflicts(reply);
}

bool
BucketManager::onUpdate(const api::UpdateCommand::SP& cmd)
{
    if (!verifyAndUpdateLastModified(*cmd,
                                     cmd->getBucket(),
                                     cmd->getTimestamp())) {
        return true;
    }

    return false;
}

bool
BucketManager::onUpdateReply(const api::UpdateReply::SP& reply)
{
    return enqueueIfBucketHasConflicts(reply);
}

bool
BucketManager::onNotifyBucketChangeReply(
        const api::NotifyBucketChangeReply::SP& reply)
{
    (void) reply;
    // Handling bucket change replies is a no-op.
    return true;
}

bool
BucketManager::enqueueIfBucketHasConflicts(const api::BucketReply::SP& reply)
{
    // Should very rarely contend, since persistence replies are all sent up
    // via a single dispatcher thread.
    std::lock_guard<std::mutex> guard(_queueProcessingLock);
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
    std::lock_guard<std::mutex> guard(_queueProcessingLock);
    if (_requestsCurrentlyProcessing != 0) {
        LOG(debug, "Enqueued %s due to concurrent RequestBucketInfo",
            reply->toString().c_str());
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

