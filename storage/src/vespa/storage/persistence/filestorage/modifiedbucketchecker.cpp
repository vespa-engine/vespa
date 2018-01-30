// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "modifiedbucketchecker.h"
#include "filestormanager.h"
#include <vespa/config/common/exceptions.h>
#include <algorithm>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.filestor.modifiedbucketchecker");

using document::BucketSpace;

namespace storage {

ModifiedBucketChecker::CyclicBucketSpaceIterator::
CyclicBucketSpaceIterator(const ContentBucketSpaceRepo::BucketSpaces &bucketSpaces)
    : _bucketSpaces(bucketSpaces),
      _idx(0)
{
    std::sort(_bucketSpaces.begin(), _bucketSpaces.end());
}

ModifiedBucketChecker::BucketIdListResult::BucketIdListResult()
    : _bucketSpace(document::BucketSpace::invalid()),
      _buckets()
{
}

void
ModifiedBucketChecker::BucketIdListResult::reset(document::BucketSpace bucketSpace,
                                                 document::bucket::BucketIdList &buckets)
{
    _bucketSpace = bucketSpace;
    assert(_buckets.empty());
    _buckets.swap(buckets);
    // We pick chunks from the end of the list, so reverse it to get
    // the same send order as order received.
    std::reverse(_buckets.begin(), _buckets.end());
}

ModifiedBucketChecker::ModifiedBucketChecker(
        ServiceLayerComponentRegister& compReg,
        spi::PersistenceProvider& provider,
        const config::ConfigUri& configUri)
    : StorageLink("Modified bucket checker"),
      _provider(provider),
      _component(),
      _thread(),
      _configFetcher(configUri.getContext()),
      _monitor(),
      _stateLock(),
      _bucketSpaces(),
      _rechecksNotStarted(),
      _pendingRequests(0),
      _maxPendingChunkSize(100),
      _singleThreadMode(false)
{
    _configFetcher.subscribe<vespa::config::content::core::StorServerConfig>(configUri.getConfigId(), this);
    _configFetcher.start();

    std::ostringstream threadName;
    threadName << "Modified bucket checker " << static_cast<void*>(this);
    _component.reset(new ServiceLayerComponent(compReg, threadName.str()));
    _bucketSpaces = std::make_unique<CyclicBucketSpaceIterator>(_component->getBucketSpaceRepo().getBucketSpaces());
}

ModifiedBucketChecker::~ModifiedBucketChecker()
{
    assert(!_thread.get());
}

void
ModifiedBucketChecker::configure(
    std::unique_ptr<vespa::config::content::core::StorServerConfig> newConfig)
{
    vespalib::LockGuard lock(_stateLock);
    if (newConfig->bucketRecheckingChunkSize < 1) {
        throw config::InvalidConfigException(
                "Cannot have bucket rechecking chunk size of less than 1");
    }
    _maxPendingChunkSize = newConfig->bucketRecheckingChunkSize;
}


void
ModifiedBucketChecker::onOpen()
{
    framework::MilliSecTime maxProcessingTime(60 * 1000);
    framework::MilliSecTime waitTime(1000);
    if (!_singleThreadMode) {
        _thread = _component->startThread(*this, maxProcessingTime, waitTime);
    }
}

void
ModifiedBucketChecker::onClose()
{
    if (_singleThreadMode) {
        return;
    }
    assert(_thread.get() != 0);
    LOG(debug, "Interrupting modified bucket checker thread");
    _thread->interrupt();
    {
        vespalib::MonitorGuard guard(_monitor);
        guard.signal();
    }
    LOG(debug, "Joining modified bucket checker thread");
    _thread->join();
    LOG(debug, "Modified bucket checker thread joined");
    _thread.reset(0);
}

void
ModifiedBucketChecker::run(framework::ThreadHandle& thread)
{
    LOG(debug, "Started modified bucket checker thread with pid %d", getpid());

    while (!thread.interrupted()) {
        thread.registerTick();

        bool ok = tick();

        vespalib::MonitorGuard guard(_monitor);
        if (ok) {
            guard.wait(50);
        } else {
            guard.wait(100);
        }
    }
}

bool
ModifiedBucketChecker::onInternalReply(
        const std::shared_ptr<api::InternalReply>& r)
{
    if (r->getType() == RecheckBucketInfoReply::ID) {
        vespalib::LockGuard guard(_stateLock);
        assert(_pendingRequests > 0);
        --_pendingRequests;
        if (_pendingRequests == 0 && moreChunksRemaining()) {
            vespalib::MonitorGuard mg(_monitor);
            // Safe: monitor never taken alongside lock anywhere else.
            mg.signal(); // Immediately signal start of new chunk
        }
        return true;
    }
    return false;
}

bool
ModifiedBucketChecker::requestModifiedBucketsFromProvider(document::BucketSpace bucketSpace)
{
    spi::BucketIdListResult result(_provider.getModifiedBuckets(bucketSpace));
    if (result.hasError()) {
        LOG(debug, "getModifiedBuckets() failed: %s",
            result.toString().c_str());
        return false;
    }
    {
        vespalib::LockGuard guard(_stateLock);
        _rechecksNotStarted.reset(bucketSpace, result.getList());
    }
    return true;
}

void
ModifiedBucketChecker::nextRecheckChunk(
        std::vector<RecheckBucketInfoCommand::SP>& commandsToSend)
{
    assert(_pendingRequests == 0);
    assert(commandsToSend.empty());
    size_t n = std::min(_maxPendingChunkSize, _rechecksNotStarted.size());

    for (size_t i = 0; i < n; ++i) {
        document::Bucket bucket(_rechecksNotStarted.bucketSpace(), _rechecksNotStarted.back());
        commandsToSend.emplace_back(new RecheckBucketInfoCommand(bucket));
        _rechecksNotStarted.pop_back();
    }
    _pendingRequests = n;
    LOG(spam, "Prepared new recheck chunk with %zu commands", n);
}

void
ModifiedBucketChecker::dispatchAllToPersistenceQueues(
        const std::vector<RecheckBucketInfoCommand::SP>& commandsToSend)
{
    for (auto& cmd : commandsToSend) {
        // We assume sendDown doesn't throw, but that it may send a reply
        // up synchronously, so we cannot hold lock around it. We also make
        // the assumption that recheck commands are only discared if their
        // bucket no longer exists, so it's safe to not retry them.
        sendDown(cmd);
    }
}

bool
ModifiedBucketChecker::tick()
{
    // Do two phases of locking, as we want tick() to both fetch modified
    // buckets and send the first chunk for these in a single call. However,
    // we want getModifiedBuckets() to called outside the lock.
    bool shouldRequestFromProvider = false;
    {
        vespalib::LockGuard guard(_stateLock);
        if (!currentChunkFinished()) {
            return true;
        }
        shouldRequestFromProvider = !moreChunksRemaining();
    }
    if (shouldRequestFromProvider) {
        if (!requestModifiedBucketsFromProvider(_bucketSpaces->next())) {
            return false;
        }
    }

    std::vector<RecheckBucketInfoCommand::SP> commandsToSend;
    {
        vespalib::LockGuard guard(_stateLock);
        if (moreChunksRemaining()) {
            nextRecheckChunk(commandsToSend);
        } 
    }
    // Sending must be done outside the lock.
    if (!commandsToSend.empty()) {
        dispatchAllToPersistenceQueues(commandsToSend);
    } 
    return true;
}

} // ns storage
