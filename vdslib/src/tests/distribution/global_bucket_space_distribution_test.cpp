// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/distribution/global_bucket_space_distribution_converter.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/config-stor-distribution.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>
#include <span>

using namespace ::testing;
using document::BucketId;

namespace storage::lib {

using DistributionConfig = vespa::config::content::StorDistributionConfig;

namespace {

std::shared_ptr<const lib::Distribution> default_to_global(const std::string& default_config) {
    auto default_cfg = GlobalBucketSpaceDistributionConverter::string_to_config(default_config);
    return GlobalBucketSpaceDistributionConverter::convert_to_global(lib::Distribution(*default_cfg));
}

void verify_ideal_nodes(const Distribution& distr, const ClusterState& state, std::span<const uint16_t> expected_nodes) {
    // Since ideal state node assignment is pseudo-random, check for "enough" distinct bucket
    // IDs that it's extremely unlikely that we only test values for which we accidentally
    // compute the correct output.
    for (uint16_t i = 0; i < 100; ++i) {
        BucketId bucket(16, i);
        std::vector<uint16_t> nodes = distr.getIdealStorageNodes(state, bucket);
        ASSERT_THAT(nodes, UnorderedElementsAreArray(expected_nodes)) << bucket;
    }
}

}

TEST(GlobalBucketSpaceDistributionTest, flat_global_distribution_includes_all_available_storage_nodes) {
    // topology: {0, 1, 2}
    std::string default_flat_config(
R"(redundancy 1
group[1]
group[0].name "invalid"
group[0].index "invalid"
group[0].partitions 1|*
group[0].nodes[3]
group[0].nodes[0].index 0
group[0].nodes[1].index 1
group[0].nodes[2].index 2
)");
    auto gd = default_to_global(default_flat_config);
    EXPECT_TRUE(gd->is_global());
    EXPECT_EQ(gd->getRedundancy(), 3);
    EXPECT_EQ(gd->getReadyCopies(), 3);
    EXPECT_TRUE(gd->activePerGroup());
    EXPECT_TRUE(gd->ensurePrimaryPersisted());
    EXPECT_EQ(gd->getInitialRedundancy(), 0);

    verify_ideal_nodes(*gd, ClusterState("distributor:3 storage:3"), {{0, 1, 2}});
    verify_ideal_nodes(*gd, ClusterState("distributor:3 storage:3 .1.s:d"), {{0, 2}});
}

TEST(GlobalBucketSpaceDistributionTest, single_level_multi_group_config_includes_nodes_across_all_groups) {
    // topology: {{0, 1, 2}, {3, 4, 5}}
    std::string default_config(
R"(redundancy 2
group[3]
group[0].name "invalid"
group[0].index "invalid"
group[0].partitions 1|*
group[0].nodes[0]
group[1].name rack0
group[1].index 0
group[1].nodes[3]
group[1].nodes[0].index 0
group[1].nodes[1].index 1
group[1].nodes[2].index 2
group[2].name rack1
group[2].index 1
group[2].nodes[3]
group[2].nodes[0].index 3
group[2].nodes[1].index 4
group[2].nodes[2].index 5
)");

    auto gd = default_to_global(default_config);
    EXPECT_TRUE(gd->is_global());
    EXPECT_EQ(gd->getRedundancy(), 6);
    EXPECT_EQ(gd->getReadyCopies(), 6);

    verify_ideal_nodes(*gd, ClusterState("distributor:6 storage:6"), {{0, 1, 2, 3, 4, 5}});
    verify_ideal_nodes(*gd, ClusterState("distributor:6 storage:6 .1.s:d .5.s:d"), {{0, 2, 3, 4}});
}

TEST(GlobalBucketSpaceDistributionTest, multi_level_multi_group_config_includes_nodes_across_all_groups) {
    // topology: {{{0}, {1}}, {{2}, {3}}}
    std::string default_config(
R"(redundancy 2
group[5]
group[0].name "invalid"
group[0].index "invalid"
group[0].partitions *|*
group[0].nodes[0]
group[1].name switch0
group[1].index 0
group[1].partitions 1|*
group[1].nodes[0]
group[2].name rack0
group[2].index 0.0
group[2].nodes[1]
group[2].nodes[0].index 0
group[3].name rack1
group[3].index 0.1
group[3].nodes[1]
group[3].nodes[0].index 1
group[4].name switch1
group[4].index 1
group[4].partitions *
group[4].nodes[0]
group[5].name rack0
group[5].index 1.0
group[5].nodes[1]
group[5].nodes[0].index 2
group[6].name rack1
group[6].index 1.1
group[6].nodes[1]
group[6].nodes[0].index 3
)");
    auto gd = default_to_global(default_config);
    EXPECT_TRUE(gd->is_global());
    EXPECT_EQ(gd->getRedundancy(), 4);
    EXPECT_EQ(gd->getReadyCopies(), 4);

    verify_ideal_nodes(*gd, ClusterState("distributor:4 storage:4"), {{0, 1, 2, 3}});
    verify_ideal_nodes(*gd, ClusterState("distributor:4 storage:4 .2.s:d"), {{0, 1, 3}});
}

TEST(GlobalBucketSpaceDistributionTest, global_distribution_handles_hetereogenous_nested_topology) {
    // topology: {{0, 1}, {2}}
    std::string default_config(
R"(redundancy 2
ready_copies 2
group[3]
group[0].name "invalid"
group[0].index "invalid"
group[0].partitions "1|*"
group[0].nodes[0]
group[1].name rack0
group[1].index 0
group[1].nodes[2]
group[1].nodes[0].index 0
group[1].nodes[1].index 1
group[2].name rack1
group[2].index 1
group[2].nodes[1]
group[2].nodes[1].index 2
)");
    auto gd = default_to_global(default_config);
    EXPECT_EQ(gd->getReadyCopies(), 3);
    EXPECT_EQ(gd->getReadyCopies(), 3);

    verify_ideal_nodes(*gd, ClusterState("distributor:3 storage:3"), {{0, 1, 2}});
    verify_ideal_nodes(*gd, ClusterState("distributor:3 storage:3 .0.s:d"), {{1, 2}});
    verify_ideal_nodes(*gd, ClusterState("distributor:3 storage:3 .2.s:d"), {{0, 1}});
}

TEST(GlobalBucketSpaceDistributionTest, global_distribution_has_same_owner_distributors_as_default) {
    // topology: {{0}, {1, 2}}
    std::string default_config(
R"(redundancy 2
ready_copies 2
group[3]
group[0].name "invalid"
group[0].index "invalid"
group[0].partitions 1|*
group[0].nodes[0]
group[1].name rack0
group[1].index 0
group[1].nodes[1]
group[1].nodes[0].index 0
group[2].name rack1
group[2].index 1
group[2].nodes[2]
group[2].nodes[0].index 1
group[2].nodes[1].index 2
)");

    auto default_cfg = GlobalBucketSpaceDistributionConverter::string_to_config(default_config);
    auto global_distr = default_to_global(default_config);

    lib::Distribution default_distr(*default_cfg);
    lib::ClusterState state("distributor:6 storage:6");

    for (unsigned int i = 0; i < UINT16_MAX; ++i) {
        document::BucketId bucket(16, i);
        const auto default_index = default_distr.getIdealDistributorNode(state, bucket, "ui");
        const auto global_index = global_distr->getIdealDistributorNode(state, bucket, "ui");
        ASSERT_EQ(default_index, global_index) << bucket;
    }
}

}
