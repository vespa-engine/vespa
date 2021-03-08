// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmovejobv2.h"
#include "imaintenancejobrunner.h"
#include "ibucketstatechangednotifier.h"
#include "iclusterstatechangednotifier.h"
#include "maintenancedocumentsubdb.h"
#include "i_disk_mem_usage_notifier.h"
#include "ibucketmodifiedhandler.h"
#include "move_operation_limiter.h"
#include "document_db_maintenance_config.h"
#include <vespa/searchcore/proton/metrics/documentdb_tagged_metrics.h>
#include <vespa/searchcore/proton/bucketdb/i_bucket_create_notifier.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/persistence/spi/bucket_tasks.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.bucketmovejob");

using document::BucketId;
using storage::spi::BucketInfo;
using storage::spi::Bucket;
using storage::spi::makeBucketTask;
using proton::bucketdb::BucketMover;
using vespalib::makeLambdaTask;
using vespalib::Trinary;

namespace proton {

namespace {

const char *
toStr(bool v) {
    return (v ? "T" : "F");
}

const char *
toStr(Trinary v) {
    return (v == Trinary::True) ? "T" : ((v == Trinary::False) ? "F" : "U");
}

bool
blockedDueToClusterState(const std::shared_ptr<IBucketStateCalculator> &calc)
{
    bool clusterUp = calc && calc->clusterUp();
    bool nodeUp = calc && calc->nodeUp();
    bool nodeInitializing = calc && calc->nodeInitializing();
    return !(clusterUp && nodeUp && !nodeInitializing);
}

}

BucketMoveJobV2::BucketMoveJobV2(const std::shared_ptr<IBucketStateCalculator> &calc,
                                 IDocumentMoveHandler &moveHandler,
                                 IBucketModifiedHandler &modifiedHandler,
                                 IThreadService & master,
                                 BucketExecutor & bucketExecutor,
                                 const MaintenanceDocumentSubDB &ready,
                                 const MaintenanceDocumentSubDB &notReady,
                                 bucketdb::IBucketCreateNotifier &bucketCreateNotifier,
                                 IClusterStateChangedNotifier &clusterStateChangedNotifier,
                                 IBucketStateChangedNotifier &bucketStateChangedNotifier,
                                 IDiskMemUsageNotifier &diskMemUsageNotifier,
                                 const BlockableMaintenanceJobConfig &blockableConfig,
                                 const vespalib::string &docTypeName,
                                 document::BucketSpace bucketSpace)
    : BlockableMaintenanceJob("move_buckets." + docTypeName, vespalib::duration::zero(), vespalib::duration::zero(), blockableConfig),
      IClusterStateChangedHandler(),
      bucketdb::IBucketCreateListener(),
      IBucketStateChangedHandler(),
      IDiskMemUsageListener(),
      _calc(calc),
      _moveHandler(moveHandler),
      _modifiedHandler(modifiedHandler),
      _master(master),
      _bucketExecutor(bucketExecutor),
      _ready(ready),
      _notReady(notReady),
      _bucketSpace(bucketSpace),
      _iterateCount(0),
      _movers(),
      _bucketsInFlight(),
      _buckets2Move(),
      _stopped(false),
      _startedCount(0),
      _executedCount(0),
      _bucketsPending(0),
      _bucketCreateNotifier(bucketCreateNotifier),
      _clusterStateChangedNotifier(clusterStateChangedNotifier),
      _bucketStateChangedNotifier(bucketStateChangedNotifier),
      _diskMemUsageNotifier(diskMemUsageNotifier)
{
    _movers.reserve(std::min(100u, blockableConfig.getMaxOutstandingMoveOps()));
    if (blockedDueToClusterState(_calc)) {
        setBlocked(BlockedReason::CLUSTER_STATE);
    }

    _bucketCreateNotifier.addListener(this);
    _clusterStateChangedNotifier.addClusterStateChangedHandler(this);
    _bucketStateChangedNotifier.addBucketStateChangedHandler(this);
    _diskMemUsageNotifier.addDiskMemUsageListener(this);
    recompute(_ready.meta_store()->getBucketDB().takeGuard());
}

BucketMoveJobV2::~BucketMoveJobV2()
{
    _bucketCreateNotifier.removeListener(this);
    _clusterStateChangedNotifier.removeClusterStateChangedHandler(this);
    _bucketStateChangedNotifier.removeBucketStateChangedHandler(this);
    _diskMemUsageNotifier.removeDiskMemUsageListener(this);
}

BucketMoveJobV2::NeedResult
BucketMoveJobV2::needMove(const ScanIterator &itr) const {
    NeedResult noMove(false, false);
    const bool hasReadyDocs = itr.hasReadyBucketDocs();
    const bool hasNotReadyDocs = itr.hasNotReadyBucketDocs();
    if (!hasReadyDocs && !hasNotReadyDocs) {
        return noMove; // No documents for bucket in ready or notready subdbs
    }
    const bool isActive = itr.isActive();
    // No point in moving buckets when node is retired and everything will be deleted soon.
    // However, allow moving of explicitly activated buckets, as this implies a lack of other good replicas.
    if (!_calc || (_calc->nodeRetired() && !isActive)) {
        return noMove;
    }
    const Trinary shouldBeReady = _calc->shouldBeReady(document::Bucket(_bucketSpace, itr.getBucket()));
    if (shouldBeReady == Trinary::Undefined) {
        return noMove;
    }
    const bool wantReady = (shouldBeReady == Trinary::True) || isActive;
    LOG(spam, "checkBucket(): bucket(%s), shouldBeReady(%s), active(%s)",
        itr.getBucket().toString().c_str(), toStr(shouldBeReady), toStr(isActive));
    if (wantReady) {
        if (!hasNotReadyDocs) {
            return noMove; // No notready bucket to make ready
        }
    } else {
        if (isActive) {
            return noMove; // Do not move rom ready to not ready when active
        }
        if (!hasReadyDocs) {
            return noMove; // No ready bucket to make notready
        }
    }
    return {true, wantReady};
}

namespace {

class IncOnDestruct {
public:
    IncOnDestruct(std::atomic<size_t> & count) : _count(count) {}
    ~IncOnDestruct() {
        _count.fetch_add(1, std::memory_order_relaxed);
    }
private:
    std::atomic<size_t> & _count;
};

}

class StartMove : public storage::spi::BucketTask {
public:
    using IDestructorCallbackSP = std::shared_ptr<vespalib::IDestructorCallback>;
    StartMove(BucketMoveJobV2 & job, std::shared_ptr<BucketMover> mover,
              std::vector<BucketMover::MoveKey> keys,
              IDestructorCallbackSP opsTracker)
        : _job(job),
          _mover(std::move(mover)),
          _keys(std::move(keys)),
          _opsTracker(std::move(opsTracker))
    {}

    void run(const Bucket &bucket, IDestructorCallbackSP onDone) override {
        assert(_mover->getBucket() == bucket.getBucketId());
        using DoneContext = vespalib::KeepAlive<std::pair<IDestructorCallbackSP, IDestructorCallbackSP>>;
        _job.prepareMove(std::move(_mover), std::move(_keys),
                         std::make_shared<DoneContext>(std::make_pair(std::move(_opsTracker), std::move(onDone))));
    }

    void fail(const Bucket &bucket) override {
        _job.failOperation(bucket.getBucketId());
    }

private:
    BucketMoveJobV2                   & _job;
    std::shared_ptr<BucketMover>        _mover;
    std::vector<BucketMover::MoveKey>   _keys;
    IDestructorCallbackSP               _opsTracker;
};

void
BucketMoveJobV2::failOperation(BucketId bucketId) {
    IncOnDestruct countGuard(_executedCount);
    _master.execute(makeLambdaTask([this, bucketId]() {
        if (_stopped.load(std::memory_order_relaxed)) return;
        considerBucket(_ready.meta_store()->getBucketDB().takeGuard(), bucketId);
    }));
}

void
BucketMoveJobV2::startMove(BucketMoverSP mover, size_t maxDocsToMove) {
    auto [keys, done] = mover->getKeysToMove(maxDocsToMove);
    if (done) {
        mover->setAllScheduled();
    }
    if (keys.empty()) return;
    mover->updateLastValidGid(keys.back()._gid);
    Bucket spiBucket(document::Bucket(_bucketSpace, mover->getBucket()));
    auto bucketTask = std::make_unique<StartMove>(*this, std::move(mover), std::move(keys), getLimiter().beginOperation());
    _startedCount.fetch_add(1, std::memory_order_relaxed);
    _bucketExecutor.execute(spiBucket, std::move(bucketTask));
}

void
BucketMoveJobV2::prepareMove(BucketMoverSP mover, std::vector<MoveKey> keys, IDestructorCallbackSP onDone)
{
    IncOnDestruct countGuard(_executedCount);
    auto moveOps = mover->createMoveOperations(std::move(keys));
    _master.execute(makeLambdaTask([this, mover=std::move(mover), moveOps=std::move(moveOps), onDone=std::move(onDone)]() mutable {
        if (_stopped.load(std::memory_order_relaxed)) return;
        completeMove(std::move(mover), std::move(moveOps), std::move(onDone));
    }));
}

void
BucketMoveJobV2::completeMove(BucketMoverSP mover, GuardedMoveOps ops, IDestructorCallbackSP onDone) {
    mover->moveDocuments(std::move(ops.success), std::move(onDone));
    ops.failed.clear();
    if (checkIfMoverComplete(*mover)) {
        reconsiderBucket(_ready.meta_store()->getBucketDB().takeGuard(), mover->getBucket());
    }
}

bool
BucketMoveJobV2::checkIfMoverComplete(const BucketMover & mover) {
    bool bucketMoveComplete = mover.allScheduled() && mover.inSync();
    bool needReschedule = mover.needReschedule();
    if (bucketMoveComplete || needReschedule) {
        BucketId bucket = mover.getBucket();
        auto found = _bucketsInFlight.find(bucket);
        if (needReschedule) {
            if ((found != _bucketsInFlight.end()) && (&mover == found->second.get())) {
                //Prevent old disconnected mover from creating havoc.
                _bucketsInFlight.erase(found);
                _movers.erase(std::remove_if(_movers.begin(), _movers.end(),
                                             [bucket](const BucketMoverSP &cand) {
                                                 return cand->getBucket() == bucket;
                                             }),
                              _movers.end());
                return true;
            }
        } else {
            assert(found != _bucketsInFlight.end());
            _bucketsInFlight.erase(found);
            _modifiedHandler.notifyBucketModified(bucket);
        }
    }
    updatePending();
    return false;
}

void
BucketMoveJobV2::cancelBucket(BucketId bucket) {
    auto inFlight = _bucketsInFlight.find(bucket);
    if (inFlight != _bucketsInFlight.end()) {
        inFlight->second->cancel();
        checkIfMoverComplete(*inFlight->second);
    }
}

void
BucketMoveJobV2::considerBucket(const bucketdb::Guard & guard, BucketId bucket) {
    cancelBucket(bucket);
    assert( !_bucketsInFlight.contains(bucket));
    reconsiderBucket(guard, bucket);
}

void
BucketMoveJobV2::reconsiderBucket(const bucketdb::Guard & guard, BucketId bucket) {
    assert( ! _bucketsInFlight.contains(bucket));
    ScanIterator itr(guard, bucket);
    auto [mustMove, wantReady] = needMove(itr);
    if (mustMove) {
        _buckets2Move[bucket] = wantReady;
    } else {
        _buckets2Move.erase(bucket);
    }
    updatePending();
    considerRun();
}

void
BucketMoveJobV2::notifyCreateBucket(const bucketdb::Guard & guard, const BucketId &bucket)
{
    considerBucket(guard, bucket);
}

BucketMoveJobV2::BucketMoveSet
BucketMoveJobV2::computeBuckets2Move(const bucketdb::Guard & guard)
{
    BucketMoveJobV2::BucketMoveSet toMove;
    for (ScanIterator itr(guard, BucketId()); itr.valid(); ++itr) {
        auto [mustMove, wantReady] = needMove(itr);
        if (mustMove) {
            toMove[itr.getBucket()] = wantReady;
        }
    }
    return toMove;
}

std::shared_ptr<BucketMover>
BucketMoveJobV2::createMover(BucketId bucket, bool wantReady) {
    const MaintenanceDocumentSubDB &source(wantReady ? _notReady : _ready);
    const MaintenanceDocumentSubDB &target(wantReady ? _ready : _notReady);
    LOG(debug, "checkBucket(): mover.setupForBucket(%s, source:%u, target:%u)",
        bucket.toString().c_str(), source.sub_db_id(), target.sub_db_id());
    return std::make_shared<BucketMover>(bucket, &source, target.sub_db_id(), _moveHandler);
}

std::shared_ptr<BucketMover>
BucketMoveJobV2::greedyCreateMover() {
    if ( ! _buckets2Move.empty()) {
        auto next = _buckets2Move.begin();
        auto mover = createMover(next->first, next->second);
        _buckets2Move.erase(next);
        return mover;
    }
    return {};
}

void
BucketMoveJobV2::moveDocs(size_t maxDocsToMove) {
    backFillMovers();
    if (_movers.empty()) return;

    // Select mover
    size_t index = _iterateCount++ % _movers.size();
    const auto & mover = _movers[index];

    //Move, or reduce movers as we are tailing off
    if (!mover->allScheduled()) {
        startMove(mover, maxDocsToMove);
        if (mover->allScheduled()) {
            _movers.erase(_movers.begin() + index);
        }
    }
}

bool
BucketMoveJobV2::scanAndMove(size_t maxBuckets2Move, size_t maxDocsToMovePerBucket) {
    for (size_t i(0); i < maxBuckets2Move; i++) {
        moveDocs(maxDocsToMovePerBucket);
    }
    return isBlocked() || done();
}

bool
BucketMoveJobV2::done() const {
    return _buckets2Move.empty() && _movers.empty() && !isBlocked();
}

bool
BucketMoveJobV2::run()
{
    if (isBlocked()) {
        return true; // indicate work is done, since node state is bad
    }
    /// Returning false here will immediately post the job back on the executor. This will give a busy loop,
    /// but this is considered fine as it is very rare and it will be intermingled with multiple feed operations.
    if ( ! scanAndMove(1, 1) ) {
        return false;
    }

    if (isBlocked(BlockedReason::OUTSTANDING_OPS)) {
        return true;
    }
    return done();
}

void
BucketMoveJobV2::recompute() {
    recompute(_ready.meta_store()->getBucketDB().takeGuard());
}
void
BucketMoveJobV2::recompute(const bucketdb::Guard & guard) {
    _buckets2Move = computeBuckets2Move(guard);
    updatePending();
}

void
BucketMoveJobV2::backFillMovers() {
    // Ensure we have enough movers.
    while ( ! _buckets2Move.empty() && (_movers.size() < _movers.capacity())) {
        auto mover = greedyCreateMover();
        _movers.push_back(mover);
        auto bucketId = mover->getBucket();
        assert( ! _bucketsInFlight.contains(bucketId));
        _bucketsInFlight[bucketId] = std::move(mover);
    }
    updatePending();
}

void
BucketMoveJobV2::notifyClusterStateChanged(const std::shared_ptr<IBucketStateCalculator> &newCalc)
{
    // Called by master write thread
    _calc = newCalc;
    if (blockedDueToClusterState(_calc)) {
        setBlocked(BlockedReason::CLUSTER_STATE);
    } else {
        unBlock(BlockedReason::CLUSTER_STATE);
        _movers.clear();
        std::for_each(_bucketsInFlight.begin(), _bucketsInFlight.end(), [](auto & entry) { entry.second->cancel();});
        _bucketsInFlight.clear();
        recompute(_ready.meta_store()->getBucketDB().takeGuard());
    }
}

void
BucketMoveJobV2::notifyBucketStateChanged(const BucketId &bucketId, BucketInfo::ActiveState)
{
    // Called by master write thread
    considerBucket(_ready.meta_store()->getBucketDB().takeGuard(), bucketId);
}

void
BucketMoveJobV2::notifyDiskMemUsage(DiskMemUsageState state)
{
    // Called by master write thread
    internalNotifyDiskMemUsage(state);
}

bool
BucketMoveJobV2::inSync() const {
    return _executedCount == _startedCount;
}

void
BucketMoveJobV2::onStop() {
    // Called by master write thread
    _stopped = true;
    while ( ! inSync() ) {
        std::this_thread::sleep_for(1ms);
    }
}

void
BucketMoveJobV2::updatePending() {
    _bucketsPending.store(_bucketsInFlight.size() + _buckets2Move.size(), std::memory_order_relaxed);
}

void
BucketMoveJobV2::updateMetrics(DocumentDBTaggedMetrics & metrics) {
    // This is an over estimate to ensure we do not count down to zero until everything has been and completed and acked.
    metrics.bucketMove.bucketsPending.set(_bucketsPending.load(std::memory_order_relaxed) +
                                          getLimiter().numPending());
}

} // namespace proton
