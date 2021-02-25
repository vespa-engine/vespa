// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmovejob.h"
#include "imaintenancejobrunner.h"
#include "ibucketstatechangednotifier.h"
#include "iclusterstatechangednotifier.h"
#include "maintenancedocumentsubdb.h"
#include "i_disk_mem_usage_notifier.h"
#include "ibucketmodifiedhandler.h"
#include "move_operation_limiter.h"
#include <vespa/searchcore/proton/bucketdb/i_bucket_create_notifier.h>
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.bucketmovejob");

using document::BucketId;
using storage::spi::BucketInfo;
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

}

void
BucketMoveJob::checkBucket(const BucketId &bucket,
                           ScanIterator &itr,
                           DocumentBucketMover &mover,
                           IFrozenBucketHandler::ExclusiveBucketGuard::UP & bucketGuard)
{
    const bool hasReadyDocs = itr.hasReadyBucketDocs();
    const bool hasNotReadyDocs = itr.hasNotReadyBucketDocs();
    if (!hasReadyDocs && !hasNotReadyDocs) {
        return; // No documents for bucket in ready or notready subdbs
    }
    const bool isActive = itr.isActive();
    // No point in moving buckets when node is retired and everything will be deleted soon.
    // However, allow moving of explicitly activated buckets, as this implies a lack of other good replicas.
    if (_calc->nodeRetired() && !isActive) {
        return;
    }
    Trinary shouldBeReady = _calc->shouldBeReady(document::Bucket(_bucketSpace, bucket));
    if (shouldBeReady == vespalib::Trinary::Undefined) {
        return;
    }
    const bool wantReady = (shouldBeReady == Trinary::True) || isActive;
    LOG(spam, "checkBucket(): bucket(%s), shouldBeReady(%s), active(%s)",
              bucket.toString().c_str(), toStr(shouldBeReady), toStr(isActive));
    if (wantReady) {
        if (!hasNotReadyDocs)
            return; // No notready bucket to make ready
    } else {
        if (!hasReadyDocs)
            return; // No ready bucket to make notready
    }
    bucketGuard = _frozenBuckets.acquireExclusiveBucket(bucket);
    if ( ! bucketGuard ) {
        LOG(debug, "checkBucket(): delay frozen bucket: (%s)", bucket.toString().c_str());
        _delayedBucketsFrozen.insert(bucket);
        _delayedBuckets.erase(bucket);
        return;
    }
    const MaintenanceDocumentSubDB &source(wantReady ? _notReady : _ready);
    const MaintenanceDocumentSubDB &target(wantReady ? _ready : _notReady);
    LOG(debug, "checkBucket(): mover.setupForBucket(%s, source:%u, target:%u)",
        bucket.toString().c_str(), source.sub_db_id(), target.sub_db_id());
    mover.setupForBucket(bucket, &source, target.sub_db_id(), _moveHandler);
}

BucketMoveJob::ScanResult
BucketMoveJob::scanBuckets(size_t maxBucketsToScan, IFrozenBucketHandler::ExclusiveBucketGuard::UP & bucketGuard)
{
    size_t bucketsScanned = 0;
    bool passDone = false;
    ScanIterator itr(_ready.meta_store()->getBucketDB().takeGuard(),
            _scanPass, _scanPos._lastBucket, _endPos._lastBucket);
    BucketId bucket;
    for (; itr.valid() &&
             bucketsScanned < maxBucketsToScan && _mover.bucketDone();
         ++itr, ++bucketsScanned)
    {
        bucket = itr.getBucket();
        _scanPos._lastBucket = bucket;
        checkBucket(bucket, itr, _mover, bucketGuard);
    }
    if (!itr.valid()) {
        passDone = true;
        _scanPos._lastBucket = BucketId();
    }
    return ScanResult(bucketsScanned, passDone);
}

bool
BucketMoveJob::moveDocuments(DocumentBucketMover &mover,
                             size_t maxDocsToMove,
                             IFrozenBucketHandler::ExclusiveBucketGuard::UP & bucketGuard)
{
    if ( ! bucketGuard ) {
        bucketGuard = _frozenBuckets.acquireExclusiveBucket(mover.getBucket());
        if (! bucketGuard) {
            maybeDelayMover(mover, mover.getBucket());
            return true;
        }
    }
    assert(mover.getBucket() == bucketGuard->getBucket());
    if ( ! mover.moveDocuments(maxDocsToMove)) {
        return false;
    }
    if (mover.bucketDone()) {
        _modifiedHandler.notifyBucketModified(mover.getBucket());
    }
    return true;
}

namespace {

bool
blockedDueToClusterState(const std::shared_ptr<IBucketStateCalculator> &calc)
{
    bool clusterUp = calc.get() != nullptr && calc->clusterUp();
    bool nodeUp = calc.get() != nullptr && calc->nodeUp();
    bool nodeInitializing = calc.get() != nullptr && calc->nodeInitializing();
    return !(clusterUp && nodeUp && !nodeInitializing);
}

}

BucketMoveJob::
BucketMoveJob(const std::shared_ptr<IBucketStateCalculator> &calc,
              IDocumentMoveHandler &moveHandler,
              IBucketModifiedHandler &modifiedHandler,
              const MaintenanceDocumentSubDB &ready,
              const MaintenanceDocumentSubDB &notReady,
              IFrozenBucketHandler &frozenBuckets,
              bucketdb::IBucketCreateNotifier &bucketCreateNotifier,
              IClusterStateChangedNotifier &clusterStateChangedNotifier,
              IBucketStateChangedNotifier &bucketStateChangedNotifier,
              IDiskMemUsageNotifier &diskMemUsageNotifier,
              const BlockableMaintenanceJobConfig &blockableConfig,
              const vespalib::string &docTypeName,
              document::BucketSpace bucketSpace)
    : BlockableMaintenanceJob("move_buckets." + docTypeName, vespalib::duration::zero(), vespalib::duration::zero(), blockableConfig),
      IClusterStateChangedHandler(),
      IBucketFreezeListener(),
      bucketdb::IBucketCreateListener(),
      IBucketStateChangedHandler(),
      IDiskMemUsageListener(),
      _calc(calc),
      _moveHandler(moveHandler),
      _modifiedHandler(modifiedHandler),
      _ready(ready),
      _notReady(notReady),
      _mover(getLimiter(), _ready.meta_store()->getBucketDB()),
      _doneScan(false),
      _scanPos(),
      _scanPass(ScanPass::FIRST),
      _endPos(),
      _bucketSpace(bucketSpace),
      _delayedBuckets(),
      _delayedBucketsFrozen(),
      _frozenBuckets(frozenBuckets),
      _bucketCreateNotifier(bucketCreateNotifier),
      _delayedMover(getLimiter(), _ready.meta_store()->getBucketDB()),
      _clusterStateChangedNotifier(clusterStateChangedNotifier),
      _bucketStateChangedNotifier(bucketStateChangedNotifier),
      _diskMemUsageNotifier(diskMemUsageNotifier)
{
    if (blockedDueToClusterState(_calc)) {
        setBlocked(BlockedReason::CLUSTER_STATE);
    }

    _frozenBuckets.addListener(this);
    _bucketCreateNotifier.addListener(this);
    _clusterStateChangedNotifier.addClusterStateChangedHandler(this);
    _bucketStateChangedNotifier.addBucketStateChangedHandler(this);
    _diskMemUsageNotifier.addDiskMemUsageListener(this);
}

BucketMoveJob::~BucketMoveJob()
{
    _frozenBuckets.removeListener(this);
    _bucketCreateNotifier.removeListener(this);
    _clusterStateChangedNotifier.removeClusterStateChangedHandler(this);
    _bucketStateChangedNotifier.removeBucketStateChangedHandler(this);
    _diskMemUsageNotifier.removeDiskMemUsageListener(this);
}

void
BucketMoveJob::maybeCancelMover(DocumentBucketMover &mover)
{
    // Cancel bucket if moving in wrong direction
    if (!mover.bucketDone()) {
        bool ready = mover.getSource() == &_ready;
        Trinary shouldBeReady = _calc->shouldBeReady(document::Bucket(_bucketSpace, mover.getBucket()));
        if (isBlocked() ||
            (shouldBeReady == Trinary::Undefined) ||
            (ready == (shouldBeReady == Trinary::True)))
        {
            mover.cancel();
        }
    }
}

void
BucketMoveJob::maybeDelayMover(DocumentBucketMover &mover, BucketId bucket)
{
    // Delay bucket if being frozen.
    if (!mover.bucketDone() && bucket == mover.getBucket()) {
        mover.cancel();
        _delayedBucketsFrozen.insert(bucket);
        _delayedBuckets.erase(bucket);
    }
}

void
BucketMoveJob::notifyThawedBucket(const BucketId &bucket)
{
    if (_delayedBucketsFrozen.erase(bucket) != 0u) {
        _delayedBuckets.insert(bucket);
        considerRun();
    }
}

void
BucketMoveJob::deactivateBucket(BucketId bucket)
{
    _delayedBuckets.insert(bucket);
}

void
BucketMoveJob::activateBucket(BucketId bucket)
{
    bucketdb::Guard notReadyBdb(_notReady.meta_store()->getBucketDB().takeGuard());
    if (notReadyBdb->get(bucket).getDocumentCount() == 0) {
        return; // notready bucket already empty. This is the normal case.
    }
    _delayedBuckets.insert(bucket);
}

void
BucketMoveJob::notifyCreateBucket(const bucketdb::Guard &, const BucketId &bucket)
{
    _delayedBuckets.insert(bucket);
    considerRun();
}

void
BucketMoveJob::changedCalculator()
{
    if (done()) {
        _scanPos = ScanPosition();
        _endPos = ScanPosition();
    } else {
        _endPos = _scanPos;
    }
    _doneScan = false;
    _scanPass = ScanPass::FIRST;
    maybeCancelMover(_mover);
    maybeCancelMover(_delayedMover);
}

bool
BucketMoveJob::scanAndMove(size_t maxBucketsToScan, size_t maxDocsToMove)
{
    IFrozenBucketHandler::ExclusiveBucketGuard::UP bucketGuard;
    // Look for delayed bucket to be processed now
    while (!_delayedBuckets.empty() && _delayedMover.bucketDone()) {
        const BucketId bucket = *_delayedBuckets.begin();
        _delayedBuckets.erase(_delayedBuckets.begin());
        ScanIterator itr(_ready.meta_store()->getBucketDB().takeGuard(), bucket);
        if (itr.getBucket() == bucket) {
            checkBucket(bucket, itr, _delayedMover, bucketGuard);
        }
    }
    if (!_delayedMover.bucketDone()) {
        return moveDocuments(_delayedMover, maxDocsToMove, bucketGuard);
    }
    if (_mover.bucketDone()) {
        size_t bucketsScanned = 0;
        for (;;) {
            if (_mover.bucketDone()) {
                ScanResult res = scanBuckets(maxBucketsToScan - bucketsScanned, bucketGuard);
                bucketsScanned += res.first;
                if (res.second) {
                    if (_scanPass == ScanPass::FIRST &&
                        _endPos.validBucket()) {
                        _scanPos = ScanPosition();
                        _scanPass = ScanPass::SECOND;
                    } else {
                        _doneScan = true;
                        break;
                    }
                }
            }
            if (!_mover.bucketDone() || bucketsScanned >= maxBucketsToScan) {
                break;
            }
        }
    }
    if (!_mover.bucketDone()) {
        return moveDocuments(_mover, maxDocsToMove, bucketGuard);
    }
    return true;
}

bool
BucketMoveJob::run()
{
    if (isBlocked() || done()) {
        return true; // indicate work is done, since node state is bad
    }
    /// Returning false here will immediately post the job back on the executor. This will give a busy loop,
    /// but this is considered fine as it is very rare and it will be intermingled with multiple feed operations.
    if ( ! scanAndMove(200, 1) ) {
        return false;
    }

    if (isBlocked(BlockedReason::OUTSTANDING_OPS)) {
        return true;
    }
    return done();
}

void
BucketMoveJob::notifyClusterStateChanged(const std::shared_ptr<IBucketStateCalculator> &newCalc)
{
    // Called by master write thread
    _calc = newCalc;
    changedCalculator();
    if (blockedDueToClusterState(_calc)) {
        setBlocked(BlockedReason::CLUSTER_STATE);
    } else {
        unBlock(BlockedReason::CLUSTER_STATE);
    }
}

void
BucketMoveJob::notifyBucketStateChanged(const BucketId &bucketId, BucketInfo::ActiveState newState)
{
    // Called by master write thread
    if (newState == BucketInfo::NOT_ACTIVE) {
        deactivateBucket(bucketId);
    } else {
        activateBucket(bucketId);
    }
    if (!done()) {
        considerRun();
    }
}

void
BucketMoveJob::notifyDiskMemUsage(DiskMemUsageState state)
{
    // Called by master write thread
    internalNotifyDiskMemUsage(state);
}

} // namespace proton
