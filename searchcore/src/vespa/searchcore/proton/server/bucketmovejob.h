// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blockable_maintenance_job.h"
#include "documentbucketmover.h"
#include "i_disk_mem_usage_listener.h"
#include "ibucketfreezelistener.h"
#include "ibucketstatechangedhandler.h"
#include "iclusterstatechangedhandler.h"
#include "ifrozenbuckethandler.h"
#include <vespa/searchcore/proton/bucketdb/bucketscaniterator.h>
#include <vespa/searchcore/proton/bucketdb/i_bucket_create_listener.h>
#include <set>

namespace proton {

class BlockableMaintenanceJobConfig;
class IBucketStateChangedNotifier;
class IClusterStateChangedNotifier;
class IDiskMemUsageNotifier;
class IBucketModifiedHandler;

namespace bucketdb { class IBucketCreateNotifier; }

/**
 * Class used to control the moving of buckets between the ready and
 * not ready sub databases based on the readiness of buckets according to the cluster state.
 */
class BucketMoveJob : public BlockableMaintenanceJob,
                      public IClusterStateChangedHandler,
                      public IBucketFreezeListener,
                      public bucketdb::IBucketCreateListener,
                      public IBucketStateChangedHandler,
                      public IDiskMemUsageListener
{
private:
    using ScanPosition = bucketdb::ScanPosition;
    using ScanIterator = bucketdb::ScanIterator;
    using ScanPass = ScanIterator::Pass;
    using ScanResult = std::pair<size_t, bool>;
    std::shared_ptr<IBucketStateCalculator>   _calc;
    IDocumentMoveHandler                     &_moveHandler;
    IBucketModifiedHandler                   &_modifiedHandler;
    const MaintenanceDocumentSubDB           &_ready;
    const MaintenanceDocumentSubDB           &_notReady;
    DocumentBucketMover                       _mover;
    bool                                      _doneScan;
    ScanPosition                              _scanPos;
    ScanPass                                  _scanPass;
    ScanPosition                              _endPos;
    document::BucketSpace                    _bucketSpace;

    using DelayedBucketSet = std::set<document::BucketId>;

    // Delayed buckets that are no longer frozen or active that can be considered for moving.
    DelayedBucketSet                   _delayedBuckets;
    // Frozen buckets that cannot be moved at all.
    DelayedBucketSet                   _delayedBucketsFrozen;
    IFrozenBucketHandler              &_frozenBuckets;
    bucketdb::IBucketCreateNotifier   &_bucketCreateNotifier;
    DocumentBucketMover                _delayedMover;
    IClusterStateChangedNotifier      &_clusterStateChangedNotifier;
    IBucketStateChangedNotifier       &_bucketStateChangedNotifier;
    IDiskMemUsageNotifier             &_diskMemUsageNotifier;

    ScanResult
    scanBuckets(size_t maxBucketsToScan,
                IFrozenBucketHandler::ExclusiveBucketGuard::UP & bucketGuard);

    void maybeCancelMover(DocumentBucketMover &mover);
    void maybeDelayMover(DocumentBucketMover &mover, document::BucketId bucket);

    bool
    moveDocuments(DocumentBucketMover &mover,
                  size_t maxDocsToMove,
                  IFrozenBucketHandler::ExclusiveBucketGuard::UP & bucketGuard);

    void
    checkBucket(const document::BucketId &bucket,
                ScanIterator &itr,
                DocumentBucketMover &mover,
                IFrozenBucketHandler::ExclusiveBucketGuard::UP & bucketGuard);

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
                  document::BucketSpace bucketSpace);

    ~BucketMoveJob() override;

    void changedCalculator();
    bool scanAndMove(size_t maxBucketsToScan, size_t maxDocsToMove);

    bool done() const {
        // Ignores _delayedBucketsFrozen, since no work can be done there yet
        return
            _doneScan &&
            _mover.bucketDone() &&
            _delayedMover.bucketDone() &&
            _delayedBuckets.empty();
    }

    // IMaintenanceJob API
    bool run() override;

    // IClusterStateChangedHandler API
    void notifyClusterStateChanged(const std::shared_ptr<IBucketStateCalculator> &newCalc) override;

    // IBucketFreezeListener API
    void notifyThawedBucket(const document::BucketId &bucket) override;

    // IBucketStateChangedHandler API
    void notifyBucketStateChanged(const document::BucketId &bucketId,
                                  storage::spi::BucketInfo::ActiveState newState) override;

    void notifyDiskMemUsage(DiskMemUsageState state) override;

    // bucketdb::IBucketCreateListener API
    void notifyCreateBucket(const bucketdb::Guard & guard, const document::BucketId &bucket) override;
};

} // namespace proton
