// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ibucketstatecalculator.h"
#include "ibucketmodifiedhandler.h"
#include "ifrozenbuckethandler.h"
#include "documentbucketmover.h"
#include "i_maintenance_job.h"
#include "iclusterstatechangedhandler.h"
#include "ibucketfreezelistener.h"
#include "ibucketstatechangedhandler.h"
#include "i_disk_mem_usage_listener.h"
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <set>

namespace proton
{


class IBucketStateChangedNotifier;
class IClusterStateChangedNotifier;
class IDiskMemUsageNotifier;

/**
 * Class used to control the moving of buckets between the ready and
 * not ready sub databases.
 */
class BucketMoveJob : public IMaintenanceJob,
                      public IClusterStateChangedHandler,
                      public IBucketFreezeListener,
                      public IBucketStateChangedHandler,
                      public IDiskMemUsageListener
{
public:
    struct ScanPosition
    {
        document::BucketId _lastBucket;

        ScanPosition() : _lastBucket() { }
        ScanPosition(document::BucketId lastBucket) : _lastBucket(lastBucket) { }
        bool validBucket() const { return _lastBucket.isSet(); }
    };

    typedef BucketDB::ConstMapIterator BucketIterator;

    class ScanIterator
    {
    private:
        BucketDBOwner::Guard _db;
        BucketIterator       _itr;
        BucketIterator       _end;

    public:
        ScanIterator(BucketDBOwner::Guard db,
                     uint32_t pass,
                     document::BucketId lastBucket,
                     document::BucketId endBucket);

        ScanIterator(BucketDBOwner::Guard db, document::BucketId bucket);

        ScanIterator(const ScanIterator &) = delete;
        ScanIterator(ScanIterator &&rhs);
        ScanIterator &operator=(const ScanIterator &) = delete;
        ScanIterator &operator=(ScanIterator &&rhs) = delete;

        bool                   valid() const { return _itr != _end; }
        bool                isActive() const { return _itr->second.isActive(); }
        document::BucketId getBucket() const { return _itr->first; }
        bool      hasReadyBucketDocs() const { return _itr->second.getReadyCount() != 0; }
        bool   hasNotReadyBucketDocs() const { return _itr->second.getNotReadyCount() != 0; }

        ScanIterator & operator++(void) {
            ++_itr;
            return *this;
        }
    };

private:
    typedef std::pair<size_t, bool> ScanResult;
    IBucketStateCalculator::SP         _calc;
    IDocumentMoveHandler              &_moveHandler;
    IBucketModifiedHandler            &_modifiedHandler;
    const MaintenanceDocumentSubDB    &_ready;
    const MaintenanceDocumentSubDB    &_notReady;
    DocumentBucketMover                _mover;
    bool                               _doneScan;
    ScanPosition                       _scanPos;
    uint32_t                           _scanPass;
    ScanPosition                       _endPos;

    typedef std::set<document::BucketId>    DelayedBucketSet;

    // Delayed buckets that are no longer frozen or active that can be considered for moving.
    DelayedBucketSet                   _delayedBuckets;
    // Frozen buckets that cannot be moved at all.
    DelayedBucketSet                   _delayedBucketsFrozen;
    IFrozenBucketHandler              &_frozenBuckets;
    DocumentBucketMover                _delayedMover;
    IMaintenanceJobRunner             *_runner;
    bool                               _clusterUp;
    bool                               _nodeUp;
    bool                               _nodeInitializing;
    bool                               _resourcesOK;
    bool                               _runnable;  // can try to perform work
    IClusterStateChangedNotifier      &_clusterStateChangedNotifier;
    IBucketStateChangedNotifier       &_bucketStateChangedNotifier;
    IDiskMemUsageNotifier             &_diskMemUsageNotifier;
    double                             _resourceLimitFactor;

    ScanResult
    scanBuckets(size_t maxBucketsToScan,
                IFrozenBucketHandler::ExclusiveBucketGuard::UP & bucketGuard);

    void maybeCancelMover(DocumentBucketMover &mover);
    void maybeDelayMover(DocumentBucketMover &mover, document::BucketId bucket);

    void
    moveDocuments(DocumentBucketMover &mover,
                  size_t maxDocsToMove,
                  IFrozenBucketHandler::ExclusiveBucketGuard::UP & bucketGuard);

    void
    checkBucket(const document::BucketId &bucket,
                ScanIterator &itr,
                DocumentBucketMover &mover,
                IFrozenBucketHandler::ExclusiveBucketGuard::UP & bucketGuard);

    void refreshRunnable();
    void refreshDerivedClusterState();

    /**
     * Signal that the given bucket should be de-activated.
     * An active bucket is not considered for moving from ready to not ready sub database.
     * A de-activated bucket can be considered for moving.
     **/
    void deactivateBucket(document::BucketId bucket);

    /**
     * Signal that the given bucket should be activated.
     */
    void activateBucket(document::BucketId bucket);

public:
    BucketMoveJob(const IBucketStateCalculator::SP &calc,
                  IDocumentMoveHandler &moveHandler,
                  IBucketModifiedHandler &modifiedHandler,
                  const MaintenanceDocumentSubDB &ready,
                  const MaintenanceDocumentSubDB &notReady,
                  IFrozenBucketHandler &frozenBuckets,
                  IClusterStateChangedNotifier &clusterStateChangedNotifier,
                  IBucketStateChangedNotifier &bucketStateChangedNotifier,
                  IDiskMemUsageNotifier &diskMemUsageNotifier,
                  double resourceLimitFactor,
                  const vespalib::string &docTypeName);

    virtual ~BucketMoveJob();

    void changedCalculator();
    void scanAndMove(size_t maxBucketsToScan, size_t maxDocsToMove);

    bool done() const {
        // Ignores _delayedBucketsFrozen, since no work can be done there yet
        return
            _doneScan &&
            _mover.bucketDone() &&
            _delayedMover.bucketDone() &&
            _delayedBuckets.empty();
    }

    // IMaintenanceJob API
    virtual void registerRunner(IMaintenanceJobRunner *runner) override;

    // IMaintenanceJob API
    virtual bool run() override;

    // IClusterStateChangedHandler API
    virtual void notifyClusterStateChanged(const IBucketStateCalculator::SP &newCalc) override;


    // IBucketFreezeListener API
    /**
     * Signal that the given bucket has been thawed.
     * A thawed bucket can be considered for moving.
     */
    virtual void notifyThawedBucket(const document::BucketId &bucket) override;

    // IBucketStateChangedHandler API
    void notifyBucketStateChanged(const document::BucketId &bucketId,
                             storage::spi::BucketInfo::ActiveState newState) override;

    virtual void notifyDiskMemUsage(DiskMemUsageState state) override;
};

} // namespace proton

