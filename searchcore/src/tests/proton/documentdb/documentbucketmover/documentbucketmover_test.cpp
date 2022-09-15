// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmover_common.h"
#include <vespa/searchcore/proton/server/bucketmovejob.h>
#include <vespa/searchcore/proton/server/executor_thread_service.h>
#include <vespa/searchcore/proton/server/document_db_maintenance_config.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/persistence/dummyimpl/dummy_bucket_executor.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
#include <vespa/searchcore/proton/metrics/documentdb_tagged_metrics.h>

LOG_SETUP("document_bucket_mover_test");

using namespace proton;
using namespace proton::move::test;
using document::BucketId;
using document::test::makeBucketSpace;
using proton::bucketdb::BucketCreateNotifier;
using storage::spi::BucketInfo;
using BlockedReason = IBlockableMaintenanceJob::BlockedReason;
using MoveOperationVector = std::vector<MoveOperation>;
using storage::spi::dummy::DummyBucketExecutor;
using vespalib::MonitoredRefCount;
using vespalib::RetainGuard;
using vespalib::ThreadStackExecutor;

struct ControllerFixtureBase : public ::testing::Test
{
    test::UserDocumentsBuilder  _builder;
    test::BucketStateCalculator::SP _calc;
    test::ClusterStateHandler   _clusterStateHandler;
    test::BucketHandler         _bucketHandler;
    MyBucketModifiedHandler     _modifiedHandler;
    std::shared_ptr<bucketdb::BucketDBOwner> _bucketDB;
    MySubDb                         _ready;
    MySubDb                         _notReady;
    BucketCreateNotifier            _bucketCreateNotifier;
    test::DiskMemUsageNotifier      _diskMemUsageNotifier;
    MonitoredRefCount               _refCount;
    ThreadStackExecutor             _singleExecutor;
    SyncableExecutorThreadService   _master;
    DummyBucketExecutor             _bucketExecutor;
    MyMoveHandler                   _moveHandler;
    DocumentDBTaggedMetrics         _metrics;
    std::shared_ptr<BucketMoveJob>  _bmj;
    MyCountJobRunner                _runner;
    ControllerFixtureBase(const BlockableMaintenanceJobConfig &blockableConfig, bool storeMoveDoneContexts);
    ~ControllerFixtureBase() override;
    ControllerFixtureBase &addReady(const BucketId &bucket) {
        _calc->addReady(bucket);
        return *this;
    }
    ControllerFixtureBase &remReady(const BucketId &bucket) {
        _calc->remReady(bucket);
        return *this;
    }
    ControllerFixtureBase &changeCalc() {
        _calc->resetAsked();
        _moveHandler.reset();
        _modifiedHandler.reset();
        _clusterStateHandler.notifyClusterStateChanged(_calc);
        return *this;
    }
    ControllerFixtureBase &activateBucket(const BucketId &bucket) {
        _ready.setBucketState(bucket, true);
        _bucketHandler.notifyBucketStateChanged(bucket, BucketInfo::ActiveState::ACTIVE);
        return *this;
    }
    ControllerFixtureBase &deactivateBucket(const BucketId &bucket) {
        _ready.setBucketState(bucket, false);
        _bucketHandler.notifyBucketStateChanged(bucket, BucketInfo::ActiveState::NOT_ACTIVE);
        return *this;
    }
    void failRetrieveForLid(uint32_t lid) {
        _ready.failRetrieveForLid(lid);
        _notReady.failRetrieveForLid(lid);
    }
    void fixRetriever() {
        _ready.failRetrieveForLid(0);
        _notReady.failRetrieveForLid(0);
    }
    const MoveOperationVector &docsMoved() const {
        return _moveHandler._moves;
    }
    const std::vector<BucketId> &bucketsModified() const {
        return _modifiedHandler._modified;
    }
    const BucketId::List &calcAsked() const {
        return _calc->asked();
    }
    size_t numPending() {
        _bmj->updateMetrics(_metrics);
        return _metrics.bucketMove.bucketsPending.getLast();
    }
    void runLoop() {
        while (!_bmj->isBlocked() && !_bmj->run()) {
        }
    }
    void sync() {
        _bucketExecutor.sync();
        _master.sync();
        _master.sync(); // Handle that  master schedules onto master again
    }
    template <typename FunctionType>
    void masterExecute(FunctionType &&function) {
        _master.execute(vespalib::makeLambdaTask(std::forward<FunctionType>(function)));
        _master.sync();
    }
};

ControllerFixtureBase::ControllerFixtureBase(const BlockableMaintenanceJobConfig &blockableConfig, bool storeMoveDoneContexts)
    : _builder(),
      _calc(std::make_shared<test::BucketStateCalculator>()),
      _bucketHandler(),
      _modifiedHandler(),
      _bucketDB(std::make_shared<bucketdb::BucketDBOwner>()),
      _ready(_builder.getRepo(), _bucketDB, 1, SubDbType::READY),
      _notReady(_builder.getRepo(), _bucketDB, 2, SubDbType::NOTREADY),
      _bucketCreateNotifier(),
      _diskMemUsageNotifier(),
      _refCount(),
      _singleExecutor(1, 0x10000),
      _master(_singleExecutor),
      _bucketExecutor(4),
      _moveHandler(*_bucketDB, storeMoveDoneContexts),
      _metrics("test", 1),
      _bmj(BucketMoveJob::create(_calc, RetainGuard(_refCount), _moveHandler, _modifiedHandler, _master, _bucketExecutor, _ready._subDb,
                                 _notReady._subDb, _bucketCreateNotifier, _clusterStateHandler, _bucketHandler,
                                 _diskMemUsageNotifier, blockableConfig, "test", makeBucketSpace())),
      _runner(*_bmj)
{
}

ControllerFixtureBase::~ControllerFixtureBase() = default;
constexpr double RESOURCE_LIMIT_FACTOR = 1.0;
constexpr uint32_t MAX_OUTSTANDING_OPS = 10;
const BlockableMaintenanceJobConfig BLOCKABLE_CONFIG(RESOURCE_LIMIT_FACTOR, MAX_OUTSTANDING_OPS);

struct ControllerFixture : public ControllerFixtureBase
{
    explicit ControllerFixture(const BlockableMaintenanceJobConfig &blockableConfig = BLOCKABLE_CONFIG)
        : ControllerFixtureBase(blockableConfig, blockableConfig.getMaxOutstandingMoveOps() != MAX_OUTSTANDING_OPS)
    {
        _builder.createDocs(1, 1, 4); // 3 docs
        _builder.createDocs(2, 4, 6); // 2 docs
        _ready.insertDocs(_builder.getDocs());
        _builder.clearDocs();
        _builder.createDocs(3, 1, 3); // 2 docs
        _builder.createDocs(4, 3, 6); // 3 docs
        _notReady.insertDocs(_builder.getDocs());
    }
};

struct OnlyReadyControllerFixture : public ControllerFixtureBase
{
    OnlyReadyControllerFixture() : ControllerFixtureBase(BLOCKABLE_CONFIG, false)
    {
        _builder.createDocs(1, 1, 2); // 1 docs
        _builder.createDocs(2, 2, 4); // 2 docs
        _builder.createDocs(3, 4, 7); // 3 docs
        _builder.createDocs(4, 7, 11); // 4 docs
        _ready.insertDocs(_builder.getDocs());
    }
};

TEST_F(ControllerFixture, require_that_nothing_is_moved_if_bucket_state_says_so)
{
    EXPECT_TRUE(_bmj->done());
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    _bmj->recompute();
    masterExecute([this]() {
        EXPECT_TRUE(_bmj->scanAndMove(4, 3));
        EXPECT_TRUE(_bmj->done());
    });
    EXPECT_TRUE(docsMoved().empty());
    EXPECT_TRUE(bucketsModified().empty());
}

TEST_F(ControllerFixture, require_that_not_ready_bucket_is_moved_to_ready_if_bucket_state_says_so)
{
    // bucket 4 should be moved
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    addReady(_notReady.bucket(4));

    EXPECT_EQ(0, numPending());
    _bmj->recompute();
    EXPECT_EQ(1, numPending());
    masterExecute([this]() {
        EXPECT_FALSE(_bmj->done());
        EXPECT_TRUE(_bmj->scanAndMove(4, 3));
        EXPECT_TRUE(_bmj->done());
    });
    sync();
    EXPECT_EQ(0, numPending());
    EXPECT_EQ(3u, docsMoved().size());
    assertEqual(_notReady.bucket(4), _notReady.docs(4)[0], 2, 1, docsMoved()[0]);
    assertEqual(_notReady.bucket(4), _notReady.docs(4)[1], 2, 1, docsMoved()[1]);
    assertEqual(_notReady.bucket(4), _notReady.docs(4)[2], 2, 1, docsMoved()[2]);
    ASSERT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_notReady.bucket(4), bucketsModified()[0]);
}

TEST_F(ControllerFixture, require_that_ready_bucket_is_moved_to_not_ready_if_bucket_state_says_so)
{
    // bucket 2 should be moved
    addReady(_ready.bucket(1));
    _bmj->recompute();
    masterExecute([this]() {
        EXPECT_FALSE(_bmj->done());
        EXPECT_TRUE(_bmj->scanAndMove(4, 3));
        EXPECT_TRUE(_bmj->done());
    });
    sync();
    EXPECT_EQ(2u, docsMoved().size());
    assertEqual(_ready.bucket(2), _ready.docs(2)[0], 1, 2, docsMoved()[0]);
    assertEqual(_ready.bucket(2), _ready.docs(2)[1], 1, 2, docsMoved()[1]);
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(2), bucketsModified()[0]);
}

TEST_F(ControllerFixture, require_that_bucket_is_moved_even_with_error)
{
    // bucket 2 should be moved
    addReady(_ready.bucket(1));
    _bmj->recompute();
    failRetrieveForLid(5);
    masterExecute([this]() {
        EXPECT_FALSE(_bmj->done());
        EXPECT_TRUE(_bmj->scanAndMove(4, 3));
        EXPECT_TRUE(_bmj->done());
    });
    sync();
    EXPECT_FALSE(_bmj->done());
    fixRetriever();
    masterExecute([this]() {
        EXPECT_TRUE(_bmj->scanAndMove(4, 3));
        EXPECT_TRUE(_bmj->done());
    });
    sync();
    EXPECT_EQ(2u, docsMoved().size());
    assertEqual(_ready.bucket(2), _ready.docs(2)[0], 1, 2, docsMoved()[0]);
    assertEqual(_ready.bucket(2), _ready.docs(2)[1], 1, 2, docsMoved()[1]);
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(2), bucketsModified()[0]);
}


TEST_F(ControllerFixture, require_that_we_move_buckets_in_several_steps)
{
    // bucket 2, 3, and 4 should be moved
    addReady(_ready.bucket(1));
    addReady(_notReady.bucket(3));
    addReady(_notReady.bucket(4));

    _bmj->recompute();
    EXPECT_EQ(3, numPending());
    masterExecute([this]() {
        EXPECT_FALSE(_bmj->done());

        EXPECT_FALSE(_bmj->scanAndMove(1, 2));
        EXPECT_FALSE(_bmj->done());
    });
    sync();
    EXPECT_EQ(2, numPending());
    EXPECT_EQ(2u, docsMoved().size());

    masterExecute([this]() {
        EXPECT_FALSE(_bmj->scanAndMove(1, 2));
        EXPECT_FALSE(_bmj->done());
    });
    sync();
    EXPECT_EQ(2, numPending());
    EXPECT_EQ(4u, docsMoved().size());

    masterExecute([this]() {
        EXPECT_FALSE(_bmj->scanAndMove(1, 2));
        EXPECT_FALSE(_bmj->done());
    });
    sync();
    EXPECT_EQ(1, numPending());
    EXPECT_EQ(6u, docsMoved().size());

    // move bucket 4, docs 3
    masterExecute([this]() {
        EXPECT_TRUE(_bmj->scanAndMove(1, 2));
        EXPECT_TRUE(_bmj->done());
    });
    sync();
    EXPECT_EQ(0, numPending());
    EXPECT_EQ(7u, docsMoved().size());
    EXPECT_EQ(3u, bucketsModified().size());
    ASSERT_EQ(_ready.bucket(2), bucketsModified()[0]);
    EXPECT_EQ(_notReady.bucket(3), bucketsModified()[1]);
    EXPECT_EQ(_notReady.bucket(4), bucketsModified()[2]);
}

TEST_F(ControllerFixture, require_that_last_bucket_is_moved_before_reporting_done)
{
    // bucket 4 should be moved
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    addReady(_notReady.bucket(4));
    _bmj->recompute();
    masterExecute([this]() {
        EXPECT_FALSE(_bmj->done());

        EXPECT_FALSE(_bmj->scanAndMove(1, 1));
        EXPECT_FALSE(_bmj->done());
    });
    sync();
    EXPECT_EQ(1u, docsMoved().size());
    EXPECT_EQ(4u, calcAsked().size());
    masterExecute([this]() {
        EXPECT_TRUE(_bmj->scanAndMove(1, 2));
        EXPECT_TRUE(_bmj->done());
    });
    sync();
    EXPECT_EQ(3u, docsMoved().size());
    EXPECT_EQ(4u, calcAsked().size());
}


TEST_F(ControllerFixture, require_that_active_bucket_is_not_moved_from_ready_to_not_ready_until_being_not_active)
{
    // bucket 1 should be moved but is active
    addReady(_ready.bucket(2));
    _bmj->recompute();
    EXPECT_FALSE(_bmj->done());
    activateBucket(_ready.bucket(1));
    masterExecute([this]() {
        EXPECT_TRUE(_bmj->scanAndMove(4, 3)); // scan all, delay active bucket 1
        EXPECT_TRUE(_bmj->done());
    });
    sync();
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());

    deactivateBucket(_ready.bucket(1));
    masterExecute([this]() {
        EXPECT_FALSE(_bmj->done());
        EXPECT_TRUE(_bmj->scanAndMove(4, 3)); // move delayed and de-activated bucket 1
        EXPECT_TRUE(_bmj->done());
    });
    sync();
    EXPECT_EQ(3u, docsMoved().size());
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(1), bucketsModified()[0]);
}

TEST_F(ControllerFixture, require_that_current_bucket_moving_is_cancelled_when_we_change_calculator)
{
    // bucket 1 should be moved
    addReady(_ready.bucket(2));

    masterExecute([this]() {
        _bmj->recompute();
        _bmj->scanAndMove(1, 1);
        EXPECT_FALSE(_bmj->done());
    });
    sync();
    EXPECT_EQ(1u, docsMoved().size());
    EXPECT_EQ(4u, calcAsked().size());
    masterExecute([this]() {
        changeCalc(); // Not cancelled, bucket 1 still moving to notReady
        EXPECT_EQ(4u, calcAsked().size());
        EXPECT_EQ(_ready.bucket(1), calcAsked()[0]);
        _calc->resetAsked();
        _bmj->scanAndMove(1, 1);
        EXPECT_FALSE(_bmj->done());
    });
    sync();
    EXPECT_EQ(1u, docsMoved().size());
    EXPECT_EQ(0u, calcAsked().size());
    addReady(_ready.bucket(1));
    masterExecute([this]() {
        changeCalc(); // cancelled, bucket 1 no longer moving to notReady
        EXPECT_EQ(4u, calcAsked().size());
        EXPECT_EQ(_ready.bucket(1), calcAsked()[0]);
        _calc->resetAsked();
        remReady(_ready.bucket(1));
        _calc->resetAsked();
        changeCalc(); // not cancelled.  No active bucket move
        EXPECT_EQ(4u, calcAsked().size());
        _bmj->scanAndMove(1, 1);
    });
    sync();
    EXPECT_EQ(1u, docsMoved().size());
    EXPECT_EQ(4u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(2), calcAsked()[1]);
    EXPECT_EQ(_notReady.bucket(3), calcAsked()[2]);
    masterExecute([this]() {
        _bmj->scanAndMove(2, 3);
    });
    EXPECT_TRUE(_bmj->done());
    sync();
    EXPECT_EQ(3u, docsMoved().size());
    EXPECT_EQ(4u, calcAsked().size());
    EXPECT_EQ(_notReady.bucket(4), calcAsked()[3]);
    EXPECT_EQ(_ready.bucket(1), calcAsked()[0]);
}

TEST_F(ControllerFixture, require_that_de_activated_bucket_is_not_moved_if_new_calculator_does_not_say_so)
{
    // bucket 1 should be moved
    addReady(_ready.bucket(2));
    _bmj->recompute();
    masterExecute([this]() {
        activateBucket(_ready.bucket(1));
        _bmj->scanAndMove(4, 3); // scan all, delay active bucket 1
    });
    sync();
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());

    masterExecute([this]() {
        deactivateBucket(_ready.bucket(1));
        addReady(_ready.bucket(1));
        changeCalc();
        _bmj->scanAndMove(4, 3); // consider delayed bucket 3
    });
    sync();
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());
    EXPECT_EQ(4u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(1), calcAsked()[0]);
}

TEST_F(ControllerFixture, ready_bucket_not_moved_to_not_ready_if_node_is_marked_as_retired)
{
    // Bucket 2 would be moved from ready to not ready in a non-retired case, but not when retired.
    remReady(_ready.bucket(1));
    _calc->setNodeRetired(true);
    masterExecute([this]() {
        _bmj->recompute();
        _bmj->scanAndMove(4, 3);
        EXPECT_TRUE(_bmj->done());
    });
    sync();
    EXPECT_EQ(0u, docsMoved().size());
}

// Technically this should never happen since a retired node is never in the ideal state,
// but test this case for the sake of completion.
TEST_F(ControllerFixture, inactive_not_ready_bucket_not_moved_to_ready_if_node_is_marked_as_retired)
{
    remReady(_ready.bucket(1));
    remReady(_ready.bucket(2));
    remReady(_notReady.bucket(3));
    _calc->setNodeRetired(true);
    masterExecute([this]() {
        _bmj->recompute();
        _bmj->scanAndMove(4, 3);
        EXPECT_TRUE(_bmj->done());
    });
    sync();
    EXPECT_EQ(0u, docsMoved().size());
}

TEST_F(ControllerFixture, explicitly_active_not_ready_bucket_can_not_be_moved_to_ready_if_node_is_marked_as_retired)
{
    remReady(_ready.bucket(1));
    remReady(_ready.bucket(2));
    remReady(_notReady.bucket(3));
    _calc->setNodeRetired(true);
    _bmj->recompute();
    masterExecute([this]() {
        activateBucket(_notReady.bucket(3));
        _bmj->scanAndMove(4, 3);
        EXPECT_TRUE(_bmj->done());
    });
    sync();
    ASSERT_EQ(0u, docsMoved().size());
    ASSERT_EQ(0u, bucketsModified().size());
}

TEST_F(ControllerFixture, explicitly_active_not_ready_bucket_can_not_be_moved_to_ready)
{
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    remReady(_notReady.bucket(3));
    _bmj->recompute();
    masterExecute([this]() {
        activateBucket(_notReady.bucket(3));
        _bmj->scanAndMove(4, 3);
        EXPECT_TRUE(_bmj->done());
    });
    sync();
    ASSERT_EQ(0u, docsMoved().size());
    ASSERT_EQ(0u, bucketsModified().size());
}

TEST_F(ControllerFixture, bucket_change_notification_is_not_lost_with_concurrent_bucket_movers)
{
    addReady(_ready.bucket(1));
    _bmj->recompute(); // Bucket 1 should be (and is) ready, bucket 2 is ready (but should not be).
    _bucketExecutor.defer_new_tasks(); // Don't execute immediately, we need to force multiple pending moves
    masterExecute([this]() {
        deactivateBucket(_ready.bucket(2));
        _bmj->scanAndMove(4, 3);
        // New deactivation received from above prior to completion of scan. This can happen since
        // moves are asynchronous and the distributor can send new (de-)activations before the old move is done.
        // In our case, we've enforced that another move is already pending in the bucket executor.
        deactivateBucket(_ready.bucket(2));
        _bmj->scanAndMove(4, 3);
    });
    sync();
    ASSERT_EQ(_bucketExecutor.num_deferred_tasks(), 2u);
    _bucketExecutor.schedule_single_deferred_task();
    sync();
    // We have to fake that moving a document marks it as not found in the source sub DB.
    // This doesn't automatically happen when using mocks. The most important part is that
    // we ensure that moving isn't erroneously tested as if it were idempotent.
    for (const auto& move : docsMoved()) {
        failRetrieveForLid(move.getPrevLid());
    }
    _bucketExecutor.schedule_single_deferred_task();
    sync();
    EXPECT_TRUE(_bmj->done());
    ASSERT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(2), bucketsModified()[0]);
}

TEST_F(ControllerFixture, require_that_notifyCreateBucket_causes_bucket_to_be_reconsidered_by_job)
{
    EXPECT_TRUE(_bmj->done());
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    runLoop();
    EXPECT_TRUE(_bmj->done());
    sync();
    EXPECT_TRUE(docsMoved().empty());
    EXPECT_TRUE(bucketsModified().empty());
    addReady(_notReady.bucket(3)); // bucket 3 now ready, no notify
    EXPECT_TRUE(_bmj->done());        // move job still believes work done
    sync();
    EXPECT_TRUE(bucketsModified().empty());
    masterExecute([this]() {
        _bmj->notifyCreateBucket(_bucketDB->takeGuard(), _notReady.bucket(3)); // reconsider bucket 3
        EXPECT_FALSE(_bmj->done());
        EXPECT_TRUE(bucketsModified().empty());
    });
    sync();
    EXPECT_TRUE(bucketsModified().empty());
    runLoop();
    EXPECT_TRUE(_bmj->done());
    sync();

    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(2u, docsMoved().size());
}


struct ResourceLimitControllerFixture : public ControllerFixture
{
    explicit ResourceLimitControllerFixture(double resourceLimitFactor = RESOURCE_LIMIT_FACTOR) :
        ControllerFixture(BlockableMaintenanceJobConfig(resourceLimitFactor, MAX_OUTSTANDING_OPS))
    {}

    void testJobStopping(DiskMemUsageState blockingUsageState) {
        // Bucket 1 should be moved
        addReady(_ready.bucket(2));
        _bmj->recompute();
        EXPECT_FALSE(_bmj->done());
        // Note: This depends on _bmj->run() moving max 1 documents
        EXPECT_FALSE(_bmj->run());
        sync();
        EXPECT_EQ(1u, docsMoved().size());
        EXPECT_EQ(0u, bucketsModified().size());
        // Notify that we've over limit
        _diskMemUsageNotifier.notify(blockingUsageState);
        EXPECT_TRUE(_bmj->run());
        sync();
        EXPECT_EQ(1u, docsMoved().size());
        EXPECT_EQ(0u, bucketsModified().size());
        // Notify that we've under limit
        _diskMemUsageNotifier.notify(DiskMemUsageState());
        EXPECT_FALSE(_bmj->run());
        sync();
        EXPECT_EQ(2u, docsMoved().size());
        EXPECT_EQ(0u, bucketsModified().size());
    }

    void testJobNotStopping(DiskMemUsageState blockingUsageState) {
        // Bucket 1 should be moved
        addReady(_ready.bucket(2));
        _bmj->recompute();
        EXPECT_FALSE(_bmj->done());
        // Note: This depends on _bmj->run() moving max 1 documents
        EXPECT_FALSE(_bmj->run());
        sync();
        EXPECT_EQ(1u, docsMoved().size());
        EXPECT_EQ(0u, bucketsModified().size());
        // Notify that we've over limit, but not over adjusted limit
        _diskMemUsageNotifier.notify(blockingUsageState);
        EXPECT_FALSE(_bmj->run());
        sync();
        EXPECT_EQ(2u, docsMoved().size());
        EXPECT_EQ(0u, bucketsModified().size());
    }
};

struct ResourceLimitControllerFixture_1_2 : public ResourceLimitControllerFixture {
    ResourceLimitControllerFixture_1_2() : ResourceLimitControllerFixture(1.2) {}
};

TEST_F(ResourceLimitControllerFixture, require_that_bucket_move_stops_when_disk_limit_is_reached)
{
    testJobStopping(DiskMemUsageState(ResourceUsageState(0.7, 0.8), ResourceUsageState()));
}

TEST_F(ResourceLimitControllerFixture, require_that_bucket_move_stops_when_memory_limit_is_reached)
{
    testJobStopping(DiskMemUsageState(ResourceUsageState(), ResourceUsageState(0.7, 0.8)));
}

TEST_F(ResourceLimitControllerFixture_1_2, require_that_bucket_move_uses_resource_limit_factor_for_disk_resource_limit)
{
    testJobNotStopping(DiskMemUsageState(ResourceUsageState(0.7, 0.8), ResourceUsageState()));
}

TEST_F(ResourceLimitControllerFixture_1_2, require_that_bucket_move_uses_resource_limit_factor_for_memory_resource_limit)
{
    testJobNotStopping(DiskMemUsageState(ResourceUsageState(), ResourceUsageState(0.7, 0.8)));
}


struct MaxOutstandingMoveOpsFixture : public ControllerFixtureBase
{
    explicit MaxOutstandingMoveOpsFixture(uint32_t maxOutstandingOps)
        : ControllerFixtureBase(BlockableMaintenanceJobConfig(RESOURCE_LIMIT_FACTOR, maxOutstandingOps), true)
    {
        _builder.createDocs(1, 1, 2);
        _builder.createDocs(2, 2, 3);
        _builder.createDocs(3, 3, 4);
        _builder.createDocs(4, 4, 5);
        _ready.insertDocs(_builder.getDocs());
        _builder.clearDocs();
        _builder.createDocs(11, 1, 2);
        _builder.createDocs(12, 2, 3);
        _builder.createDocs(13, 3, 4);
        _builder.createDocs(14, 4, 5);
        _notReady.insertDocs(_builder.getDocs());
        addReady(_ready.bucket(3));
        _bmj->recompute();
    }

    void assertRunToBlocked() {
        EXPECT_TRUE(_bmj->run()); // job becomes blocked as max outstanding limit is reached
        EXPECT_FALSE(_bmj->done());
        EXPECT_TRUE(_bmj->isBlocked());
        EXPECT_TRUE(_bmj->isBlocked(BlockedReason::OUTSTANDING_OPS));
    }
    void assertRunToNotBlocked() {
        EXPECT_FALSE(_bmj->run());
        EXPECT_FALSE(_bmj->done());
        EXPECT_FALSE(_bmj->isBlocked());
    }
    void assertRunToFinished() {
        EXPECT_TRUE(_bmj->run());
        EXPECT_TRUE(_bmj->done());
        EXPECT_FALSE(_bmj->isBlocked());
    }
    void assertDocsMoved(uint32_t expDocsMovedCnt, uint32_t expMoveContextsCnt) {
        EXPECT_EQ(expDocsMovedCnt, docsMoved().size());
        EXPECT_EQ(expMoveContextsCnt, _moveHandler._moveDoneContexts.size());
    }
    void unblockJob(uint32_t expRunnerCnt) {
        _moveHandler.clearMoveDoneContexts(); // unblocks job and try to execute it via runner
        EXPECT_EQ(expRunnerCnt, _runner.runCount);
        EXPECT_FALSE(_bmj->isBlocked());
    }
};

struct MaxOutstandingMoveOpsFixture_1 : public MaxOutstandingMoveOpsFixture {
    MaxOutstandingMoveOpsFixture_1() : MaxOutstandingMoveOpsFixture(1) {}
};

struct MaxOutstandingMoveOpsFixture_2 : public MaxOutstandingMoveOpsFixture {
    MaxOutstandingMoveOpsFixture_2() : MaxOutstandingMoveOpsFixture(2) {}
};

TEST_F(MaxOutstandingMoveOpsFixture_1, require_that_bucket_move_job_is_blocked_if_it_has_too_many_outstanding_move_operations_max_1)
{
    assertRunToBlocked();
    sync();
    assertDocsMoved(1, 1);
    assertRunToBlocked();
    assertDocsMoved(1, 1);

    unblockJob(1);
    assertRunToBlocked();
    sync();
    assertDocsMoved(2, 1);

    unblockJob(2);
    assertRunToBlocked();
    sync();
    assertDocsMoved(3, 1);

    unblockJob(3);
    assertRunToFinished();
    sync();
    assertDocsMoved(3, 0);
}

TEST_F(MaxOutstandingMoveOpsFixture_2, require_that_bucket_move_job_is_blocked_if_it_has_too_many_outstanding_move_operations_max_2)
{
    assertRunToNotBlocked();
    sync();
    assertDocsMoved(1, 1);

    assertRunToBlocked();
    sync();
    assertDocsMoved(2, 2);

    unblockJob(1);
    assertRunToFinished();
    sync();
    assertDocsMoved(3, 1);
}

GTEST_MAIN_RUN_ALL_TESTS()
