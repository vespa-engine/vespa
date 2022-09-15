// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmovejob.h"
#include "imaintenancejobrunner.h"
#include "ibucketstatechangednotifier.h"
#include "iclusterstatechangednotifier.h"
#include "i_disk_mem_usage_notifier.h"
#include "ibucketmodifiedhandler.h"
#include "document_db_maintenance_config.h"
#include <vespa/searchcore/proton/metrics/documentdb_tagged_metrics.h>
#include <vespa/searchcore/proton/bucketdb/i_bucket_create_notifier.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
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
using proton::bucketdb::BucketMover;
using vespalib::RetainGuard;
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

BucketMoveJob::BucketMoveJob(std::shared_ptr<IBucketStateCalculator> calc,
                             RetainGuard dbRetainer,
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
      std::enable_shared_from_this<BucketMoveJob>(),
      _calc(std::move(calc)),
      _dbRetainer(std::move(dbRetainer)),
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

BucketMoveJob::~BucketMoveJob()
{
    _bucketCreateNotifier.removeListener(this);
    _clusterStateChangedNotifier.removeClusterStateChangedHandler(this);
    _bucketStateChangedNotifier.removeBucketStateChangedHandler(this);
    _diskMemUsageNotifier.removeDiskMemUsageListener(this);
}

std::shared_ptr<BucketMoveJob>
BucketMoveJob::create(std::shared_ptr<IBucketStateCalculator> calc,
                      RetainGuard dbRetainer,
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
{
    return {new BucketMoveJob(std::move(calc), std::move(dbRetainer), moveHandler, modifiedHandler, master, bucketExecutor, ready, notReady,
                              bucketCreateNotifier, clusterStateChangedNotifier, bucketStateChangedNotifier,
                              diskMemUsageNotifier, blockableConfig, docTypeName, bucketSpace),
            [&master](auto job) {
                auto failed = master.execute(makeLambdaTask([job]() { delete job; }));
                assert(!failed);
            }};
}

BucketMoveJob::NeedResult
BucketMoveJob::needMove(BucketId bucketId, const BucketStateWrapper &itr) const {
    NeedResult noMove(false, false);
    const bool hasReadyDocs = itr.hasReadyBucketDocs();
    const bool hasNotReadyDocs = itr.hasNotReadyBucketDocs();
    if (!hasReadyDocs && !hasNotReadyDocs) {
        return noMove; // No documents for bucket in ready or notready subdbs
    }
    // No point in moving buckets when node is retired and everything will be deleted soon.
    if (!_calc || _calc->nodeRetired()) {
        return noMove;
    }
    const Trinary shouldBeReady = _calc->shouldBeReady(document::Bucket(_bucketSpace, bucketId));
    if (shouldBeReady == Trinary::Undefined) {
        return noMove;
    }
    const bool isActive = itr.isActive();
    const bool wantReady = (shouldBeReady == Trinary::True);
    LOG(spam, "needMove(): bucket(%s), shouldBeReady(%s), active(%s)",
        bucketId.toString().c_str(), toStr(shouldBeReady), toStr(isActive));
    if (wantReady) {
        if (!hasNotReadyDocs) {
            return noMove; // No notready bucket to make ready
        }
    } else {
        if (isActive) {
            return noMove; // Do not move from ready to not ready when active
        }
        if (!hasReadyDocs) {
            return noMove; // No ready bucket to make notready
        }
    }
    return {true, wantReady};
}

class BucketMoveJob::StartMove : public storage::spi::BucketTask {
public:
    using IDestructorCallbackSP = std::shared_ptr<vespalib::IDestructorCallback>;
    StartMove(std::shared_ptr<BucketMoveJob> job, BucketMover::MoveKeys keys, IDestructorCallbackSP opsTracker)
        : _job(std::move(job)),
          _keys(std::move(keys)),
          _opsTracker(std::move(opsTracker))
    {}

    void run(const Bucket &bucket, IDestructorCallbackSP onDone) override {
        assert(_keys.mover().getBucket() == bucket.getBucketId());
        using DoneContext = vespalib::KeepAlive<std::pair<IDestructorCallbackSP, IDestructorCallbackSP>>;
        BucketMoveJob::prepareMove(std::move(_job), std::move(_keys),
                                   std::make_shared<DoneContext>(std::make_pair(std::move(_opsTracker), std::move(onDone))));
    }

    void fail(const Bucket &bucket) override {
        BucketMoveJob::failOperation(std::move(_job), bucket.getBucketId());
    }

private:
    std::shared_ptr<BucketMoveJob> _job;
    BucketMover::MoveKeys          _keys;
    IDestructorCallbackSP          _opsTracker;
};

void
BucketMoveJob::failOperation(std::shared_ptr<BucketMoveJob> job, BucketId bucketId) {
    auto & master = job->_master;
    if (job->stopped()) return;
    master.execute(makeLambdaTask([job=std::move(job), bucketId]() {
        if (job->stopped()) return;
        job->considerBucket(job->_ready.meta_store()->getBucketDB().takeGuard(), bucketId);
    }));
}

void
BucketMoveJob::startMove(BucketMover & mover, size_t maxDocsToMove) {
    auto [keys, done] = mover.getKeysToMove(maxDocsToMove);
    if (done) {
        mover.setAllScheduled();
    }
    if (keys.empty()) return;
    mover.updateLastValidGid(keys.back()._gid);
    Bucket spiBucket(document::Bucket(_bucketSpace, mover.getBucket()));
    auto bucketTask = std::make_unique<StartMove>(shared_from_this(), std::move(keys), getLimiter().beginOperation());
    _bucketExecutor.execute(spiBucket, std::move(bucketTask));
}

void
BucketMoveJob::prepareMove(std::shared_ptr<BucketMoveJob> job, BucketMover::MoveKeys keys, IDestructorCallbackSP onDone)
{
    if (job->stopped()) return; //TODO Remove once lidtracker is no longer in use.
    auto moveOps = keys.createMoveOperations();
    auto & master = job->_master;
    if (job->stopped()) return;
    master.execute(makeLambdaTask([job=std::move(job), moveOps=std::move(moveOps), onDone=std::move(onDone)]() mutable {
        if (job->stopped()) return;
        job->completeMove(std::move(moveOps), std::move(onDone));
    }));
}

void
BucketMoveJob::completeMove(GuardedMoveOps ops, IDestructorCallbackSP onDone) {
    BucketMover & mover = ops.mover();
    if (mover.cancelled()) {
        LOG(spam, "completeMove(%s, mover@%p): mover already cancelled, not processing it further",
            mover.getBucket().toString().c_str(), &mover);
        return;
    }
    mover.moveDocuments(std::move(ops.success()), std::move(onDone));
    ops.failed().clear();
    if (checkIfMoverComplete(mover)) {
        reconsiderBucket(_ready.meta_store()->getBucketDB().takeGuard(), mover.getBucket());
    }
}

bool
BucketMoveJob::checkIfMoverComplete(const BucketMover & mover) {
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
BucketMoveJob::cancelBucket(BucketId bucket) {
    auto inFlight = _bucketsInFlight.find(bucket);
    if (inFlight != _bucketsInFlight.end()) {
        LOG(spam, "cancelBucket(%s): cancelling existing mover %p", bucket.toString().c_str(), inFlight->second.get());
        inFlight->second->cancel();
        checkIfMoverComplete(*inFlight->second);
    }
}

void
BucketMoveJob::considerBucket(const bucketdb::Guard & guard, BucketId bucket) {
    cancelBucket(bucket);
    assert( !_bucketsInFlight.contains(bucket));
    reconsiderBucket(guard, bucket);
}

void
BucketMoveJob::reconsiderBucket(const bucketdb::Guard & guard, BucketId bucket) {
    assert( ! _bucketsInFlight.contains(bucket));
    auto [mustMove, wantReady] = needMove(bucket, BucketStateWrapper(guard->get(bucket)));
    if (mustMove) {
        _buckets2Move[bucket] = wantReady;
    } else {
        _buckets2Move.erase(bucket);
    }
    updatePending();
    considerRun();
}

void
BucketMoveJob::notifyCreateBucket(const bucketdb::Guard & guard, const BucketId &bucket)
{
    considerBucket(guard, bucket);
}

BucketMoveJob::BucketMoveSet
BucketMoveJob::computeBuckets2Move(const bucketdb::Guard & guard)
{
    BucketMoveJob::BucketMoveSet toMove;
    BucketId::List buckets = guard->getBuckets();
    for (BucketId bucketId : buckets) {
        auto [mustMove, wantReady] = needMove(bucketId, BucketStateWrapper(guard->get(bucketId)));
        if (mustMove) {
            toMove[bucketId] = wantReady;
        }
    }
    return toMove;
}

std::shared_ptr<BucketMover>
BucketMoveJob::createMover(BucketId bucket, bool wantReady) {
    const MaintenanceDocumentSubDB &source(wantReady ? _notReady : _ready);
    const MaintenanceDocumentSubDB &target(wantReady ? _ready : _notReady);
    LOG(debug, "createMover(): BucketMover::create(%s, source:%u, target:%u)",
        bucket.toString().c_str(), source.sub_db_id(), target.sub_db_id());
    return BucketMover::create(bucket, &source, target.sub_db_id(), _moveHandler);
}

std::shared_ptr<BucketMover>
BucketMoveJob::greedyCreateMover() {
    if ( ! _buckets2Move.empty()) {
        auto next = _buckets2Move.begin();
        auto mover = createMover(next->first, next->second);
        _buckets2Move.erase(next);
        return mover;
    }
    return {};
}

void
BucketMoveJob::moveDocs(size_t maxDocsToMove) {
    backFillMovers();
    if (_movers.empty()) return;

    // Select mover
    size_t index = _iterateCount++ % _movers.size();
    auto & mover = *_movers[index];

    //Move, or reduce movers as we are tailing off
    if (!mover.allScheduled()) {
        startMove(mover, maxDocsToMove);
        if (mover.allScheduled()) {
            _movers.erase(_movers.begin() + index);
        }
    }
}

bool
BucketMoveJob::scanAndMove(size_t maxBuckets2Move, size_t maxDocsToMovePerBucket) {
    for (size_t i(0); i < maxBuckets2Move; i++) {
        moveDocs(maxDocsToMovePerBucket);
    }
    return isBlocked() || done();
}

bool
BucketMoveJob::done() const {
    return _buckets2Move.empty() && _movers.empty() && !isBlocked();
}

bool
BucketMoveJob::run()
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
BucketMoveJob::recompute() {
    recompute(_ready.meta_store()->getBucketDB().takeGuard());
}
void
BucketMoveJob::recompute(const bucketdb::Guard & guard) {
    _buckets2Move = computeBuckets2Move(guard);
    updatePending();
}

void
BucketMoveJob::backFillMovers() {
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
BucketMoveJob::notifyClusterStateChanged(const std::shared_ptr<IBucketStateCalculator> &newCalc)
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
BucketMoveJob::notifyBucketStateChanged(const BucketId &bucketId, BucketInfo::ActiveState)
{
    // Called by master write thread
    considerBucket(_ready.meta_store()->getBucketDB().takeGuard(), bucketId);
}

void
BucketMoveJob::notifyDiskMemUsage(DiskMemUsageState state)
{
    // Called by master write thread
    internalNotifyDiskMemUsage(state);
}

void
BucketMoveJob::updatePending() {
    _bucketsPending.store(_bucketsInFlight.size() + _buckets2Move.size(), std::memory_order_relaxed);
}

void
BucketMoveJob::updateMetrics(DocumentDBTaggedMetrics & metrics) const {
    // This is an over estimate to ensure we do not count down to zero until everything has been and completed and acked.
    metrics.bucketMove.bucketsPending.set(_bucketsPending.load(std::memory_order_relaxed) +
                                          getLimiter().numPending());
}

} // namespace proton
