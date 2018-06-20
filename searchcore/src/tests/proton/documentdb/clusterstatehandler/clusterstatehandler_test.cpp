// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/searchcore/proton/server/clusterstatehandler.h>
#include <vespa/searchcore/proton/server/iclusterstatechangedhandler.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>

#include <vespa/log/log.h>
LOG_SETUP("cluster_state_handler_test");

using namespace proton;
using document::BucketId;
using storage::lib::Distribution;
using storage::spi::BucketIdListResult;
using storage::spi::ClusterState;
using storage::spi::Result;

struct MyClusterStateChangedHandler : public IClusterStateChangedHandler
{
    IBucketStateCalculator::SP _calc;
    virtual void
    notifyClusterStateChanged(const IBucketStateCalculator::SP &newCalc) override {
        _calc = newCalc;
    }
};


BucketId bucket1(1);
BucketId bucket2(2);
BucketId bucket3(3);
Distribution distribution(Distribution::getDefaultDistributionConfig(3, 3));
storage::lib::ClusterState rawClusterState("version:1 storage:3 distributor:3");
ClusterState clusterState(rawClusterState, 0, distribution);


struct Fixture
{
    vespalib::ThreadStackExecutor   _exec;
    ClusterStateHandler             _stateHandler;
    MyClusterStateChangedHandler    _changedHandler;
    test::GenericResultHandler      _genericHandler;
    test::BucketIdListResultHandler _bucketListHandler;
    Fixture()
        : _exec(1, 64000),
          _stateHandler(_exec),
          _changedHandler(),
          _genericHandler(),
          _bucketListHandler()
    {
        _stateHandler.addClusterStateChangedHandler(&_changedHandler);
    }
    ~Fixture()
    {
        _stateHandler.removeClusterStateChangedHandler(&_changedHandler);
    }
};


TEST_F("require that cluster state change is notified", Fixture)
{
    f._stateHandler.handleSetClusterState(clusterState, f._genericHandler);
    f._exec.sync();
    EXPECT_TRUE(f._changedHandler._calc.get() != NULL);
}


TEST_F("require that modified buckets are returned", Fixture)
{
    f._stateHandler.handleSetClusterState(clusterState, f._genericHandler);
    f._exec.sync();

    // notify 2 buckets
    IBucketModifiedHandler &bmh = f._stateHandler;
    bmh.notifyBucketModified(bucket1);
    bmh.notifyBucketModified(bucket2);
    f._stateHandler.handleGetModifiedBuckets(f._bucketListHandler);
    f._exec.sync();
    EXPECT_EQUAL(2u, f._bucketListHandler.getList().size());
    EXPECT_EQUAL(bucket1, f._bucketListHandler.getList()[0]);
    EXPECT_EQUAL(bucket2, f._bucketListHandler.getList()[1]);

    // notify 1 bucket, already reported buckets should be gone
    bmh.notifyBucketModified(bucket3);
    f._stateHandler.handleGetModifiedBuckets(f._bucketListHandler);
    f._exec.sync();
    EXPECT_EQUAL(1u, f._bucketListHandler.getList().size());
    EXPECT_EQUAL(bucket3, f._bucketListHandler.getList()[0]);
}


TEST_MAIN()
{
    TEST_RUN_ALL();
}

