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
#include <vespa/searchcore/proton/bucketdb/i_bucket_create_notifier.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/persistence/spi/bucket_tasks.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/lambdatask.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.bucketmovejob");

using document::BucketId;
using storage::spi::BucketInfo;
using storage::spi::Bucket;
using storage::spi::makeBucketTask;
using proton::bucketdb::BucketMover;
using vespalib::makeLambdaTask;

namespace proton {

namespace {

const char * bool2str(bool v) { return (v ? "T" : "F"); }

bool
blockedDueToClusterState(const IBucketStateCalculator::SP &calc)
{
    bool clusterUp = calc && calc->clusterUp();
    bool nodeUp = calc && calc->nodeUp();
    bool nodeInitializing = calc && calc->nodeInitializing();
    return !(clusterUp && nodeUp && !nodeInitializing);
}

}

BucketMoveJobV2::BucketMoveJobV2(const IBucketStateCalculator::SP &calc,
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
      _buckets2Move(),
      _stopped(false),
      _startedCount(0),
      _executedCount(0),
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
    recompute();
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
    const bool shouldBeReady = _calc->shouldBeReady(document::Bucket(_bucketSpace, itr.getBucket()));
    const bool wantReady = shouldBeReady || isActive;
    LOG(spam, "checkBucket(): bucket(%s), shouldBeReady(%s), active(%s)",
        itr.getBucket().toString().c_str(), bool2str(shouldBeReady), bool2str(isActive));
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

void
BucketMoveJobV2::startMove(BucketMoverSP mover, size_t maxDocsToMove) {
    auto [keys, done] = mover->getKeysToMove(maxDocsToMove);
    if (done) {
        mover->setBucketDone();
    }
    if (keys.empty()) return;
    if (_stopped.load(std::memory_order_relaxed)) return;
    mover->updateLastValidGid(keys.back()._gid);
    auto context = getLimiter().beginOperation();
    Bucket spiBucket(document::Bucket(_bucketSpace, mover->getBucket()));
    auto bucketTask = makeBucketTask(
            [this, mover=std::move(mover), keys=std::move(keys),opsTracker=getLimiter().beginOperation()]
            (const Bucket & bucket, std::shared_ptr<IDestructorCallback> onDone) mutable
    {
        assert(mover->getBucket() == bucket.getBucketId());
        using DoneContext = vespalib::KeepAlive<std::pair<IDestructorCallbackSP, IDestructorCallbackSP>>;
        prepareMove(std::move(mover), std::move(keys),
                    std::make_shared<DoneContext>(std::make_pair(std::move(opsTracker), std::move(onDone))));
    });
    auto failed = _bucketExecutor.execute(spiBucket, std::move(bucketTask));
    if (!failed) {
        _startedCount.fetch_add(1, std::memory_order_relaxed);
    }
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

void
BucketMoveJobV2::prepareMove(BucketMoverSP mover, std::vector<MoveKey> keys, IDestructorCallbackSP onDone)
{
    IncOnDestruct countGuard(_executedCount);
    if (_stopped.load(std::memory_order_relaxed)) return;
    auto moveOps = mover->createMoveOperations(keys);
    _master.execute(makeLambdaTask([this, mover=std::move(mover), moveOps=std::move(moveOps), onDone=std::move(onDone)]() mutable {
        completeMove(std::move(mover), std::move(moveOps), std::move(onDone));
    }));
}

void
BucketMoveJobV2::completeMove(BucketMoverSP mover, std::vector<GuardedMoveOp> ops, IDestructorCallbackSP onDone) {
    mover->moveDocuments(std::move(ops), std::move(onDone));
    if (mover->bucketDone() && mover->inSync()) {
        _modifiedHandler.notifyBucketModified(mover->getBucket());
    }
}

void
BucketMoveJobV2::cancelMovesForBucket(BucketId bucket) {
    for (auto itr = _movers.begin(); itr != _movers.end(); itr++) {
        if (bucket == (*itr)->getBucket()) {
            _movers.erase(itr);
            backFillMovers();
            return;
        }
    }
}

void
BucketMoveJobV2::considerBucket(const bucketdb::Guard & guard, BucketId bucket)
{
    ScanIterator itr(guard, bucket);
    auto [mustMove, wantReady] = needMove(itr);
    if (mustMove) {
        _buckets2Move[bucket] = wantReady;
    } else {
        _buckets2Move.erase(bucket);
        cancelMovesForBucket(bucket);
    }
    backFillMovers();
    considerRun();
}

void
BucketMoveJobV2::notifyCreateBucket(const bucketdb::Guard & guard, const BucketId &bucket)
{
    considerBucket(guard, bucket);
}

BucketMoveJobV2::BucketSet
BucketMoveJobV2::computeBuckets2Move()
{
    BucketMoveJobV2::BucketSet toMove;
    for (ScanIterator itr(_ready.meta_store()->getBucketDB().takeGuard(), BucketId()); itr.valid(); ++itr) {
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

bool
BucketMoveJobV2::moveDocs(size_t maxDocsToMove) {
    if (done()) return true;

    // Select mover
    size_t index = _iterateCount++ % _movers.size();
    const auto & mover = _movers[index];

    //Move, or reduce movers as we are tailing off
    if (!mover->bucketDone()) {
        startMove(mover, maxDocsToMove);
        if (mover->bucketDone()) {
            auto next = greedyCreateMover();
            if (next) {
                _movers[index] = next;
            } else {
                _movers.erase(_movers.begin() + index);
            }
        }
    }
    return done();
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
    _movers.clear();
    _buckets2Move = computeBuckets2Move();
    backFillMovers();
}

void
BucketMoveJobV2::backFillMovers() {
    // Ensure we have enough movers.
    while ( ! _buckets2Move.empty() && (_movers.size() < _movers.capacity())) {
        _movers.push_back(greedyCreateMover());
    }
}
void
BucketMoveJobV2::notifyClusterStateChanged(const IBucketStateCalculator::SP &newCalc)
{
    // Called by master write thread
    _calc = newCalc;
    if (blockedDueToClusterState(_calc)) {
        setBlocked(BlockedReason::CLUSTER_STATE);
    } else {
        unBlock(BlockedReason::CLUSTER_STATE);
        recompute();
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

} // namespace proton
