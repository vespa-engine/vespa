// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmover_common.h"
#include <vespa/searchcore/proton/server/bucketmovejob.h>
#include <vespa/searchcore/proton/server/document_db_maintenance_config.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("document_bucket_mover_test");

using namespace proton;
using namespace proton::move::test;
using document::BucketId;
using document::test::makeBucketSpace;
using proton::bucketdb::BucketCreateNotifier;
using storage::spi::BucketInfo;
using BlockedReason = IBlockableMaintenanceJob::BlockedReason;
using MoveOperationVector = std::vector<MoveOperation>;

struct MyFrozenBucketHandler : public IFrozenBucketHandler
{
    std::set<BucketId> _frozen;
    std::set<IBucketFreezeListener *> _listeners;

    MyFrozenBucketHandler()
        : IFrozenBucketHandler(),
          _frozen(),
          _listeners()
    {
    }

    ~MyFrozenBucketHandler() override {
        assert(_listeners.empty());
    }

    MyFrozenBucketHandler &addFrozen(const BucketId &bucket) {
        _frozen.insert(bucket);
        return *this;
    }
    MyFrozenBucketHandler &remFrozen(const BucketId &bucket) {
        _frozen.erase(bucket);
        for (auto &listener : _listeners) {
            listener->notifyThawedBucket(bucket);
        }
        return *this;
    }
    void addListener(IBucketFreezeListener *listener) override {
        _listeners.insert(listener);
    }
    void removeListener(IBucketFreezeListener *listener) override {
        _listeners.erase(listener);
    }

    ExclusiveBucketGuard::UP acquireExclusiveBucket(BucketId bucket) override {
        return (_frozen.count(bucket) != 0)
            ? ExclusiveBucketGuard::UP()
            : std::make_unique<ExclusiveBucketGuard>(bucket);
    }
};

struct ControllerFixtureBase : public ::testing::Test
{
    test::UserDocumentsBuilder  _builder;
    test::BucketStateCalculator::SP _calc;
    test::ClusterStateHandler   _clusterStateHandler;
    test::BucketHandler         _bucketHandler;
    MyBucketModifiedHandler     _modifiedHandler;
    std::shared_ptr<bucketdb::BucketDBOwner> _bucketDB;
    MyMoveHandler               _moveHandler;
    MySubDb                     _ready;
    MySubDb                     _notReady;
    MyFrozenBucketHandler       _fbh;
    BucketCreateNotifier        _bucketCreateNotifier;
    test::DiskMemUsageNotifier  _diskMemUsageNotifier;
    BucketMoveJob               _bmj;
    MyCountJobRunner            _runner;
    ControllerFixtureBase(const BlockableMaintenanceJobConfig &blockableConfig, bool storeMoveDoneContexts);
    ~ControllerFixtureBase();
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
    ControllerFixtureBase &addFrozen(const BucketId &bucket) {
        _fbh.addFrozen(bucket);
        return *this;
    }
    ControllerFixtureBase &remFrozen(const BucketId &bucket) {
        _fbh.remFrozen(bucket);
        _bmj.notifyThawedBucket(bucket);
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
    const MoveOperationVector &docsMoved() const {
        return _moveHandler._moves;
    }
    const std::vector<BucketId> &bucketsModified() const {
        return _modifiedHandler._modified;
    }
    const BucketId::List &calcAsked() const {
        return _calc->asked();
    }
    void runLoop() {
        while (!_bmj.isBlocked() && !_bmj.run()) {
        }
    }
};

ControllerFixtureBase::ControllerFixtureBase(const BlockableMaintenanceJobConfig &blockableConfig, bool storeMoveDoneContexts)
    : _builder(),
      _calc(std::make_shared<test::BucketStateCalculator>()),
      _bucketHandler(),
      _modifiedHandler(),
      _bucketDB(std::make_shared<bucketdb::BucketDBOwner>()),
      _moveHandler(*_bucketDB, storeMoveDoneContexts),
      _ready(_builder.getRepo(), _bucketDB, 1, SubDbType::READY),
      _notReady(_builder.getRepo(), _bucketDB, 2, SubDbType::NOTREADY),
      _fbh(),
      _bucketCreateNotifier(),
      _diskMemUsageNotifier(),
      _bmj(_calc, _moveHandler, _modifiedHandler, _ready._subDb,
           _notReady._subDb, _fbh, _bucketCreateNotifier, _clusterStateHandler, _bucketHandler,
           _diskMemUsageNotifier, blockableConfig,
           "test", makeBucketSpace()),
      _runner(_bmj)
{
}

ControllerFixtureBase::~ControllerFixtureBase() = default;
constexpr double RESOURCE_LIMIT_FACTOR = 1.0;
constexpr uint32_t MAX_OUTSTANDING_OPS = 10;
const BlockableMaintenanceJobConfig BLOCKABLE_CONFIG(RESOURCE_LIMIT_FACTOR, MAX_OUTSTANDING_OPS);

struct ControllerFixture : public ControllerFixtureBase
{
    ControllerFixture(const BlockableMaintenanceJobConfig &blockableConfig = BLOCKABLE_CONFIG)
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
    EXPECT_FALSE(_bmj.done());
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    _bmj.scanAndMove(4, 3);
    EXPECT_TRUE(_bmj.done());
    EXPECT_TRUE(docsMoved().empty());
    EXPECT_TRUE(bucketsModified().empty());
}

TEST_F(ControllerFixture, require_that_not_ready_bucket_is_moved_to_ready_if_bucket_state_says_so)
{
    // bucket 4 should be moved
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    addReady(_notReady.bucket(4));
    _bmj.scanAndMove(4, 3);
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(3u, docsMoved().size());
    assertEqual(_notReady.bucket(4), _notReady.docs(4)[0], 2, 1, docsMoved()[0]);
    assertEqual(_notReady.bucket(4), _notReady.docs(4)[1], 2, 1, docsMoved()[1]);
    assertEqual(_notReady.bucket(4), _notReady.docs(4)[2], 2, 1, docsMoved()[2]);
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_notReady.bucket(4), bucketsModified()[0]);
}

TEST_F(ControllerFixture, require_that_ready_bucket_is_moved_to_not_ready_if_bucket_state_says_so)
{
    // bucket 2 should be moved
    addReady(_ready.bucket(1));
    _bmj.scanAndMove(4, 3);
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(2u, docsMoved().size());
    assertEqual(_ready.bucket(2), _ready.docs(2)[0], 1, 2, docsMoved()[0]);
    assertEqual(_ready.bucket(2), _ready.docs(2)[1], 1, 2, docsMoved()[1]);
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(2), bucketsModified()[0]);
}

TEST_F(ControllerFixture, require_that_maxBucketsToScan_is_taken_into_consideration_between_not_ready_and_ready_scanning)
{
    // bucket 4 should moved (last bucket)
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    addReady(_notReady.bucket(4));

    // buckets 1, 2, and 3 considered
    _bmj.scanAndMove(3, 3);
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());

    // move bucket 4
    _bmj.scanAndMove(1, 4);
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(3u, docsMoved().size());
    assertEqual(_notReady.bucket(4), _notReady.docs(4)[0], 2, 1, docsMoved()[0]);
    assertEqual(_notReady.bucket(4), _notReady.docs(4)[1], 2, 1, docsMoved()[1]);
    assertEqual(_notReady.bucket(4), _notReady.docs(4)[2], 2, 1, docsMoved()[2]);
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_notReady.bucket(4), bucketsModified()[0]);
}

TEST_F(ControllerFixture, require_that_we_move_buckets_in_several_steps)
{
    // bucket 2, 3, and 4 should be moved
    addReady(_ready.bucket(1));
    addReady(_notReady.bucket(3));
    addReady(_notReady.bucket(4));

    // consider move bucket 1
    _bmj.scanAndMove(1, 2);
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());

    // move bucket 2, docs 1,2
    _bmj.scanAndMove(1, 2);
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(2u, docsMoved().size());
    EXPECT_TRUE(assertEqual(_ready.bucket(2), _ready.docs(2)[0], 1, 2, docsMoved()[0]));
    EXPECT_TRUE(assertEqual(_ready.bucket(2), _ready.docs(2)[1], 1, 2, docsMoved()[1]));
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(2), bucketsModified()[0]);

    // move bucket 3, docs 1,2
    _bmj.scanAndMove(1, 2);
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(4u, docsMoved().size());
    EXPECT_TRUE(assertEqual(_notReady.bucket(3), _notReady.docs(3)[0], 2, 1, docsMoved()[2]));
    EXPECT_TRUE(assertEqual(_notReady.bucket(3), _notReady.docs(3)[1], 2, 1, docsMoved()[3]));
    EXPECT_EQ(2u, bucketsModified().size());
    EXPECT_EQ(_notReady.bucket(3), bucketsModified()[1]);

    // move bucket 4, docs 1,2
    _bmj.scanAndMove(1, 2);
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(6u, docsMoved().size());
    EXPECT_TRUE(assertEqual(_notReady.bucket(4), _notReady.docs(4)[0], 2, 1, docsMoved()[4]));
    EXPECT_TRUE(assertEqual(_notReady.bucket(4), _notReady.docs(4)[1], 2, 1, docsMoved()[5]));
    EXPECT_EQ(2u, bucketsModified().size());

    // move bucket 4, docs 3
    _bmj.scanAndMove(1, 2);
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(7u, docsMoved().size());
    EXPECT_TRUE(assertEqual(_notReady.bucket(4), _notReady.docs(4)[2], 2, 1, docsMoved()[6]));
    EXPECT_EQ(3u, bucketsModified().size());
    EXPECT_EQ(_notReady.bucket(4), bucketsModified()[2]);
}

TEST_F(ControllerFixture, require_that_we_can_change_calculator_and_continue_scanning_where_we_left_off)
{
    // no buckets should move
    // original scan sequence is bucket1, bucket2, bucket3, bucket4
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));

    // start with bucket2
    _bmj.scanAndMove(1, 0);
    changeCalc();
    _bmj.scanAndMove(5, 0);
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(4u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(2),    calcAsked()[0]);
    EXPECT_EQ(_notReady.bucket(3), calcAsked()[1]);
    EXPECT_EQ(_notReady.bucket(4), calcAsked()[2]);
    EXPECT_EQ(_ready.bucket(1),    calcAsked()[3]);

    // start with bucket3
    changeCalc();
    _bmj.scanAndMove(2, 0);
    changeCalc();
    _bmj.scanAndMove(5, 0);
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(4u, calcAsked().size());
    EXPECT_EQ(_notReady.bucket(3), calcAsked()[0]);
    EXPECT_EQ(_notReady.bucket(4), calcAsked()[1]);
    EXPECT_EQ(_ready.bucket(1),    calcAsked()[2]);
    EXPECT_EQ(_ready.bucket(2),    calcAsked()[3]);

    // start with bucket4
    changeCalc();
    _bmj.scanAndMove(3, 0);
    changeCalc();
    _bmj.scanAndMove(5, 0);
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(4u, calcAsked().size());
    EXPECT_EQ(_notReady.bucket(4), calcAsked()[0]);
    EXPECT_EQ(_ready.bucket(1),    calcAsked()[1]);
    EXPECT_EQ(_ready.bucket(2),    calcAsked()[2]);
    EXPECT_EQ(_notReady.bucket(3), calcAsked()[3]);

    // start with bucket1
    changeCalc();
    _bmj.scanAndMove(5, 0);
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(4u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(1),    calcAsked()[0]);
    EXPECT_EQ(_ready.bucket(2),    calcAsked()[1]);
    EXPECT_EQ(_notReady.bucket(3), calcAsked()[2]);
    EXPECT_EQ(_notReady.bucket(4), calcAsked()[3]);

    // change calc in second pass
    changeCalc();
    _bmj.scanAndMove(3, 0);
    changeCalc();
    _bmj.scanAndMove(2, 0);
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(2u, calcAsked().size());
    EXPECT_EQ(_notReady.bucket(4), calcAsked()[0]);
    EXPECT_EQ(_ready.bucket(1),    calcAsked()[1]);
    changeCalc();
    _bmj.scanAndMove(5, 0);
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(4u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(2),    calcAsked()[0]);
    EXPECT_EQ(_notReady.bucket(3), calcAsked()[1]);
    EXPECT_EQ(_notReady.bucket(4), calcAsked()[2]);
    EXPECT_EQ(_ready.bucket(1),    calcAsked()[3]);

    // check 1 bucket at a time, start with bucket2
    changeCalc();
    _bmj.scanAndMove(1, 0);
    changeCalc();
    _bmj.scanAndMove(1, 0);
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(1u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(2), calcAsked()[0]);
    _bmj.scanAndMove(1, 0);
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(2u, calcAsked().size());
    EXPECT_EQ(_notReady.bucket(3), calcAsked()[1]);
    _bmj.scanAndMove(1, 0);
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(3u, calcAsked().size());
    EXPECT_EQ(_notReady.bucket(4), calcAsked()[2]);
    _bmj.scanAndMove(1, 0);
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(4u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(1), calcAsked()[3]);
}

TEST_F(ControllerFixture, require_that_current_bucket_moving_is_cancelled_when_we_change_calculator)
{
    // bucket 1 should be moved
    addReady(_ready.bucket(2));
    _bmj.scanAndMove(3, 1);
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(1u, docsMoved().size());
    EXPECT_EQ(1u, calcAsked().size());
    changeCalc(); // Not cancelled, bucket 1 still moving to notReady
    EXPECT_EQ(1u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(1), calcAsked()[0]);
    _calc->resetAsked();
    _bmj.scanAndMove(2, 1);
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(1u, docsMoved().size());
    EXPECT_EQ(0u, calcAsked().size());
    addReady(_ready.bucket(1));
    changeCalc(); // cancelled, bucket 1 no longer moving to notReady
    EXPECT_EQ(1u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(1), calcAsked()[0]);
    _calc->resetAsked();
    remReady(_ready.bucket(1));
    changeCalc(); // not cancelled.  No active bucket move
    EXPECT_EQ(0u, calcAsked().size());
    _calc->resetAsked();
    _bmj.scanAndMove(2, 1);
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(2u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(2), calcAsked()[0]);
    EXPECT_EQ(_notReady.bucket(3), calcAsked()[1]);
    _bmj.scanAndMove(2, 3);
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(3u, docsMoved().size());
    EXPECT_EQ(4u, calcAsked().size());
    EXPECT_EQ(_notReady.bucket(4), calcAsked()[2]);
    EXPECT_EQ(_ready.bucket(1), calcAsked()[3]);
}

TEST_F(ControllerFixture, require_that_last_bucket_is_moved_before_reporting_done)
{
    // bucket 4 should be moved
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    addReady(_notReady.bucket(4));
    _bmj.scanAndMove(4, 1);
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(1u, docsMoved().size());
    EXPECT_EQ(4u, calcAsked().size());
    _bmj.scanAndMove(0, 2);
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(3u, docsMoved().size());
    EXPECT_EQ(4u, calcAsked().size());
}

TEST_F(ControllerFixture, require_that_frozen_bucket_is_not_moved_until_thawed)
{
    // bucket 1 should be moved but is frozen
    addReady(_ready.bucket(2));
    addFrozen(_ready.bucket(1));
    _bmj.scanAndMove(4, 3); // scan all, delay frozen bucket 1
    remFrozen(_ready.bucket(1));
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());
    _bmj.scanAndMove(0, 3); // move delayed and thawed bucket 1
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(3u, docsMoved().size());
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(1), bucketsModified()[0]);
}

TEST_F(ControllerFixture, require_that_thawed_bucket_is_moved_before_other_buckets)
{
    // bucket 2 should be moved but is frozen.
    // bucket 3 & 4 should also be moved
    addReady(_ready.bucket(1));
    addReady(_notReady.bucket(3));
    addReady(_notReady.bucket(4));
    addFrozen(_ready.bucket(2));
    _bmj.scanAndMove(3, 2); // delay bucket 2, move bucket 3
    remFrozen(_ready.bucket(2));
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(2u, docsMoved().size());
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_notReady.bucket(3), bucketsModified()[0]);
    _bmj.scanAndMove(2, 2); // move thawed bucket 2
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(4u, docsMoved().size());
    EXPECT_EQ(2u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(2), bucketsModified()[1]);
    _bmj.scanAndMove(1, 4); // move bucket 4
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(7u, docsMoved().size());
    EXPECT_EQ(3u, bucketsModified().size());
    EXPECT_EQ(_notReady.bucket(4), bucketsModified()[2]);
}

TEST_F(ControllerFixture, require_that_re_frozen_thawed_bucket_is_not_moved_until_re_thawed)
{
    // bucket 1 should be moved but is re-frozen
    addReady(_ready.bucket(2));
    addFrozen(_ready.bucket(1));
    _bmj.scanAndMove(1, 0); // scan, delay frozen bucket 1
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());
    EXPECT_EQ(1u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(1), calcAsked()[0]);
    remFrozen(_ready.bucket(1));
    addFrozen(_ready.bucket(1));
    _bmj.scanAndMove(1, 0); // scan, but nothing to move
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());
    EXPECT_EQ(3u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(1), calcAsked()[1]);
    EXPECT_EQ(_ready.bucket(2), calcAsked()[2]);
    remFrozen(_ready.bucket(1));
    _bmj.scanAndMove(3, 4); // move delayed and thawed bucket 1
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(3u, docsMoved().size());
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(1), bucketsModified()[0]);
    EXPECT_EQ(4u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(1), calcAsked()[3]);
    _bmj.scanAndMove(2, 0); // scan the rest
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(3u, docsMoved().size());
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(6u, calcAsked().size());
}

TEST_F(ControllerFixture, require_that_thawed_bucket_is_not_moved_if_new_calculator_does_not_say_so)
{
    // bucket 3 should be moved
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    addReady(_notReady.bucket(3));
    addFrozen(_notReady.bucket(3));
    _bmj.scanAndMove(4, 3); // scan all, delay frozen bucket 3
    remFrozen(_notReady.bucket(3));
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());
    EXPECT_EQ(4u, calcAsked().size());
    changeCalc();
    remReady(_notReady.bucket(3));
    _bmj.scanAndMove(0, 3); // consider delayed bucket 3
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());
    EXPECT_EQ(1u, calcAsked().size());
    EXPECT_EQ(_notReady.bucket(3), calcAsked()[0]);
}

TEST_F(ControllerFixture, require_that_current_bucket_mover_is_cancelled_if_bucket_is_frozen)
{
    // bucket 3 should be moved
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    addReady(_notReady.bucket(3));
    _bmj.scanAndMove(3, 1); // move 1 doc from bucket 3
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(1u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());
    EXPECT_EQ(3u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(1), calcAsked()[0]);
    EXPECT_EQ(_ready.bucket(2), calcAsked()[1]);
    EXPECT_EQ(_notReady.bucket(3), calcAsked()[2]);

    addFrozen(_notReady.bucket(3));
    _bmj.scanAndMove(1, 3); // done scanning
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(1u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());
    EXPECT_EQ(3u, calcAsked().size());

    _bmj.scanAndMove(1, 3); // done scanning
    remFrozen(_notReady.bucket(3));
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(1u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());
    EXPECT_EQ(4u, calcAsked().size());

    EXPECT_EQ(_notReady.bucket(4), calcAsked()[3]);
    _bmj.scanAndMove(0, 2); // move all docs from bucket 3 again
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(3u, docsMoved().size());
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_notReady.bucket(3), bucketsModified()[0]);
    EXPECT_EQ(5u, calcAsked().size());
    EXPECT_EQ(_notReady.bucket(3), calcAsked()[4]);
}

TEST_F(ControllerFixture, require_that_current_bucket_mover_is_not_cancelled_if_another_bucket_is_frozen)
{
    // bucket 3 and 4 should be moved
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    addReady(_notReady.bucket(3));
    addReady(_notReady.bucket(4));
    _bmj.scanAndMove(3, 1); // move 1 doc from bucket 3
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(1u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());
    EXPECT_EQ(3u, calcAsked().size());
    addFrozen(_notReady.bucket(4));
    _bmj.scanAndMove(1, 2); // move rest of docs from bucket 3
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(2u, docsMoved().size());
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_notReady.bucket(3), bucketsModified()[0]);
    EXPECT_EQ(3u, calcAsked().size());
}

TEST_F(ControllerFixture, require_that_active_bucket_is_not_moved_from_ready_to_not_ready_until_being_not_active)
{
    // bucket 1 should be moved but is active
    addReady(_ready.bucket(2));
    activateBucket(_ready.bucket(1));
    _bmj.scanAndMove(4, 3); // scan all, delay active bucket 1
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());

    deactivateBucket(_ready.bucket(1));
    EXPECT_FALSE(_bmj.done());
    _bmj.scanAndMove(0, 3); // move delayed and de-activated bucket 1
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(3u, docsMoved().size());
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(1), bucketsModified()[0]);
}

TEST_F(OnlyReadyControllerFixture, require_that_de_activated_bucket_is_moved_before_other_buckets)
{
    // bucket 1, 2, 3 should be moved (but bucket 1 is active)
    addReady(_ready.bucket(4));
    activateBucket(_ready.bucket(1));
    _bmj.scanAndMove(2, 4); // delay bucket 1, move bucket 2
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(2u, docsMoved().size());
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(2), bucketsModified()[0]);

    deactivateBucket(_ready.bucket(1));
    _bmj.scanAndMove(2, 4); // move de-activated bucket 1
    EXPECT_FALSE(_bmj.done());
    EXPECT_EQ(3u, docsMoved().size());
    EXPECT_EQ(2u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(1), bucketsModified()[1]);

    _bmj.scanAndMove(2, 4); // move bucket 3
    // EXPECT_TRUE(_bmj.done()); // TODO(geirst): fix this
    EXPECT_EQ(6u, docsMoved().size());
    EXPECT_EQ(3u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(3), bucketsModified()[2]);
}

TEST_F(ControllerFixture, require_that_de_activated_bucket_is_not_moved_if_new_calculator_does_not_say_so)
{
    // bucket 1 should be moved
    addReady(_ready.bucket(2));
    activateBucket(_ready.bucket(1));
    _bmj.scanAndMove(4, 3); // scan all, delay active bucket 1
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());

    deactivateBucket(_ready.bucket(1));
    addReady(_ready.bucket(1));
    changeCalc();
    _bmj.scanAndMove(0, 3); // consider delayed bucket 3
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());
    EXPECT_EQ(1u, calcAsked().size());
    EXPECT_EQ(_ready.bucket(1), calcAsked()[0]);
}

TEST_F(ControllerFixture, require_that_de_activated_bucket_is_not_moved_if_frozen_as_well)
{
    // bucket 1 should be moved
    addReady(_ready.bucket(2));
    activateBucket(_ready.bucket(1));
    _bmj.scanAndMove(4, 3); // scan all, delay active bucket 1
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());

    addFrozen(_ready.bucket(1));
    deactivateBucket(_ready.bucket(1));
    _bmj.scanAndMove(0, 3); // bucket 1 de-activated but frozen
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());

    remFrozen(_ready.bucket(1));
    _bmj.scanAndMove(0, 3); // handle thawed bucket 1
    EXPECT_EQ(3u, docsMoved().size());
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(1), bucketsModified()[0]);
}

TEST_F(ControllerFixture, require_that_thawed_bucket_is_not_moved_if_active_as_well)
{
    // bucket 1 should be moved
    addReady(_ready.bucket(2));
    addFrozen(_ready.bucket(1));
    _bmj.scanAndMove(4, 3); // scan all, delay frozen bucket 1
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());

    activateBucket(_ready.bucket(1));
    remFrozen(_ready.bucket(1));
    _bmj.scanAndMove(0, 3); // bucket 1 thawed but active
    EXPECT_EQ(0u, docsMoved().size());
    EXPECT_EQ(0u, bucketsModified().size());

    deactivateBucket(_ready.bucket(1));
    _bmj.scanAndMove(0, 3); // handle de-activated bucket 1
    EXPECT_EQ(3u, docsMoved().size());
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_ready.bucket(1), bucketsModified()[0]);
}

TEST_F(ControllerFixture, ready_bucket_not_moved_to_not_ready_if_node_is_marked_as_retired)
{
    _calc->setNodeRetired(true);
    // Bucket 2 would be moved from ready to not ready in a non-retired case, but not when retired.
    addReady(_ready.bucket(1));
    _bmj.scanAndMove(4, 3);
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(0u, docsMoved().size());
}

// Technically this should never happen since a retired node is never in the ideal state,
// but test this case for the sake of completion.
TEST_F(ControllerFixture, inactive_not_ready_bucket_not_moved_to_ready_if_node_is_marked_as_retired)
{
    _calc->setNodeRetired(true);
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    addReady(_notReady.bucket(3));
    _bmj.scanAndMove(4, 3);
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(0u, docsMoved().size());
}

TEST_F(ControllerFixture, explicitly_active_not_ready_bucket_can_be_moved_to_ready_even_if_node_is_marked_as_retired)
{
    _calc->setNodeRetired(true);
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    addReady(_notReady.bucket(3));
    activateBucket(_notReady.bucket(3));
    _bmj.scanAndMove(4, 3);
    EXPECT_FALSE(_bmj.done());
    ASSERT_EQ(2u, docsMoved().size());
    assertEqual(_notReady.bucket(3), _notReady.docs(3)[0], 2, 1, docsMoved()[0]);
    assertEqual(_notReady.bucket(3), _notReady.docs(3)[1], 2, 1, docsMoved()[1]);
    ASSERT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(_notReady.bucket(3), bucketsModified()[0]);
}

TEST_F(ControllerFixture, require_that_notifyCreateBucket_causes_bucket_to_be_reconsidered_by_job)
{
    EXPECT_FALSE(_bmj.done());
    addReady(_ready.bucket(1));
    addReady(_ready.bucket(2));
    runLoop();
    EXPECT_TRUE(_bmj.done());
    EXPECT_TRUE(docsMoved().empty());
    EXPECT_TRUE(bucketsModified().empty());
    addReady(_notReady.bucket(3)); // bucket 3 now ready, no notify
    EXPECT_TRUE(_bmj.done());        // move job still believes work done
    _bmj.notifyCreateBucket(_bucketDB->takeGuard(), _notReady.bucket(3)); // reconsider bucket 3
    EXPECT_FALSE(_bmj.done());
    runLoop();
    EXPECT_TRUE(_bmj.done());
    EXPECT_EQ(1u, bucketsModified().size());
    EXPECT_EQ(2u, docsMoved().size());
}

struct ResourceLimitControllerFixture : public ControllerFixture
{
    ResourceLimitControllerFixture(double resourceLimitFactor = RESOURCE_LIMIT_FACTOR) :
        ControllerFixture(BlockableMaintenanceJobConfig(resourceLimitFactor, MAX_OUTSTANDING_OPS))
    {}

    void testJobStopping(DiskMemUsageState blockingUsageState) {
        // Bucket 1 should be moved
        addReady(_ready.bucket(2));
        // Note: This depends on _bmj.run() moving max 1 documents
        EXPECT_TRUE(!_bmj.run());
        EXPECT_EQ(1u, docsMoved().size());
        EXPECT_EQ(0u, bucketsModified().size());
        // Notify that we've over limit
        _diskMemUsageNotifier.notify(blockingUsageState);
        EXPECT_TRUE(_bmj.run());
        EXPECT_EQ(1u, docsMoved().size());
        EXPECT_EQ(0u, bucketsModified().size());
        // Notify that we've under limit
        _diskMemUsageNotifier.notify(DiskMemUsageState());
        EXPECT_TRUE(!_bmj.run());
        EXPECT_EQ(2u, docsMoved().size());
        EXPECT_EQ(0u, bucketsModified().size());
    }

    void testJobNotStopping(DiskMemUsageState blockingUsageState) {
        // Bucket 1 should be moved
        addReady(_ready.bucket(2));
        // Note: This depends on _bmj.run() moving max 1 documents
        EXPECT_TRUE(!_bmj.run());
        EXPECT_EQ(1u, docsMoved().size());
        EXPECT_EQ(0u, bucketsModified().size());
        // Notify that we've over limit, but not over adjusted limit
        _diskMemUsageNotifier.notify(blockingUsageState);
        EXPECT_TRUE(!_bmj.run());
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

struct MaxOutstandingMoveOpsFixture : public ControllerFixture
{
    MaxOutstandingMoveOpsFixture(uint32_t maxOutstandingOps) :
            ControllerFixture(BlockableMaintenanceJobConfig(RESOURCE_LIMIT_FACTOR, maxOutstandingOps))
    {
        // Bucket 1 should be moved from ready -> notready
        addReady(_ready.bucket(2));
    }

    void assertRunToBlocked() {
        EXPECT_TRUE(_bmj.run()); // job becomes blocked as max outstanding limit is reached
        EXPECT_FALSE(_bmj.done());
        EXPECT_TRUE(_bmj.isBlocked());
        EXPECT_TRUE(_bmj.isBlocked(BlockedReason::OUTSTANDING_OPS));
    }
    void assertRunToNotBlocked() {
        EXPECT_FALSE(_bmj.run());
        EXPECT_FALSE(_bmj.done());
        EXPECT_FALSE(_bmj.isBlocked());
    }
    void assertRunToFinished() {
        EXPECT_TRUE(_bmj.run());
        EXPECT_TRUE(_bmj.done());
        EXPECT_FALSE(_bmj.isBlocked());
    }
    void assertDocsMoved(uint32_t expDocsMovedCnt, uint32_t expMoveContextsCnt) {
        EXPECT_EQ(expDocsMovedCnt, docsMoved().size());
        EXPECT_EQ(expMoveContextsCnt, _moveHandler._moveDoneContexts.size());
    }
    void unblockJob(uint32_t expRunnerCnt) {
        _moveHandler.clearMoveDoneContexts(); // unblocks job and try to execute it via runner
        EXPECT_EQ(expRunnerCnt, _runner.runCount);
        EXPECT_FALSE(_bmj.isBlocked());
    }
};

struct MaxOutstandingMoveOpsFixture_1 : public MaxOutstandingMoveOpsFixture {
    MaxOutstandingMoveOpsFixture_1() : MaxOutstandingMoveOpsFixture(1) {}
};

struct MaxOutstandingMoveOpsFixture_2 : public MaxOutstandingMoveOpsFixture {
    MaxOutstandingMoveOpsFixture_2() : MaxOutstandingMoveOpsFixture(2) {}
};

TEST_F(MaxOutstandingMoveOpsFixture_1, require_that_bucket_move_job_is_blocked_if_it_has_too_many_outstanding_move_operations__max_1)
{
    assertRunToBlocked();
    assertDocsMoved(1, 1);
    assertRunToBlocked();
    assertDocsMoved(1, 1);

    unblockJob(1);
    assertRunToBlocked();
    assertDocsMoved(2, 1);

    unblockJob(2);
    assertRunToBlocked();
    assertDocsMoved(3, 1);

    unblockJob(3);
    assertRunToFinished();
    assertDocsMoved(3, 0);
}

TEST_F(MaxOutstandingMoveOpsFixture_2, require_that_bucket_move_job_is_blocked_if_it_has_too_many_outstanding_move_operations_max_2)
{
    assertRunToNotBlocked();
    assertDocsMoved(1, 1);

    assertRunToBlocked();
    assertDocsMoved(2, 2);

    unblockJob(1);
    assertRunToNotBlocked();
    assertDocsMoved(3, 1);

    assertRunToFinished();
    assertDocsMoved(3, 1);
}

GTEST_MAIN_RUN_ALL_TESTS()
