// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "buckethandler.h"
#include "ibucketstatechangedhandler.h"
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.buckethandler");

using document::BucketId;
using storage::spi::Bucket;
using storage::spi::BucketChecksum;
using storage::spi::BucketIdListResult;
using storage::spi::BucketInfo;
using storage::spi::BucketInfoResult;
using storage::spi::Result;
using vespalib::Executor;
using vespalib::makeLambdaTask;

namespace proton {

void
BucketHandler::performSetCurrentState(BucketId bucketId,
                                      storage::spi::BucketInfo::ActiveState newState,
                                      IGenericResultHandler *resultHandler)
{
    if (!_nodeUp) {
        Result result(Result::ErrorType::TRANSIENT_ERROR,
                      "Cannot set bucket active state when node is down");
        resultHandler->handle(result);
        return;
    }
    bool active = (newState == storage::spi::BucketInfo::ACTIVE);
    LOG(debug, "performSetCurrentState(%s, %s)",
        bucketId.toString().c_str(), (active ? "ACTIVE" : "NOT_ACTIVE"));
    _ready->setBucketState(bucketId, active);
    for (const auto & ch : _changedHandlers) {
        ch->notifyBucketStateChanged(bucketId, newState);
    }
    resultHandler->handle(Result());
}

void
BucketHandler::performPopulateActiveBuckets(document::BucketId::List buckets,
                                            IGenericResultHandler *resultHandler)
{
    _ready->populateActiveBuckets(std::move(buckets));
    resultHandler->handle(Result());
}

void
BucketHandler::deactivateAllActiveBuckets()
{
    BucketId::List buckets = _ready->getBucketDB().takeGuard()->getActiveBuckets();
    for (auto bucketId : buckets) {
        _ready->setBucketState(bucketId, storage::spi::BucketInfo::NOT_ACTIVE);
        // Don't notify bucket state changed, node is marked down so
        // noone is listening.
    }
}

BucketHandler::BucketHandler(vespalib::Executor &executor)
    : IClusterStateChangedHandler(),
      IBucketStateChangedNotifier(),
      _executor(executor),
      _ready(nullptr),
      _changedHandlers(),
      _nodeUp(false),
      _nodeMaintenance(false)
{
    LOG(spam, "BucketHandler::BucketHandler");
}

BucketHandler::~BucketHandler()
{
    assert(_changedHandlers.empty());
}

void
BucketHandler::setReadyBucketHandler(documentmetastore::IBucketHandler &ready)
{
    _ready = &ready;
}

void
BucketHandler::handleListBuckets(IBucketIdListResultHandler &resultHandler)
{
    // Called by SPI thread.
    // BucketDBOwner ensures synchronization between SPI thread and
    // master write thread in document database.
    BucketIdListResult::List buckets = _ready->getBucketDB().takeGuard()->getBuckets();
    resultHandler.handle(BucketIdListResult(std::move(buckets)));
}

void
BucketHandler::handleSetCurrentState(const BucketId &bucketId,
                                     storage::spi::BucketInfo::ActiveState newState,
                                     std::shared_ptr<IGenericResultHandler> resultHandlerSP)
{
    _executor.execute(makeLambdaTask([this, bucketId, newState, resultHandler = std::move(resultHandlerSP)]() {
        performSetCurrentState(bucketId, newState, resultHandler.get());
    }));
}

void
BucketHandler::handleGetBucketInfo(const Bucket &bucket,
                                   IBucketInfoResultHandler &resultHandler)
{
    // Called by SPI thread.
    // BucketDBOwner ensures synchronization between SPI thread and
    // master write thread in document database.
    BucketInfo bucketInfo = _ready->getBucketDB().takeGuard()->cachedGetBucketInfo(bucket);
    LOG(spam, "handleGetBucketInfo(%s): %s",
        bucket.toString().c_str(), bucketInfo.toString().c_str());
    resultHandler.handle(BucketInfoResult(bucketInfo));
}

bool
BucketHandler::hasBucket(const storage::spi::Bucket &bucket) {
    return _ready->getBucketDB().takeGuard()->hasBucket(bucket);
}

void
BucketHandler::handleListActiveBuckets(IBucketIdListResultHandler &resultHandler)
{
    // Called by SPI thread.
    // BucketDBOwner ensures synchronization between SPI thread and
    // master write thread in document database.

    resultHandler.handle(BucketIdListResult(_ready->getBucketDB().takeGuard()->getActiveBuckets()));
}

void
BucketHandler::handlePopulateActiveBuckets(document::BucketId::List buckets,
                                           IGenericResultHandler &resultHandler)
{
    _executor.execute(makeLambdaTask([this, moved_buckets=std::move(buckets), &resultHandler]() mutable {
        performPopulateActiveBuckets(std::move(moved_buckets), &resultHandler);
    }));
}

namespace {
constexpr const char* bool_str(bool v) noexcept {
    return v ? "true" : "false";
}
}

void
BucketHandler::notifyClusterStateChanged(const std::shared_ptr<IBucketStateCalculator> & newCalc)
{
    bool oldNodeUp = _nodeUp;
    bool oldNodeMaintenance = _nodeMaintenance;
    _nodeUp = newCalc->nodeUp(); // Up, Retired or Initializing
    _nodeMaintenance = newCalc->nodeMaintenance();
    LOG(spam, "notifyClusterStateChanged; up: %s -> %s, maintenance: %s -> %s",
        bool_str(oldNodeUp), bool_str(_nodeUp),
        bool_str(oldNodeMaintenance), bool_str(_nodeMaintenance));
    if (_nodeMaintenance) {
        return; // Don't deactivate buckets in maintenance mode; let query traffic drain away naturally.
    }
    // We implicitly deactivate buckets in two edge cases:
    //  - Up -> Down (not maintenance; handled above), since the node can not be expected to offer
    //    any graceful query draining when set Down.
    //  - Maintenance -> !Maintenance, since we'd otherwise introduce a bunch of transient duplicate
    //    results into queries if we transition to an available state.
    //    The assumption is that the system has already activated buckets on other nodes in such a scenario.
    if ((oldNodeUp && !_nodeUp) || oldNodeMaintenance) {
        deactivateAllActiveBuckets();
    }
}

void
BucketHandler::addBucketStateChangedHandler(IBucketStateChangedHandler *handler)
{
    _changedHandlers.push_back(handler);
}

void
BucketHandler::
removeBucketStateChangedHandler(IBucketStateChangedHandler *handler)
{
    // Called by executor thread
    auto it = std::find(_changedHandlers.begin(), _changedHandlers.end(), handler);
    if (it != _changedHandlers.end()) {
        _changedHandlers.erase(it);
    }

}

} // namespace proton
