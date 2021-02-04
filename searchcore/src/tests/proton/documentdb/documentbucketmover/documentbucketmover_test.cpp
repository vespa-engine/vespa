// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketmover_common.h"
#include <vespa/vespalib/testkit/testapp.h>

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

struct MyCountJobRunner : public IMaintenanceJobRunner {
    uint32_t runCount;
    explicit MyCountJobRunner(IMaintenanceJob &job) : runCount(0) {
        job.registerRunner(this);
    }
    void run() override { ++runCount; }
};

struct ControllerFixtureBase
{
    test::UserDocumentsBuilder  _builder;
    test::BucketStateCalculator::SP _calc;
    test::ClusterStateHandler   _clusterStateHandler;
    test::BucketHandler         _bucketHandler;
    MyBucketModifiedHandler     _modifiedHandler;
    std::shared_ptr<BucketDBOwner> _bucketDB;
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
    const BucketId::List &bucketsModified() const {
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
      _bucketDB(std::make_shared<BucketDBOwner>()),
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

ControllerFixtureBase::~ControllerFixtureBase() {}
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

TEST_F("require that nothing is moved if bucket state says so", ControllerFixture)
{
    EXPECT_FALSE(f._bmj.done());
    f.addReady(f._ready.bucket(1));
    f.addReady(f._ready.bucket(2));
    f._bmj.scanAndMove(4, 3);
    EXPECT_TRUE(f._bmj.done());
    EXPECT_TRUE(f.docsMoved().empty());
    EXPECT_TRUE(f.bucketsModified().empty());
}

TEST_F("require that not ready bucket is moved to ready if bucket state says so", ControllerFixture)
{
    // bucket 4 should be moved
    f.addReady(f._ready.bucket(1));
    f.addReady(f._ready.bucket(2));
    f.addReady(f._notReady.bucket(4));
    f._bmj.scanAndMove(4, 3);
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(3u, f.docsMoved().size());
    assertEqual(f._notReady.bucket(4), f._notReady.docs(4)[0], 2, 1, f.docsMoved()[0]);
    assertEqual(f._notReady.bucket(4), f._notReady.docs(4)[1], 2, 1, f.docsMoved()[1]);
    assertEqual(f._notReady.bucket(4), f._notReady.docs(4)[2], 2, 1, f.docsMoved()[2]);
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(f._notReady.bucket(4), f.bucketsModified()[0]);
}

TEST_F("require that ready bucket is moved to not ready if bucket state says so", ControllerFixture)
{
    // bucket 2 should be moved
    f.addReady(f._ready.bucket(1));
    f._bmj.scanAndMove(4, 3);
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(2u, f.docsMoved().size());
    assertEqual(f._ready.bucket(2), f._ready.docs(2)[0], 1, 2, f.docsMoved()[0]);
    assertEqual(f._ready.bucket(2), f._ready.docs(2)[1], 1, 2, f.docsMoved()[1]);
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(f._ready.bucket(2), f.bucketsModified()[0]);
}

TEST_F("require that maxBucketsToScan is taken into consideration between not ready and ready scanning", ControllerFixture)
{
    // bucket 4 should moved (last bucket)
    f.addReady(f._ready.bucket(1));
    f.addReady(f._ready.bucket(2));
    f.addReady(f._notReady.bucket(4));

    // buckets 1, 2, and 3 considered
    f._bmj.scanAndMove(3, 3);
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());

    // move bucket 4
    f._bmj.scanAndMove(1, 4);
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(3u, f.docsMoved().size());
    assertEqual(f._notReady.bucket(4), f._notReady.docs(4)[0], 2, 1, f.docsMoved()[0]);
    assertEqual(f._notReady.bucket(4), f._notReady.docs(4)[1], 2, 1, f.docsMoved()[1]);
    assertEqual(f._notReady.bucket(4), f._notReady.docs(4)[2], 2, 1, f.docsMoved()[2]);
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(f._notReady.bucket(4), f.bucketsModified()[0]);
}

TEST_F("require that we move buckets in several steps", ControllerFixture)
{
    // bucket 2, 3, and 4 should be moved
    f.addReady(f._ready.bucket(1));
    f.addReady(f._notReady.bucket(3));
    f.addReady(f._notReady.bucket(4));

    // consider move bucket 1
    f._bmj.scanAndMove(1, 2);
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());

    // move bucket 2, docs 1,2
    f._bmj.scanAndMove(1, 2);
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(2u, f.docsMoved().size());
    EXPECT_TRUE(assertEqual(f._ready.bucket(2), f._ready.docs(2)[0], 1, 2, f.docsMoved()[0]));
    EXPECT_TRUE(assertEqual(f._ready.bucket(2), f._ready.docs(2)[1], 1, 2, f.docsMoved()[1]));
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(f._ready.bucket(2), f.bucketsModified()[0]);

    // move bucket 3, docs 1,2
    f._bmj.scanAndMove(1, 2);
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(4u, f.docsMoved().size());
    EXPECT_TRUE(assertEqual(f._notReady.bucket(3), f._notReady.docs(3)[0], 2, 1, f.docsMoved()[2]));
    EXPECT_TRUE(assertEqual(f._notReady.bucket(3), f._notReady.docs(3)[1], 2, 1, f.docsMoved()[3]));
    EXPECT_EQUAL(2u, f.bucketsModified().size());
    EXPECT_EQUAL(f._notReady.bucket(3), f.bucketsModified()[1]);

    // move bucket 4, docs 1,2
    f._bmj.scanAndMove(1, 2);
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(6u, f.docsMoved().size());
    EXPECT_TRUE(assertEqual(f._notReady.bucket(4), f._notReady.docs(4)[0], 2, 1, f.docsMoved()[4]));
    EXPECT_TRUE(assertEqual(f._notReady.bucket(4), f._notReady.docs(4)[1], 2, 1, f.docsMoved()[5]));
    EXPECT_EQUAL(2u, f.bucketsModified().size());

    // move bucket 4, docs 3
    f._bmj.scanAndMove(1, 2);
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(7u, f.docsMoved().size());
    EXPECT_TRUE(assertEqual(f._notReady.bucket(4), f._notReady.docs(4)[2], 2, 1, f.docsMoved()[6]));
    EXPECT_EQUAL(3u, f.bucketsModified().size());
    EXPECT_EQUAL(f._notReady.bucket(4), f.bucketsModified()[2]);
}

TEST_F("require that we can change calculator and continue scanning where we left off", ControllerFixture)
{
    // no buckets should move
    // original scan sequence is bucket1, bucket2, bucket3, bucket4
    f.addReady(f._ready.bucket(1));
    f.addReady(f._ready.bucket(2));

    // start with bucket2
    f._bmj.scanAndMove(1, 0);
    f.changeCalc();
    f._bmj.scanAndMove(5, 0);
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(4u, f.calcAsked().size());
    EXPECT_EQUAL(f._ready.bucket(2),    f.calcAsked()[0]);
    EXPECT_EQUAL(f._notReady.bucket(3), f.calcAsked()[1]);
    EXPECT_EQUAL(f._notReady.bucket(4), f.calcAsked()[2]);
    EXPECT_EQUAL(f._ready.bucket(1),    f.calcAsked()[3]);

    // start with bucket3
    f.changeCalc();
    f._bmj.scanAndMove(2, 0);
    f.changeCalc();
    f._bmj.scanAndMove(5, 0);
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(4u, f.calcAsked().size());
    EXPECT_EQUAL(f._notReady.bucket(3), f.calcAsked()[0]);
    EXPECT_EQUAL(f._notReady.bucket(4), f.calcAsked()[1]);
    EXPECT_EQUAL(f._ready.bucket(1),    f.calcAsked()[2]);
    EXPECT_EQUAL(f._ready.bucket(2),    f.calcAsked()[3]);

    // start with bucket4
    f.changeCalc();
    f._bmj.scanAndMove(3, 0);
    f.changeCalc();
    f._bmj.scanAndMove(5, 0);
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(4u, f.calcAsked().size());
    EXPECT_EQUAL(f._notReady.bucket(4), f.calcAsked()[0]);
    EXPECT_EQUAL(f._ready.bucket(1),    f.calcAsked()[1]);
    EXPECT_EQUAL(f._ready.bucket(2),    f.calcAsked()[2]);
    EXPECT_EQUAL(f._notReady.bucket(3), f.calcAsked()[3]);

    // start with bucket1
    f.changeCalc();
    f._bmj.scanAndMove(5, 0);
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(4u, f.calcAsked().size());
    EXPECT_EQUAL(f._ready.bucket(1),    f.calcAsked()[0]);
    EXPECT_EQUAL(f._ready.bucket(2),    f.calcAsked()[1]);
    EXPECT_EQUAL(f._notReady.bucket(3), f.calcAsked()[2]);
    EXPECT_EQUAL(f._notReady.bucket(4), f.calcAsked()[3]);

    // change calc in second pass
    f.changeCalc();
    f._bmj.scanAndMove(3, 0);
    f.changeCalc();
    f._bmj.scanAndMove(2, 0);
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(2u, f.calcAsked().size());
    EXPECT_EQUAL(f._notReady.bucket(4), f.calcAsked()[0]);
    EXPECT_EQUAL(f._ready.bucket(1),    f.calcAsked()[1]);
    f.changeCalc();
    f._bmj.scanAndMove(5, 0);
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(4u, f.calcAsked().size());
    EXPECT_EQUAL(f._ready.bucket(2),    f.calcAsked()[0]);
    EXPECT_EQUAL(f._notReady.bucket(3), f.calcAsked()[1]);
    EXPECT_EQUAL(f._notReady.bucket(4), f.calcAsked()[2]);
    EXPECT_EQUAL(f._ready.bucket(1),    f.calcAsked()[3]);

    // check 1 bucket at a time, start with bucket2
    f.changeCalc();
    f._bmj.scanAndMove(1, 0);
    f.changeCalc();
    f._bmj.scanAndMove(1, 0);
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(1u, f.calcAsked().size());
    EXPECT_EQUAL(f._ready.bucket(2), f.calcAsked()[0]);
    f._bmj.scanAndMove(1, 0);
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(2u, f.calcAsked().size());
    EXPECT_EQUAL(f._notReady.bucket(3), f.calcAsked()[1]);
    f._bmj.scanAndMove(1, 0);
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(3u, f.calcAsked().size());
    EXPECT_EQUAL(f._notReady.bucket(4), f.calcAsked()[2]);
    f._bmj.scanAndMove(1, 0);
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(4u, f.calcAsked().size());
    EXPECT_EQUAL(f._ready.bucket(1), f.calcAsked()[3]);
}

TEST_F("require that current bucket moving is cancelled when we change calculator", ControllerFixture)
{
    // bucket 1 should be moved
    f.addReady(f._ready.bucket(2));
    f._bmj.scanAndMove(3, 1);
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(1u, f.docsMoved().size());
    EXPECT_EQUAL(1u, f.calcAsked().size());
    f.changeCalc(); // Not cancelled, bucket 1 still moving to notReady
    EXPECT_EQUAL(1u, f.calcAsked().size());
    EXPECT_EQUAL(f._ready.bucket(1), f.calcAsked()[0]);
    f._calc->resetAsked();
    f._bmj.scanAndMove(2, 1);
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(1u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.calcAsked().size());
    f.addReady(f._ready.bucket(1));
    f.changeCalc(); // cancelled, bucket 1 no longer moving to notReady
    EXPECT_EQUAL(1u, f.calcAsked().size());
    EXPECT_EQUAL(f._ready.bucket(1), f.calcAsked()[0]);
    f._calc->resetAsked();
    f.remReady(f._ready.bucket(1));
    f.changeCalc(); // not cancelled.  No active bucket move
    EXPECT_EQUAL(0u, f.calcAsked().size());
    f._calc->resetAsked();
    f._bmj.scanAndMove(2, 1);
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(2u, f.calcAsked().size());
    EXPECT_EQUAL(f._ready.bucket(2), f.calcAsked()[0]);
    EXPECT_EQUAL(f._notReady.bucket(3), f.calcAsked()[1]);
    f._bmj.scanAndMove(2, 3);
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(3u, f.docsMoved().size());
    EXPECT_EQUAL(4u, f.calcAsked().size());
    EXPECT_EQUAL(f._notReady.bucket(4), f.calcAsked()[2]);
    EXPECT_EQUAL(f._ready.bucket(1), f.calcAsked()[3]);
}

TEST_F("require that last bucket is moved before reporting done", ControllerFixture)
{
    // bucket 4 should be moved
    f.addReady(f._ready.bucket(1));
    f.addReady(f._ready.bucket(2));
    f.addReady(f._notReady.bucket(4));
    f._bmj.scanAndMove(4, 1);
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(1u, f.docsMoved().size());
    EXPECT_EQUAL(4u, f.calcAsked().size());
    f._bmj.scanAndMove(0, 2);
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(3u, f.docsMoved().size());
    EXPECT_EQUAL(4u, f.calcAsked().size());
}

TEST_F("require that frozen bucket is not moved until thawed", ControllerFixture)
{
    // bucket 1 should be moved but is frozen
    f.addReady(f._ready.bucket(2));
    f.addFrozen(f._ready.bucket(1));
    f._bmj.scanAndMove(4, 3); // scan all, delay frozen bucket 1
    f.remFrozen(f._ready.bucket(1));
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());
    f._bmj.scanAndMove(0, 3); // move delayed and thawed bucket 1
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(3u, f.docsMoved().size());
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(f._ready.bucket(1), f.bucketsModified()[0]);
}

TEST_F("require that thawed bucket is moved before other buckets", ControllerFixture)
{
    // bucket 2 should be moved but is frozen.
    // bucket 3 & 4 should also be moved
    f.addReady(f._ready.bucket(1));
    f.addReady(f._notReady.bucket(3));
    f.addReady(f._notReady.bucket(4));
    f.addFrozen(f._ready.bucket(2));
    f._bmj.scanAndMove(3, 2); // delay bucket 2, move bucket 3
    f.remFrozen(f._ready.bucket(2));
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(2u, f.docsMoved().size());
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(f._notReady.bucket(3), f.bucketsModified()[0]);
    f._bmj.scanAndMove(2, 2); // move thawed bucket 2
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(4u, f.docsMoved().size());
    EXPECT_EQUAL(2u, f.bucketsModified().size());
    EXPECT_EQUAL(f._ready.bucket(2), f.bucketsModified()[1]);
    f._bmj.scanAndMove(1, 4); // move bucket 4
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(7u, f.docsMoved().size());
    EXPECT_EQUAL(3u, f.bucketsModified().size());
    EXPECT_EQUAL(f._notReady.bucket(4), f.bucketsModified()[2]);
}

TEST_F("require that re-frozen thawed bucket is not moved until re-thawed", ControllerFixture)
{
    // bucket 1 should be moved but is re-frozen
    f.addReady(f._ready.bucket(2));
    f.addFrozen(f._ready.bucket(1));
    f._bmj.scanAndMove(1, 0); // scan, delay frozen bucket 1
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());
    EXPECT_EQUAL(1u, f.calcAsked().size());
    EXPECT_EQUAL(f._ready.bucket(1), f.calcAsked()[0]);
    f.remFrozen(f._ready.bucket(1));
    f.addFrozen(f._ready.bucket(1));
    f._bmj.scanAndMove(1, 0); // scan, but nothing to move
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());
    EXPECT_EQUAL(3u, f.calcAsked().size());
    EXPECT_EQUAL(f._ready.bucket(1), f.calcAsked()[1]);
    EXPECT_EQUAL(f._ready.bucket(2), f.calcAsked()[2]);
    f.remFrozen(f._ready.bucket(1));
    f._bmj.scanAndMove(3, 4); // move delayed and thawed bucket 1
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(3u, f.docsMoved().size());
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(f._ready.bucket(1), f.bucketsModified()[0]);
    EXPECT_EQUAL(4u, f.calcAsked().size());
    EXPECT_EQUAL(f._ready.bucket(1), f.calcAsked()[3]);
    f._bmj.scanAndMove(2, 0); // scan the rest
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(3u, f.docsMoved().size());
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(6u, f.calcAsked().size());
}

TEST_F("require that thawed bucket is not moved if new calculator does not say so", ControllerFixture)
{
    // bucket 3 should be moved
    f.addReady(f._ready.bucket(1));
    f.addReady(f._ready.bucket(2));
    f.addReady(f._notReady.bucket(3));
    f.addFrozen(f._notReady.bucket(3));
    f._bmj.scanAndMove(4, 3); // scan all, delay frozen bucket 3
    f.remFrozen(f._notReady.bucket(3));
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());
    EXPECT_EQUAL(4u, f.calcAsked().size());
    f.changeCalc();
    f.remReady(f._notReady.bucket(3));
    f._bmj.scanAndMove(0, 3); // consider delayed bucket 3
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());
    EXPECT_EQUAL(1u, f.calcAsked().size());
    EXPECT_EQUAL(f._notReady.bucket(3), f.calcAsked()[0]);
}

TEST_F("require that current bucket mover is cancelled if bucket is frozen", ControllerFixture)
{
    // bucket 3 should be moved
    f.addReady(f._ready.bucket(1));
    f.addReady(f._ready.bucket(2));
    f.addReady(f._notReady.bucket(3));
    f._bmj.scanAndMove(3, 1); // move 1 doc from bucket 3
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(1u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());
    EXPECT_EQUAL(3u, f.calcAsked().size());
    EXPECT_EQUAL(f._ready.bucket(1), f.calcAsked()[0]);
    EXPECT_EQUAL(f._ready.bucket(2), f.calcAsked()[1]);
    EXPECT_EQUAL(f._notReady.bucket(3), f.calcAsked()[2]);

    f.addFrozen(f._notReady.bucket(3));
    f._bmj.scanAndMove(1, 3); // done scanning
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(1u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());
    EXPECT_EQUAL(3u, f.calcAsked().size());

    f._bmj.scanAndMove(1, 3); // done scanning
    f.remFrozen(f._notReady.bucket(3));
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(1u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());
    EXPECT_EQUAL(4u, f.calcAsked().size());

    EXPECT_EQUAL(f._notReady.bucket(4), f.calcAsked()[3]);
    f._bmj.scanAndMove(0, 2); // move all docs from bucket 3 again
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(3u, f.docsMoved().size());
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(f._notReady.bucket(3), f.bucketsModified()[0]);
    EXPECT_EQUAL(5u, f.calcAsked().size());
    EXPECT_EQUAL(f._notReady.bucket(3), f.calcAsked()[4]);
}

TEST_F("require that current bucket mover is not cancelled if another bucket is frozen", ControllerFixture)
{
    // bucket 3 and 4 should be moved
    f.addReady(f._ready.bucket(1));
    f.addReady(f._ready.bucket(2));
    f.addReady(f._notReady.bucket(3));
    f.addReady(f._notReady.bucket(4));
    f._bmj.scanAndMove(3, 1); // move 1 doc from bucket 3
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(1u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());
    EXPECT_EQUAL(3u, f.calcAsked().size());
    f.addFrozen(f._notReady.bucket(4));
    f._bmj.scanAndMove(1, 2); // move rest of docs from bucket 3
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(2u, f.docsMoved().size());
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(f._notReady.bucket(3), f.bucketsModified()[0]);
    EXPECT_EQUAL(3u, f.calcAsked().size());
}

TEST_F("require that active bucket is not moved from ready to not ready until being not active", ControllerFixture)
{
    // bucket 1 should be moved but is active
    f.addReady(f._ready.bucket(2));
    f.activateBucket(f._ready.bucket(1));
    f._bmj.scanAndMove(4, 3); // scan all, delay active bucket 1
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());

    f.deactivateBucket(f._ready.bucket(1));
    EXPECT_FALSE(f._bmj.done());
    f._bmj.scanAndMove(0, 3); // move delayed and de-activated bucket 1
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(3u, f.docsMoved().size());
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(f._ready.bucket(1), f.bucketsModified()[0]);
}

TEST_F("require that de-activated bucket is moved before other buckets", OnlyReadyControllerFixture)
{
    // bucket 1, 2, 3 should be moved (but bucket 1 is active)
    f.addReady(f._ready.bucket(4));
    f.activateBucket(f._ready.bucket(1));
    f._bmj.scanAndMove(2, 4); // delay bucket 1, move bucket 2
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(2u, f.docsMoved().size());
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(f._ready.bucket(2), f.bucketsModified()[0]);

    f.deactivateBucket(f._ready.bucket(1));
    f._bmj.scanAndMove(2, 4); // move de-activated bucket 1
    EXPECT_FALSE(f._bmj.done());
    EXPECT_EQUAL(3u, f.docsMoved().size());
    EXPECT_EQUAL(2u, f.bucketsModified().size());
    EXPECT_EQUAL(f._ready.bucket(1), f.bucketsModified()[1]);

    f._bmj.scanAndMove(2, 4); // move bucket 3
    // EXPECT_TRUE(f._bmj.done()); // TODO(geirst): fix this
    EXPECT_EQUAL(6u, f.docsMoved().size());
    EXPECT_EQUAL(3u, f.bucketsModified().size());
    EXPECT_EQUAL(f._ready.bucket(3), f.bucketsModified()[2]);
}

TEST_F("require that de-activated bucket is not moved if new calculator does not say so", ControllerFixture)
{
    // bucket 1 should be moved
    f.addReady(f._ready.bucket(2));
    f.activateBucket(f._ready.bucket(1));
    f._bmj.scanAndMove(4, 3); // scan all, delay active bucket 1
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());

    f.deactivateBucket(f._ready.bucket(1));
    f.addReady(f._ready.bucket(1));
    f.changeCalc();
    f._bmj.scanAndMove(0, 3); // consider delayed bucket 3
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());
    EXPECT_EQUAL(1u, f.calcAsked().size());
    EXPECT_EQUAL(f._ready.bucket(1), f.calcAsked()[0]);
}

TEST_F("require that de-activated bucket is not moved if frozen as well", ControllerFixture)
{
    // bucket 1 should be moved
    f.addReady(f._ready.bucket(2));
    f.activateBucket(f._ready.bucket(1));
    f._bmj.scanAndMove(4, 3); // scan all, delay active bucket 1
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());

    f.addFrozen(f._ready.bucket(1));
    f.deactivateBucket(f._ready.bucket(1));
    f._bmj.scanAndMove(0, 3); // bucket 1 de-activated but frozen
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());

    f.remFrozen(f._ready.bucket(1));
    f._bmj.scanAndMove(0, 3); // handle thawed bucket 1
    EXPECT_EQUAL(3u, f.docsMoved().size());
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(f._ready.bucket(1), f.bucketsModified()[0]);
}

TEST_F("require that thawed bucket is not moved if active as well", ControllerFixture)
{
    // bucket 1 should be moved
    f.addReady(f._ready.bucket(2));
    f.addFrozen(f._ready.bucket(1));
    f._bmj.scanAndMove(4, 3); // scan all, delay frozen bucket 1
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());

    f.activateBucket(f._ready.bucket(1));
    f.remFrozen(f._ready.bucket(1));
    f._bmj.scanAndMove(0, 3); // bucket 1 thawed but active
    EXPECT_EQUAL(0u, f.docsMoved().size());
    EXPECT_EQUAL(0u, f.bucketsModified().size());

    f.deactivateBucket(f._ready.bucket(1));
    f._bmj.scanAndMove(0, 3); // handle de-activated bucket 1
    EXPECT_EQUAL(3u, f.docsMoved().size());
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(f._ready.bucket(1), f.bucketsModified()[0]);
}

TEST_F("ready bucket not moved to not ready if node is marked as retired", ControllerFixture)
{
    f._calc->setNodeRetired(true);
    // Bucket 2 would be moved from ready to not ready in a non-retired case, but not when retired.
    f.addReady(f._ready.bucket(1));
    f._bmj.scanAndMove(4, 3);
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(0u, f.docsMoved().size());
}

// Technically this should never happen since a retired node is never in the ideal state,
// but test this case for the sake of completion.
TEST_F("inactive not ready bucket not moved to ready if node is marked as retired", ControllerFixture)
{
    f._calc->setNodeRetired(true);
    f.addReady(f._ready.bucket(1));
    f.addReady(f._ready.bucket(2));
    f.addReady(f._notReady.bucket(3));
    f._bmj.scanAndMove(4, 3);
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(0u, f.docsMoved().size());
}

TEST_F("explicitly active not ready bucket can be moved to ready even if node is marked as retired", ControllerFixture)
{
    f._calc->setNodeRetired(true);
    f.addReady(f._ready.bucket(1));
    f.addReady(f._ready.bucket(2));
    f.addReady(f._notReady.bucket(3));
    f.activateBucket(f._notReady.bucket(3));
    f._bmj.scanAndMove(4, 3);
    EXPECT_FALSE(f._bmj.done());
    ASSERT_EQUAL(2u, f.docsMoved().size());
    assertEqual(f._notReady.bucket(3), f._notReady.docs(3)[0], 2, 1, f.docsMoved()[0]);
    assertEqual(f._notReady.bucket(3), f._notReady.docs(3)[1], 2, 1, f.docsMoved()[1]);
    ASSERT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(f._notReady.bucket(3), f.bucketsModified()[0]);
}

TEST_F("require that notifyCreateBucket causes bucket to be reconsidered by job", ControllerFixture)
{
    EXPECT_FALSE(f._bmj.done());
    f.addReady(f._ready.bucket(1));
    f.addReady(f._ready.bucket(2));
    f.runLoop();
    EXPECT_TRUE(f._bmj.done());
    EXPECT_TRUE(f.docsMoved().empty());
    EXPECT_TRUE(f.bucketsModified().empty());
    f.addReady(f._notReady.bucket(3)); // bucket 3 now ready, no notify
    EXPECT_TRUE(f._bmj.done());        // move job still believes work done
    f._bmj.notifyCreateBucket(f._notReady.bucket(3)); // reconsider bucket 3
    EXPECT_FALSE(f._bmj.done());
    f.runLoop();
    EXPECT_TRUE(f._bmj.done());
    EXPECT_EQUAL(1u, f.bucketsModified().size());
    EXPECT_EQUAL(2u, f.docsMoved().size());
}

struct ResourceLimitControllerFixture : public ControllerFixture
{
    ResourceLimitControllerFixture(double resourceLimitFactor = RESOURCE_LIMIT_FACTOR) :
        ControllerFixture(BlockableMaintenanceJobConfig(resourceLimitFactor, MAX_OUTSTANDING_OPS))
    {}

    void testJobStopping(DiskMemUsageState blockingUsageState) {
        // Bucket 1 should be moved
        addReady(_ready.bucket(2));
        // Note: This depends on f._bmj.run() moving max 1 documents
        EXPECT_TRUE(!_bmj.run());
        EXPECT_EQUAL(1u, docsMoved().size());
        EXPECT_EQUAL(0u, bucketsModified().size());
        // Notify that we've over limit
        _diskMemUsageNotifier.notify(blockingUsageState);
        EXPECT_TRUE(_bmj.run());
        EXPECT_EQUAL(1u, docsMoved().size());
        EXPECT_EQUAL(0u, bucketsModified().size());
        // Notify that we've under limit
        _diskMemUsageNotifier.notify(DiskMemUsageState());
        EXPECT_TRUE(!_bmj.run());
        EXPECT_EQUAL(2u, docsMoved().size());
        EXPECT_EQUAL(0u, bucketsModified().size());
    }

    void testJobNotStopping(DiskMemUsageState blockingUsageState) {
        // Bucket 1 should be moved
        addReady(_ready.bucket(2));
        // Note: This depends on f._bmj.run() moving max 1 documents
        EXPECT_TRUE(!_bmj.run());
        EXPECT_EQUAL(1u, docsMoved().size());
        EXPECT_EQUAL(0u, bucketsModified().size());
        // Notify that we've over limit, but not over adjusted limit
        _diskMemUsageNotifier.notify(blockingUsageState);
        EXPECT_TRUE(!_bmj.run());
        EXPECT_EQUAL(2u, docsMoved().size());
        EXPECT_EQUAL(0u, bucketsModified().size());
    }
};

TEST_F("require that bucket move stops when disk limit is reached", ResourceLimitControllerFixture)
{
    f.testJobStopping(DiskMemUsageState(ResourceUsageState(0.7, 0.8), ResourceUsageState()));
}

TEST_F("require that bucket move stops when memory limit is reached", ResourceLimitControllerFixture)
{
    f.testJobStopping(DiskMemUsageState(ResourceUsageState(), ResourceUsageState(0.7, 0.8)));
}

TEST_F("require that bucket move uses resource limit factor for disk resource limit", ResourceLimitControllerFixture(1.2))
{
    f.testJobNotStopping(DiskMemUsageState(ResourceUsageState(0.7, 0.8), ResourceUsageState()));
}

TEST_F("require that bucket move uses resource limit factor for memory resource limit", ResourceLimitControllerFixture(1.2))
{
    f.testJobNotStopping(DiskMemUsageState(ResourceUsageState(), ResourceUsageState(0.7, 0.8)));
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
        EXPECT_EQUAL(expDocsMovedCnt, docsMoved().size());
        EXPECT_EQUAL(expMoveContextsCnt, _moveHandler._moveDoneContexts.size());
    }
    void unblockJob(uint32_t expRunnerCnt) {
        _moveHandler.clearMoveDoneContexts(); // unblocks job and try to execute it via runner
        EXPECT_EQUAL(expRunnerCnt, _runner.runCount);
        EXPECT_FALSE(_bmj.isBlocked());
    }

};

TEST_F("require that bucket move job is blocked if it has too many outstanding move operations (max=1)", MaxOutstandingMoveOpsFixture(1))
{
    TEST_DO(f.assertRunToBlocked());
    TEST_DO(f.assertDocsMoved(1, 1));
    TEST_DO(f.assertRunToBlocked());
    TEST_DO(f.assertDocsMoved(1, 1));

    TEST_DO(f.unblockJob(1));
    TEST_DO(f.assertRunToBlocked());
    TEST_DO(f.assertDocsMoved(2, 1));

    TEST_DO(f.unblockJob(2));
    TEST_DO(f.assertRunToBlocked());
    TEST_DO(f.assertDocsMoved(3, 1));

    TEST_DO(f.unblockJob(3));
    TEST_DO(f.assertRunToFinished());
    TEST_DO(f.assertDocsMoved(3, 0));
}

TEST_F("require that bucket move job is blocked if it has too many outstanding move operations (max=2)", MaxOutstandingMoveOpsFixture(2))
{
    TEST_DO(f.assertRunToNotBlocked());
    TEST_DO(f.assertDocsMoved(1, 1));

    TEST_DO(f.assertRunToBlocked());
    TEST_DO(f.assertDocsMoved(2, 2));

    TEST_DO(f.unblockJob(1));
    TEST_DO(f.assertRunToNotBlocked());
    TEST_DO(f.assertDocsMoved(3, 1));

    TEST_DO(f.assertRunToFinished());
    TEST_DO(f.assertDocsMoved(3, 1));
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
