// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::BucketSpace;

namespace storage::lib {

using ClusterStatePtr = std::shared_ptr<const ClusterState>;

struct Fixture {
    ClusterState baselineState;
    ClusterStatePtr derivedState;
    ClusterStateBundle bundle;
    Fixture()
        : baselineState("storage:2"),
          derivedState(std::make_shared<const ClusterState>("storage:2 .1.s:m")),
          bundle(baselineState, {{BucketSpace(1), derivedState}})
    {}
    ~Fixture() {}
};

TEST(ClusterStateBundleTest, derived_state_is_returned_if_bucket_space_is_found)
{
    Fixture f;
    EXPECT_EQ(*f.derivedState, *f.bundle.getDerivedClusterState(BucketSpace(1)));
}

TEST(ClusterStateBundleTest, baseline_state_is_returned_if_bucket_space_is_not_found)
{
    Fixture f;
    EXPECT_EQ(f.baselineState, *f.bundle.getDerivedClusterState(BucketSpace(2)));
}

ClusterStateBundle
makeBundle(const vespalib::string &baselineState, const std::map<BucketSpace, vespalib::string> &derivedStates,
           bool deferred_activation = false)
{
    ClusterStateBundle::BucketSpaceStateMapping derivedBucketSpaceStates;
    for (const auto &entry : derivedStates) {
        derivedBucketSpaceStates[entry.first] = std::make_shared<const ClusterState>(entry.second);
    }
    return ClusterStateBundle(ClusterState(baselineState), std::move(derivedBucketSpaceStates), deferred_activation);
}

ClusterStateBundle
bundle_with_feed_block(const ClusterStateBundle::FeedBlock& feed_block)
{
    return ClusterStateBundle(ClusterState("storage:2"), {}, feed_block, false);
}

TEST(ClusterStateBundleTest, verify_equality_operator)
{
    Fixture f;
    EXPECT_NE(f.bundle, makeBundle("storage:3", {{BucketSpace(1), "storage:2 .1.s:m"}}));
    EXPECT_NE(f.bundle, makeBundle("storage:2", {}));
    EXPECT_NE(f.bundle, makeBundle("storage:2", {{BucketSpace(1), "storage:2 .0.s:m"}}));
    EXPECT_NE(f.bundle, makeBundle("storage:2", {{BucketSpace(2), "storage:2 .1.s:m"}}));
    EXPECT_NE(f.bundle, makeBundle("storage:2", {{BucketSpace(1), "storage:2 .1.s:m"}}, true));

    EXPECT_EQ(f.bundle, makeBundle("storage:2", {{BucketSpace(1), "storage:2 .1.s:m"}}));
}

TEST(ClusterStateBundleTest, feed_block_state_is_available)
{
    auto non_blocking = makeBundle("storage:2", {});
    auto blocking = bundle_with_feed_block({true, "foo"});

    EXPECT_FALSE(non_blocking.block_feed_in_cluster());
    EXPECT_FALSE(non_blocking.feed_block().has_value());

    EXPECT_TRUE(blocking.block_feed_in_cluster());
    EXPECT_TRUE(blocking.feed_block().has_value());
    EXPECT_TRUE(blocking.feed_block()->block_feed_in_cluster());
    EXPECT_EQ("foo", blocking.feed_block()->description());
}

TEST(ClusterStateBundleTest, equality_operator_considers_feed_block)
{
    EXPECT_NE(bundle_with_feed_block({true, "foo"}), bundle_with_feed_block({false, "foo"}));
    EXPECT_NE(bundle_with_feed_block({true, "foo"}), bundle_with_feed_block({true, "bar"}));
    EXPECT_NE(makeBundle("storage:2", {}), bundle_with_feed_block({false, "bar"}));

    EXPECT_EQ(bundle_with_feed_block({true, "foo"}), bundle_with_feed_block({true, "foo"}));
    EXPECT_EQ(bundle_with_feed_block({false, "foo"}), bundle_with_feed_block({false, "foo"}));
}

TEST(ClusterStateBundleTest, toString_with_feed_block_includes_description)
{
    EXPECT_EQ("ClusterStateBundle('storage:2', feed blocked: 'full disk')",
              bundle_with_feed_block({true, "full disk"}).toString());
}

}

