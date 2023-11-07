// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/clusterstatehandler.h>
#include <vespa/searchcore/proton/server/iclusterstatechangedhandler.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP("cluster_state_handler_test");

using namespace proton;
using document::BucketId;
using storage::lib::Distribution;
using storage::spi::BucketIdListResult;
using storage::spi::ClusterState;
using storage::spi::Result;

struct MyClusterStateChangedHandler : public IClusterStateChangedHandler {
    std::shared_ptr<IBucketStateCalculator> _calc;
    void
    notifyClusterStateChanged(const std::shared_ptr<IBucketStateCalculator> &newCalc) override {
        _calc = newCalc;
    }
};

BucketId bucket1(1);
BucketId bucket2(2);
BucketId bucket3(3);
Distribution distribution(Distribution::getDefaultDistributionConfig(3, 3));

ClusterState make_cluster_state(const vespalib::string& state, uint16_t node_index, bool maintenance_in_all_spaces = false) {
    return ClusterState(storage::lib::ClusterState(state), node_index, distribution, maintenance_in_all_spaces);
}

ClusterState basic_state = make_cluster_state("distributor:3 storage:3", 0);
ClusterState node_retired_state = make_cluster_state("distributor:3 .1.s:d storage:3 .1.s:r", 1);
ClusterState node_maintenance_state = make_cluster_state("distributor:3 storage:3", 1, true);

struct ClusterStateHandlerTest : testing::Test {
    vespalib::ThreadStackExecutor   _exec;
    ClusterStateHandler             _stateHandler;
    MyClusterStateChangedHandler    _changedHandler;
    test::GenericResultHandler      _genericHandler;
    test::BucketIdListResultHandler _bucketListHandler;
    ClusterStateHandlerTest()
        : _exec(1),
          _stateHandler(_exec),
          _changedHandler(),
          _genericHandler(),
          _bucketListHandler()
    {
        _stateHandler.addClusterStateChangedHandler(&_changedHandler);
    }
    ~ClusterStateHandlerTest() {
        _stateHandler.removeClusterStateChangedHandler(&_changedHandler);
    }
    const IBucketStateCalculator& set_cluster_state(const ClusterState& state) {
        _stateHandler.handleSetClusterState(state, _genericHandler);
        _exec.sync();
        EXPECT_TRUE(_changedHandler._calc);
        return *_changedHandler._calc;
    }
};

TEST_F(ClusterStateHandlerTest, cluster_state_change_is_notified)
{
    const auto& calc = set_cluster_state(basic_state);
    EXPECT_TRUE(calc.clusterUp());
    EXPECT_TRUE(calc.nodeUp());
    EXPECT_FALSE(calc.nodeInitializing());
    EXPECT_FALSE(calc.nodeRetired());
    EXPECT_FALSE(calc.nodeMaintenance());
    EXPECT_FALSE(calc.node_retired_or_maintenance());
}

TEST_F(ClusterStateHandlerTest, node_in_retired_state)
{
    const auto &calc = set_cluster_state(node_retired_state);
    EXPECT_TRUE(calc.nodeRetired());
    EXPECT_FALSE(calc.nodeMaintenance());
    EXPECT_TRUE(calc.node_retired_or_maintenance());
}

TEST_F(ClusterStateHandlerTest, node_in_maintenance_state)
{
    const auto &calc = set_cluster_state(node_maintenance_state);
    EXPECT_FALSE(calc.nodeRetired());
    EXPECT_TRUE(calc.nodeMaintenance());
    EXPECT_TRUE(calc.node_retired_or_maintenance());
}

TEST_F(ClusterStateHandlerTest, modified_buckets_are_returned)
{
    _stateHandler.handleSetClusterState(basic_state, _genericHandler);
    _exec.sync();

    // notify 2 buckets
    IBucketModifiedHandler &bmh = _stateHandler;
    bmh.notifyBucketModified(bucket1);
    bmh.notifyBucketModified(bucket2);
    _stateHandler.handleGetModifiedBuckets(_bucketListHandler);
    _exec.sync();
    EXPECT_EQ(2u, _bucketListHandler.getList().size());
    EXPECT_EQ(bucket1, _bucketListHandler.getList()[0]);
    EXPECT_EQ(bucket2, _bucketListHandler.getList()[1]);

    // notify 1 bucket, already reported buckets should be gone
    bmh.notifyBucketModified(bucket3);
    _stateHandler.handleGetModifiedBuckets(_bucketListHandler);
    _exec.sync();
    EXPECT_EQ(1u, _bucketListHandler.getList().size());
    EXPECT_EQ(bucket3, _bucketListHandler.getList()[0]);
}

GTEST_MAIN_RUN_ALL_TESTS()

