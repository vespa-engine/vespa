// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blockable_maintenance_job.h"
#include "documentbucketmover.h"
#include "i_disk_mem_usage_listener.h"
#include "ibucketstatechangedhandler.h"
#include "iclusterstatechangedhandler.h"
#include <vespa/searchcore/proton/bucketdb/bucketscaniterator.h>
#include <vespa/searchcore/proton/bucketdb/i_bucket_create_listener.h>

namespace storage::spi { struct BucketExecutor; }
namespace searchcorespi::index { struct IThreadService; }

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
 * It will first compute the set of buckets to be moved. Then N of these buckets will be iterated in parallel and
 * the documents scheduled for move. The movment will happen in 3 phases.
 * 1 - Collect meta info for documents. Must happend in master thread
 * 2 - Acquire bucket lock and fetch documents and very against meta data. This is done in BucketExecutor threads.
 * 3 - Actual movement is then done in master thread while still holding bucket lock. Once bucket has fully moved
 *     bucket modified notification is sent.
 */
class BucketMoveJobV2 : public BlockableMaintenanceJob,
                      public IClusterStateChangedHandler,
                      public bucketdb::IBucketCreateListener,
                      public IBucketStateChangedHandler,
                      public IDiskMemUsageListener
{
private:
    using BucketExecutor = storage::spi::BucketExecutor;
    using IDestructorCallback = vespalib::IDestructorCallback;
    using IDestructorCallbackSP = std::shared_ptr<IDestructorCallback>;
    using IThreadService = searchcorespi::index::IThreadService;
    using BucketId = document::BucketId;
    using ScanIterator = bucketdb::ScanIterator;
    using BucketSet = std::map<BucketId, bool>;
    using NeedResult = std::pair<bool, bool>;
    using ActiveState = storage::spi::BucketInfo::ActiveState;
    using BucketMover = bucketdb::BucketMover;
    using BucketMoverSP = std::shared_ptr<BucketMover>;
    using Movers = std::vector<std::shared_ptr<BucketMover>>;
    using MoveKey = BucketMover::MoveKey;
    using GuardedMoveOp = BucketMover::GuardedMoveOp;
    std::shared_ptr<IBucketStateCalculator>   _calc;
    IDocumentMoveHandler                     &_moveHandler;
    IBucketModifiedHandler                   &_modifiedHandler;
    IThreadService                           &_master;
    BucketExecutor                           &_bucketExecutor;
    const MaintenanceDocumentSubDB           &_ready;
    const MaintenanceDocumentSubDB           &_notReady;
    const document::BucketSpace               _bucketSpace;
    size_t                                    _iterateCount;
    Movers                                    _movers;
    BucketSet                                 _buckets2Move;
    std::atomic<bool>                         _stopped;
    std::atomic<size_t>                       _startedCount;
    std::atomic<size_t>                       _executedCount;

    bucketdb::IBucketCreateNotifier   &_bucketCreateNotifier;
    IClusterStateChangedNotifier      &_clusterStateChangedNotifier;
    IBucketStateChangedNotifier       &_bucketStateChangedNotifier;
    IDiskMemUsageNotifier             &_diskMemUsageNotifier;

    void startMove(BucketMoverSP mover, size_t maxDocsToMove);
    void prepareMove(BucketMoverSP mover, std::vector<MoveKey> keysToMove, IDestructorCallbackSP context);
    void completeMove(BucketMoverSP mover, std::vector<GuardedMoveOp> keys, IDestructorCallbackSP context);
    void considerBucket(const bucketdb::Guard & guard, BucketId bucket);
    NeedResult needMove(const ScanIterator &itr) const;
    BucketSet computeBuckets2Move();
    BucketMoverSP createMover(BucketId bucket, bool wantReady);
    BucketMoverSP greedyCreateMover();
    void backFillMovers();
    void cancelMovesForBucket(BucketId bucket);
    bool moveDocs(size_t maxDocsToMove);
public:
    BucketMoveJobV2(const std::shared_ptr<IBucketStateCalculator> &calc,
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
                    document::BucketSpace bucketSpace);

    ~BucketMoveJobV2() override;

    bool scanAndMove(size_t maxBuckets2Move, size_t maxDocsToMovePerBucket);
    bool done() const;
    void recompute();
    bool inSync() const;

    bool run() override;
    void notifyClusterStateChanged(const std::shared_ptr<IBucketStateCalculator> &newCalc) override;
    void notifyBucketStateChanged(const BucketId &bucketId, ActiveState newState) override;
    void notifyDiskMemUsage(DiskMemUsageState state) override;
    void notifyCreateBucket(const bucketdb::Guard & guard, const BucketId &bucket) override;
    void onStop() override;
};

} // namespace proton
