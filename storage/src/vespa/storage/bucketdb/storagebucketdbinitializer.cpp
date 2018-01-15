// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagebucketdbinitializer.h"
#include "lockablemap.hpp"
#include "config-stor-bucket-init.h"
#include "storbucketdb.h"
#include <vespa/storage/common/nodestateupdater.h>
#include <vespa/storage/common/content_bucket_space_repo.h>
#include <vespa/storage/storageserver/storagemetricsset.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/config/config.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/config/helper/configgetter.hpp>
#include <iomanip>
#include <chrono>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".storage.bucketdb.initializer");

using document::BucketSpace;
using namespace std::chrono_literals;

namespace storage {

using BucketSet = vespalib::hash_set<document::BucketId, document::BucketId::hash>;

struct BucketReadState {
    using UP = std::unique_ptr<BucketReadState>;

    BucketSet _pending;
    document::BucketId _databaseIterator;
    bool _done;

    BucketReadState() : _done(false) {}
};

using vespa::config::content::core::StorBucketInitConfig;

StorageBucketDBInitializer::Config::Config(const config::ConfigUri & configUri)
    : _listPriority(0),
      _infoReadPriority(255),
      _minPendingInfoReadsPerDisk(16),
      _maxPendingInfoReadsPerDisk(32)
{
    auto config = config::ConfigGetter<StorBucketInitConfig>::getConfig(configUri.getConfigId(),
                                                                        configUri.getContext());
    _maxPendingInfoReadsPerDisk = config->maxPendingInfoReadsPerDisk;
    _minPendingInfoReadsPerDisk = config->minPendingInfoReadsPerDisk;
    _infoReadPriority = config->infoReadPriority;
    _listPriority = config->listPriority;
    if (config->completeListBeforeStartingRead) {
        LOG(warning, "This config option is currently not honored. Info "
                     "reading will always start on a directory as soon as "
                     "it is done listing.");
    }
    LOG(debug, "Initializing bucket database: List priority %u, info priority "
               "%u, min/max pending info per disk %u/%u.",
        _listPriority, _infoReadPriority,
        _minPendingInfoReadsPerDisk, _maxPendingInfoReadsPerDisk);
}

StorageBucketDBInitializer::System::System(
        const spi::PartitionStateList& partitions,
        DoneInitializeHandler& doneInitializeHandler,
        ServiceLayerComponentRegister& compReg,
        const Config&)
    : _doneInitializeHandler(doneInitializeHandler),
      _component(compReg, "storagebucketdbinitializer"),
      _partitions(partitions),
      _bucketSpaceRepo(_component.getBucketSpaceRepo()),
      _nodeIndex(_component.getIndex()),
      _nodeState()
{
    // Is this correct? We should get the node state from the node state updater
    // so it could work with disk capacities. Object is used to check for
    // correct disk further down (in the case of internal join, deciding which
    // should have it). Not that bad if wrong disk is picked though.
    _nodeState.setDiskCount(_partitions.size());
    for (uint32_t i=0; i<_partitions.size(); ++i) {
        if (!_partitions[i].isUp()) {
            _nodeState.setDiskState(i, lib::State::DOWN);
        }
    }
}

StorBucketDatabase &
StorageBucketDBInitializer::System::getBucketDatabase(document::BucketSpace bucketSpace) const
{
    return _component.getBucketDatabase(bucketSpace);
}

StorageBucketDBInitializer::Metrics::Metrics(framework::Component& component)
    : metrics::MetricSet("dbinit", "",
                         "Metrics for the storage bucket database initializer"),
      _wrongDisk("wrongdisk", "",
              "Number of buckets found on non-ideal disk.", this),
      _insertedCount("insertedcount", "",
              "Number of buckets inserted into database in list step.", this),
      _joinedCount("joinedcount", "",
              "Number of buckets found in list step already found "
              "(added from other disks).", this),
      _infoReadCount("infocount", "",
              "Number of buckets we have read bucket information from.", this),
      _infoSetByLoad("infosetbyload", "",
              "Number of buckets we did not need to request bucket info for "
              "due to load already having updated them.", this),
      _dirsListed("dirslisted", "",
              "Directories listed in list step of initialization.", this),
      _startTime(component.getClock()),
      _listLatency("listlatency", "",
              "Time used until list phase is done. (in ms)", this),
      _initLatency("initlatency", "",
              "Time used until initialization is complete. (in ms)", this)
{
    component.registerMetric(*this);
}

StorageBucketDBInitializer::Metrics::~Metrics() {}

StorageBucketDBInitializer::GlobalState::GlobalState()
    : _lists(), _joins(), _infoRequests(), _replies(),
      _insertedCount(0), _infoReadCount(0),
      _infoSetByLoad(0), _dirsListed(0), _dirsToList(0),
      _gottenInitProgress(false), _doneListing(false),
      _doneInitializing(false), _workerLock(), _workerCond(), _replyLock()
{ }
StorageBucketDBInitializer::GlobalState::~GlobalState() { }

StorageBucketDBInitializer::StorageBucketDBInitializer(
        const config::ConfigUri & configUri,
        const spi::PartitionStateList& partitions,
        DoneInitializeHandler& doneInitializeHandler,
        ServiceLayerComponentRegister& compReg)
    : StorageLink("StorageBucketDBInitializer"),
      framework::HtmlStatusReporter("dbinit", "Bucket database initializer"),
      _config(configUri),
      _system(partitions, doneInitializeHandler, compReg, _config),
      _metrics(_system._component),
      _state(),
      _readState(_system._partitions.size())
{
        // Initialize read state for disks being available
    for (uint32_t i=0; i<_system._partitions.size(); ++i) {
        if (!_system._partitions[i].isUp()) continue;
        _readState[i] = std::make_unique<BucketSpaceReadState>();
        for (const auto &elem : _system._bucketSpaceRepo) {
            _readState[i]->emplace(elem.first, std::make_unique<BucketReadState>());
            _state._dirsToList += 1;
        }
    }
    _system._component.registerStatusPage(*this);
}

StorageBucketDBInitializer::~StorageBucketDBInitializer()
{
    if (_system._thread.get() != 0) {
        LOG(error, "Deleted without calling close() first");
        onClose();
    }
    closeNextLink();
}

void
StorageBucketDBInitializer::onOpen()
{
        // Trigger bucket database initialization
    for (uint32_t i=0; i<_system._partitions.size(); ++i) {
        if (!_system._partitions[i].isUp()) continue;
        assert(_readState[i]);
        const BucketSpaceReadState &spaceState = *_readState[i];
        for (const auto &stateElem : spaceState) {
            document::BucketSpace bucketSpace = stateElem.first;
            auto msg = std::make_shared<ReadBucketList>(bucketSpace, spi::PartitionId(i));
            _state._lists[msg->getMsgId()] = msg;
            sendDown(msg);
        }
    }
    framework::MilliSecTime maxProcessingTime(10);
    framework::MilliSecTime sleepTime(1000);
    _system._thread = _system._component.startThread(
            *this, maxProcessingTime, sleepTime);
}

void
StorageBucketDBInitializer::onClose()
{
    if (_system._thread.get() != 0) {
        _system._thread->interruptAndJoin(_state._workerLock, _state._workerCond);
        _system._thread.reset(0);
    }
}

void
StorageBucketDBInitializer::run(framework::ThreadHandle& thread)
{
    std::unique_lock<std::mutex> guard(_state._workerLock);
    while (!thread.interrupted() && !_state._doneInitializing) {
        std::list<api::StorageMessage::SP> replies;
        {
            std::lock_guard<std::mutex> replyGuard(_state._replyLock);
            _state._replies.swap(replies);
        }
        for (std::list<api::StorageMessage::SP>::iterator it = replies.begin();
             it != replies.end(); ++it)
        {
            api::InternalReply& reply(static_cast<api::InternalReply&>(**it));
            if (reply.getType() == ReadBucketListReply::ID) {
                handleReadBucketListReply(
                        static_cast<ReadBucketListReply&>(reply));
            } else if (reply.getType() == ReadBucketInfoReply::ID) {
                handleReadBucketInfoReply(
                        static_cast<ReadBucketInfoReply&>(reply));
            } else if (reply.getType() == InternalBucketJoinReply::ID) {
                handleInternalBucketJoinReply(
                        static_cast<InternalBucketJoinReply&>(reply));
            }
        }
        if (_state._gottenInitProgress) {
            _state._gottenInitProgress = false;
            updateInitProgress();
        }
        if (replies.empty()) {
            _state._workerCond.wait_for(guard, 10ms);
            thread.registerTick(framework::WAIT_CYCLE);
        } else {
            thread.registerTick(framework::PROCESS_CYCLE);
        }
    }
}

void
StorageBucketDBInitializer::print(
        std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "StorageBucketDBInitializer()";
}

namespace {

size_t
notDoneCount(const StorageBucketDBInitializer::ReadState &readState)
{
    size_t result = 0;
    for (const auto &elem : readState) {
        if (elem) {
            for (const auto &stateElem : *elem) {
                if (!stateElem.second->_done) {
                    ++result;
                }
            }
        }
    }
    return result;
}

}

void
StorageBucketDBInitializer::reportHtmlStatus(
        std::ostream& out, const framework::HttpUrlPath&) const
{
    std::lock_guard<std::mutex> guard(_state._workerLock);
    out << "\n  <h2>Config</h2>\n"
        << "    <table>\n"
        << "      <tr><td>Max pending info reads per disk</td><td>"
        << _config._maxPendingInfoReadsPerDisk << "</td></tr>\n"
        << "      <tr><td>Min pending info reads per disk</td><td>"
        << _config._minPendingInfoReadsPerDisk << "</td></tr>\n"
        << "      <tr><td>List priority</td><td>"
        << _config._listPriority << "</td></tr>\n"
        << "      <tr><td>Info read priority</td><td>"
        << _config._infoReadPriority << "</td></tr>\n"
        << "    </table>\n";

    out << "\n  <h2>Init progress</h2>\n";
    if (_state._doneListing) {
        out << "    Done listing.<br/>\n";
    } else {
        out << "    Listed " << _state._dirsListed << " of "
            << _state._dirsToList << " partitions.<br/>\n";
    }
    if (_state._lists.empty()) {
        out << "    No lists pending.<br/>\n";
    } else {
        out << "    " << _state._lists.size() << " lists pending.<br/>\n";
    }
    if (_state._joins.empty()) {
        out << "    No internal joins pending.<br/>\n";
    } else {
        out << "    " << _state._joins.size()
            << " internal joins pending.<br/>\n";
    }
    if (_state._infoRequests.empty()) {
        out << "    No info requests pending.<br/>\n";
    } else {
        out << "    " << _state._infoRequests.size()
            << " info requests pending.<br/>\n";
    }
    uint32_t incompleteScan = notDoneCount(_readState);
    if (incompleteScan == 0) {
        out << "    Done iterating bucket database to generate info "
            << "requests.<br/>\n";
    } else {
        out << "    " << incompleteScan << " partitions still have buckets "
            << "that needs bucket info.<br/>\n";
    }
    out << "    Init progress gotten after state update: "
        << (_state._gottenInitProgress ? "true" : "false") << "<br/>\n";
    if (_state._doneInitializing) {
        out << "    Initialization complete.\n";
    } else {
        out << "    Initialization not completed.\n";
    }

    out << "\n  <h2>Metrics</h2>\n";
    out << "    " << _metrics._insertedCount.toString(true) << "<br/>\n"
        << "    " << _metrics._joinedCount.toString(true) << "<br/>\n"
        << "    " << _metrics._infoReadCount.toString(true) << "<br/>\n"
        << "    " << _metrics._infoSetByLoad.toString(true) << "<br/>\n"
        << "    " << _metrics._dirsListed.toString(true) << "<br/>\n"
        << "    Dirs to list " << _state._dirsToList << "<br/>\n";
    if (!_state._joins.empty()) {
        out << "\n  <h2>Pending internal bucket joins</h2>\n";
        for (const auto & e : _state._joins) {
            out << "    " << e.first << " - " << *e.second << "<br/>\n";
        }
    }
    out << "\n  <h2>Info read state</h2>\n";
    std::map<Disk, uint32_t> pendingCounts;
    for (const auto & e : _state._infoRequests)
    {
        ++pendingCounts[e.second];
    }
    for (uint32_t i=0; i<_readState.size(); ++i) {
        if (_readState[i].get() == 0) {
            out << "    <h3>Disk " << i << " is down</h3>\n";
            continue;
        }
        const BucketSpaceReadState& spaceState(*_readState[i]);
        for (const auto &stateElem : spaceState) {
            const BucketReadState &state = *stateElem.second;
            out << "    <h3>Disk " << i << ", bucket space " << stateElem.first.getId() << "</h3>\n";
            out << "      Pending info requests: " << pendingCounts[i] << " (";
            if (state._pending.empty()) {
                out << "none";
            } else {
                bool first = true;
                for (BucketSet::const_iterator it = state._pending.begin();
                     it != state._pending.end(); ++it) {
                    if (!first) {
                        out << ", ";
                    } else {
                        first = false;
                    }
                    out << *it;
                }
            }
            out << ")<br/>\n";
            out << "      Bucket database iterator: " << state._databaseIterator
                << "<br/>\n";
            out << "      Done iterating bucket database. "
                << (state._done ? "true" : "false") << "<br/>\n";
        }
    }
    for (std::map<Disk, uint32_t>::iterator it = pendingCounts.begin();
         it != pendingCounts.end(); ++it)
    {
        out << "      Disk " << it->first << ": " << it->second << "<br/>\n";
    }
}

// Always called from worker thread. Worker monitor already grabbed
void
StorageBucketDBInitializer::registerBucket(const document::Bucket &bucket,
                                           const lib::Distribution &distribution,
                                           spi::PartitionId partition,
                                           api::BucketInfo bucketInfo)
{
    document::BucketId bucketId(bucket.getBucketId());
    StorBucketDatabase::WrappedEntry entry(_system.getBucketDatabase(bucket.getBucketSpace()).get(
                bucketId, "StorageBucketDBInitializer::registerBucket",
                StorBucketDatabase::CREATE_IF_NONEXISTING));
    if (bucketInfo.valid()) {
        if (entry.preExisted()) {
            LOG(debug, "Had value %s for %s before registering",
                entry->getBucketInfo().toString().c_str(),
                bucketId.toString().c_str());
        }
        LOG(debug, "Got new value %s from %s partition %u",
            bucketInfo.toString().c_str(), bucketId.toString().c_str(),
            partition.getValue());
        entry->setBucketInfo(bucketInfo);
    } else {
        LOG(debug, "Got invalid bucket info from %s partition %u: %s",
            bucketId.toString().c_str(), partition.getValue(),
            bucketInfo.toString().c_str());
    }
    if (entry.preExisted()) {
        if (entry->disk == partition) {
            LOG(debug, "%s already existed in bucket database on disk %i. "
                       "Might have been moved from wrong directory prior to "
                       "listing this directory.",
                bucketId.toString().c_str(), int(partition));
            return;
        }
        uint32_t keepOnDisk, joinFromDisk;
        if (distribution.getPreferredAvailableDisk(
                _system._nodeState, _system._nodeIndex,
                bucketId.stripUnused()) == partition)
        {
            keepOnDisk = partition;
            joinFromDisk = entry->disk;
        } else {
            keepOnDisk = entry->disk;
            joinFromDisk = partition;
        }
        LOG(debug, "%s exist on both disk %u and disk %i. Joining two versions "
                   "onto disk %u.",
            bucketId.toString().c_str(), entry->disk, int(partition), keepOnDisk);
        entry.unlock();
        // Must not have bucket db lock while sending down
        auto cmd = std::make_shared<InternalBucketJoinCommand>(bucket, keepOnDisk, joinFromDisk);
        {
            _state._joins[cmd->getMsgId()] = cmd;
        }
        sendDown(cmd);
    } else {
        _system._component.getMinUsedBitsTracker().update(bucketId);
        LOG(spam, "Inserted %s on disk %i into bucket database",
            bucketId.toString().c_str(), int(partition));
        entry->disk = partition;
        entry.write();
        uint16_t disk(distribution.getIdealDisk(
                _system._nodeState, _system._nodeIndex, bucketId.stripUnused(),
                lib::Distribution::IDEAL_DISK_EVEN_IF_DOWN));
        if (disk != partition) {
            ++_metrics._wrongDisk;
        }

        _metrics._insertedCount.inc();
        ++_state._insertedCount;
    }
}

namespace {
    struct NextBucketOnDiskFinder {
        typedef document::BucketId BucketId;

        uint16_t _disk;
        BucketId& _iterator;
        uint16_t _count;
        std::vector<BucketId> _next;
        uint32_t _alreadySet;

        NextBucketOnDiskFinder(uint16_t disk, BucketId& iterator,
                               uint16_t maxToFind)
            : _disk(disk), _iterator(iterator), _count(maxToFind),
              _next(), _alreadySet(0) {}

        StorBucketDatabase::Decision operator()(
                uint64_t revBucket, StorBucketDatabase::Entry& entry)
        {
            BucketId bucket(BucketId::keyToBucketId(revBucket));
            if (bucket == _iterator) {
                //LOG(spam, "Ignoring bucket %s as it has value of current "
                //          "iterator", bucket.toString().c_str());
                return StorBucketDatabase::CONTINUE;
            }
            _iterator = bucket;
            if (entry.disk != _disk) {
                //LOG(spam, "Ignoring bucket %s as it is not on disk currently "
                //          "being processed", bucket.toString().c_str());
                // Ignore. We only want to scan for one disk
            } else if (entry.valid()) {
                LOG(spam, "%s already initialized by load %s. "
                          "Not requesting info",
                    bucket.toString().c_str(),
                    entry.getBucketInfo().toString().c_str());
                ++_alreadySet;
            } else {
                _next.push_back(_iterator);
                if (_next.size() >= _count) {
                    LOG(spam, "Aborting iterating for disk %u as we have "
                              "enough results. Leaving iterator at %s",
                        uint32_t(_disk), _iterator.toString().c_str());
                    return StorBucketDatabase::ABORT;
                }
            }
            return StorBucketDatabase::CONTINUE;
        }
    };
}

// Always called from worker thread. It holds worker monitor.
void
StorageBucketDBInitializer::sendReadBucketInfo(spi::PartitionId disk, document::BucketSpace bucketSpace)
{
    auto itr = _readState[disk]->find(bucketSpace);
    assert(itr != _readState[disk]->end());
    BucketReadState& state = *itr->second;
    if (state._done
        || state._pending.size() >= _config._maxPendingInfoReadsPerDisk)
    {
        LOG(spam, "No need to iterate further. Database has completed "
                  "iterating buckets for disk %u.", uint32_t(disk));
        return;
    }
    uint32_t count(_config._maxPendingInfoReadsPerDisk - state._pending.size());
    NextBucketOnDiskFinder finder(disk, state._databaseIterator, count);
    LOG(spam, "Iterating bucket db further. Starting at iterator %s",
        state._databaseIterator.toString().c_str());
    _system.getBucketDatabase(bucketSpace).all(finder,
                                "StorageBucketDBInitializer::readBucketInfo",
                                state._databaseIterator.stripUnused().toKey());
    if (finder._alreadySet > 0) {
        _metrics._infoSetByLoad.inc(finder._alreadySet);
        _state._infoSetByLoad += finder._alreadySet;
    }
    for (uint32_t i=0; i<finder._next.size(); ++i) {
        document::Bucket bucket(bucketSpace, finder._next[i]);
        auto cmd = std::make_shared<ReadBucketInfo>(bucket);
        cmd->setPriority(_config._infoReadPriority);
        state._pending.insert(finder._next[i]);
        _state._infoRequests[cmd->getMsgId()] = disk;
        LOG(spam, "Requesting bucket info from %s on disk %u.",
            finder._next[i].toString().c_str(), uint32_t(disk));
        sendDown(cmd);
    }
    state._done |= finder._next.empty();
    _state._gottenInitProgress = true;
    checkIfDone();
}

bool
StorageBucketDBInitializer::onDown(
        const std::shared_ptr<api::StorageMessage>& msg)
{
    // If we're done listing, load can go as normal.
    // Rationale behind memory_order_relaxed: _doneListing is initially false
    // and is ever only written once. Since the behavior for temporarily
    // reading a stale default is safe (block the message) and we do not
    // access any other shared state dependent on _doneListing, relaxed
    // semantics should be fine here.
    if (_state._doneListing.load(std::memory_order_relaxed)) {
        return StorageLink::onDown(msg);
    }

    // If we're not done listing, block most types of load

    // There are no known replies, but if there are to come any, they should
    // likely not be blocked.
    if (msg->getType().isReply()) return false;

    switch (msg->getType().getId()) {
        // Don't want to block communication with state manager
        case api::MessageType::SETSYSTEMSTATE_ID:
        case api::MessageType::GETNODESTATE_ID:
            return StorageLink::onDown(msg);
        default:
            break;
    }
    // Fail everything else
    std::ostringstream ost;
    ost << "Cannot perform operation " << msg->getType() << " now because "
        << "we are still listing buckets from disk.";
    LOGBP(warning, "%s", ost.str().c_str());
    std::unique_ptr<api::StorageReply> reply(
            static_cast<api::StorageCommand&>(*msg).makeReply());
    reply->setResult(api::ReturnCode(api::ReturnCode::ABORTED, ost.str()));
    sendUp(std::shared_ptr<api::StorageReply>(reply.release()));
    return true;
}

// Called from disk threads. Just push replies to reply list so worker thread
// can handle it. This minimizes locking needed. Disk reads should be the
// limiting factor, so don't need to update initializer state in multiple
// threads.
bool
StorageBucketDBInitializer::onInternalReply(
        const std::shared_ptr<api::InternalReply>& reply)
{
    switch(reply->getType()) {
        case ReadBucketListReply::ID:
        case ReadBucketInfoReply::ID:
        case InternalBucketJoinReply::ID:
        {
            std::lock_guard<std::mutex> guard(_state._replyLock);
            _state._replies.push_back(reply);
            return true;
        }
        default:
            return false;
    }
}

// Always called from worker thread. It holds worker monitor.
void
StorageBucketDBInitializer::handleReadBucketListReply(
        ReadBucketListReply& reply)
{
    vespalib::hash_map<api::StorageMessage::Id,
                       ReadBucketList::SP>::iterator it(
            _state._lists.find(reply.getMsgId()));
    if (it == _state._lists.end()) {
        LOGBP(warning, "Got bucket list reply for partition %u, request "
                       "%" PRIu64 ", that was not registered pending.",
              reply.getPartition().getValue(), reply.getMsgId());
    } else {
        _state._lists.erase(it);
    }
    // We don't handle failed bucket listings. Kill process. Bucket lists are
    // essential for storage node operations
    if (reply.getResult().failed()) {
        LOG(debug, "Got failing bucket list reply. Requesting shutdown");
        _system._component.requestShutdown(
                "Failed to list buckets. Cannot run without bucket list: "
                + reply.getResult().toString());
        return;
    }
    _metrics._dirsListed.inc();
    _state._gottenInitProgress = true;
    const spi::BucketIdListResult::List& list(reply.getBuckets());
    api::BucketInfo info;
    assert(!info.valid());
    const auto &contentBucketSpace(_system._bucketSpaceRepo.get(reply.getBucketSpace()));
    auto distribution(contentBucketSpace.getDistribution());
    for (uint32_t i=0, n=list.size(); i<n; ++i) {
        registerBucket(document::Bucket(reply.getBucketSpace(), list[i]), *distribution, reply.getPartition(), info);
    }
    if (++_state._dirsListed == _state._dirsToList) {
        handleListingCompleted();
    }
    checkIfDone();
    sendReadBucketInfo(reply.getPartition(), reply.getBucketSpace());
}

// Always called from worker thread. It holds worker monitor.
void
StorageBucketDBInitializer::handleReadBucketInfoReply(
        ReadBucketInfoReply& reply)
{
    document::BucketSpace bucketSpace = reply.getBucket().getBucketSpace();
    if (reply.getResult().failed()) {
        LOGBP(warning, "Deleting %s from bucket database. Cannot use it as we "
                       "failed to read bucket info for it: %s",
              reply.getBucketId().toString().c_str(),
              reply.getResult().toString().c_str());
        _system.getBucketDatabase(bucketSpace).erase(reply.getBucketId(),
                                      "dbinit.failedreply");
    }
    _metrics._infoReadCount.inc();
    ++_state._infoReadCount;
    _state._gottenInitProgress = true;
    vespalib::hash_map<api::StorageMessage::Id, Disk>::iterator it(
            _state._infoRequests.find(reply.getMsgId()));
    if (it == _state._infoRequests.end()) {
        LOGBP(warning, "Got bucket info reply for %s, request %" PRIu64 ", that "
                       "was not registered pending.",
              reply.getBucketId().toString().c_str(), reply.getMsgId());
        checkIfDone();
    } else {
        uint32_t disk(it->second);
        _state._infoRequests.erase(it->first);
        auto itr = _readState[disk]->find(bucketSpace);
        assert(itr != _readState[disk]->end());
        BucketReadState& state = *itr->second;
        BucketSet::iterator it2(state._pending.find(reply.getBucketId()));
        if (it2 == state._pending.end()) {
            LOGBP(warning, "Got bucket info reply for %s that was registered "
                           "in global state but not in disk %u's state.",
                  reply.getBucketId().toString().c_str(), disk);
        } else {
            state._pending.erase(reply.getBucketId());
            LOG(spam, "Got info reply for %s: %s",
                reply.getBucketId().toString().c_str(),
                _system.getBucketDatabase(reply.getBucket().getBucketSpace()).get(
                    reply.getBucketId(), "dbinit.inforeply")
                        ->getBucketInfo().toString().c_str());
        }
        checkIfDone();
        sendReadBucketInfo(spi::PartitionId(disk), bucketSpace);
    }
}

// Always called from worker thread. It holds worker monitor.
void
StorageBucketDBInitializer::handleInternalBucketJoinReply(
        InternalBucketJoinReply& reply)
{
    _metrics._joinedCount.inc();
    vespalib::hash_map<api::StorageMessage::Id,
                       InternalBucketJoinCommand::SP>::iterator it(
            _state._joins.find(reply.getMsgId()));
    if (reply.getResult().failed()) {
        LOGBP(warning, "Failed to join multiple copies of %s. One of the "
                       "versions will not be available: %s",
              reply.getBucketId().toString().c_str(),
              reply.getResult().toString().c_str());
    }
    if (it != _state._joins.end()) {
        _state._joins.erase(reply.getMsgId());
        LOG(debug, "Completed internal bucket join for %s. Got bucket info %s",
            reply.getBucketId().toString().c_str(),
            reply.getBucketInfo().toString().c_str());
        StorBucketDatabase::WrappedEntry entry(_system.getBucketDatabase(reply.getBucket().getBucketSpace()).get(
                reply.getBucketId(),
                "StorageBucketDBInitializer::onInternalBucketJoinReply"));
        entry->setBucketInfo(reply.getBucketInfo());
        entry.write();
    } else {
        LOGBP(warning, "Got internal join reply for %s which was not "
                       "registered to be pending.",
        reply.getBucketId().toString().c_str());
    }
    checkIfDone();
}

namespace {

bool
isDone(const StorageBucketDBInitializer::ReadState &readState)
{
    return notDoneCount(readState) == 0;
}

}

// Always called from worker thread. It holds worker monitor.
void
StorageBucketDBInitializer::checkIfDone()
{
    if (_state._dirsListed < _state._dirsToList) return;
    if (!_state._infoRequests.empty()) return;
    if (!_state._joins.empty()) return;
    if (!isDone(_readState)) {
        return;
    }
    _state._doneInitializing = true;
    _system._doneInitializeHandler.notifyDoneInitializing();
    _metrics._initLatency.addValue(_metrics._startTime.getElapsedTimeAsDouble());
    LOG(debug, "Completed initializing");
}

double
StorageBucketDBInitializer::calculateMinProgressFromDiskIterators() const
{
    double minProgress = 1.0;
    for (size_t disk = 0; disk < _readState.size(); ++disk) {
        if (_readState[disk].get() == 0) {
            continue;
        }
        for (const auto &stateElem : *_readState[disk]) {
            const BucketReadState &state = *stateElem.second;
            document::BucketId bid(state._databaseIterator);

            double progress;
            if (!state._done) {
                progress = BucketProgressCalculator::calculateProgress(bid);
            } else {
                progress = 1.0;
            }

            minProgress = std::min(minProgress, progress);
        }
    }
    //std::cerr << "minProgress: " << minProgress << "\n";
    return minProgress;
}

// Always called from worker thread. It holds worker monitor.
double
StorageBucketDBInitializer::calcInitProgress() const
{
    double listProgress(_state._dirsToList == 0
            ? 0 : _state._dirsListed / _state._dirsToList);
        // Do sanity check
    if (_state._dirsListed > _state._dirsToList) {
        LOG(error, "%" PRIu64 " of %u dirs are reported listed. This is a bug.",
            _state._dirsListed, _state._dirsToList);
        listProgress = 1.0;
    }
    double infoProgress(calculateMinProgressFromDiskIterators());
    if (_state._dirsToList > _state._dirsListed
        && infoProgress > 0)
    {
        LOG(debug, "Not done with list step yet. (%" PRIu64 " of %u done). "
                   "Need to nullify info part of progress so fleetcontroller "
                   "doesn't think listing is completed.",
            _state._dirsListed, _state._dirsToList);
        infoProgress = 0;

        // Currently we never honor complete_list_before_starting_read option.
        // We might want to do that later, in order to be able to enforce
        // waiting to read. For instance, if we have usecase where several
        // directories map to the same disk, such that reading info is slowing
        // down directory listing to such an extent that quick restart aint
        // quick enough anymore. If we do, revert to make this an error if that
        // config option is enabled
    }

    double listLimit = lib::NodeState::getListingBucketsInitProgressLimit();
    double progress(listLimit * listProgress
                    + (1.0 - listLimit) * infoProgress);
    assert(progress < 1.000000001);
    return progress;
}

// Always called from worker thread. It holds worker monitor.
void
StorageBucketDBInitializer::updateInitProgress() const
{
    double progress = calcInitProgress();
    NodeStateUpdater::Lock::SP lock(
            _system._component.getStateUpdater().grabStateChangeLock());
    lib::NodeState ns(
            *_system._component.getStateUpdater().getReportedNodeState());
    LOG(debug, "Reporting node init progress as %g", progress);
    ns.setInitProgress(progress);
    ns.setMinUsedBits(_system._component.getMinUsedBitsTracker()
                      .getMinUsedBits());
    _system._component.getStateUpdater().setReportedNodeState(ns);
}

// Always called from worker thread. It holds worker monitor.
void
StorageBucketDBInitializer::handleListingCompleted()
{
    assert(!_state._doneListing);
    _state._doneListing = true;
    if (_state._dirsToList != _state._dirsListed) {
        LOG(warning, "After list phase completed, counters indicate we've "
                     "listed %" PRIu64 " of %u directories. This is a bug.",
            _state._dirsListed, _state._dirsToList);
    }
    LOG(info, "Completed listing buckets from disk. Minimum used bits is %u",
        _system._component.getMinUsedBitsTracker().getMinUsedBits());
    _metrics._listLatency.addValue(_metrics._startTime.getElapsedTimeAsDouble());
}

double
StorageBucketDBInitializer::
BucketProgressCalculator::calculateProgress(const document::BucketId& bid)
{
    uint64_t revBucket(document::BucketId::bucketIdToKey(bid.getId()));

    // Remove unused bits
    uint64_t progressBits(revBucket >> (64 - bid.getUsedBits()));
/*
    std::cerr << bid << ":\n";
    std::cerr << "revBucket: " << std::hex << revBucket << ", progressBits: " << progressBits
              << ", divisor: " << (1ULL << bid.getUsedBits())
              << ", result= " << (static_cast<double>(progressBits) / (1ULL << bid.getUsedBits()))
              << "\n";
*/
    return static_cast<double>(progressBits) / (1ULL << bid.getUsedBits());
}

} // storage
