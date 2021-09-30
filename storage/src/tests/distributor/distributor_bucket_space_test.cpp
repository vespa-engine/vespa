// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributor_bucket_space_repo.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::BucketId;
using storage::lib::ClusterState;
using storage::lib::Distribution;

namespace storage::distributor {

namespace {

std::shared_ptr<ClusterState> stable_state(std::make_shared<ClusterState>("distributor:4 storage:4 bits:8"));
std::shared_ptr<ClusterState> node_1_down_state(std::make_shared<ClusterState>("distributor:4 .1.s:d storage:4 .1.s:d bits:8"));
std::shared_ptr<ClusterState> node_1_retired_state(std::make_shared<ClusterState>("distributor:4 .1.s:d storage:4 .1.s:r bits:8"));
std::shared_ptr<ClusterState> node_1_maintenance_state(std::make_shared<ClusterState>("distributor:4 .1.s:d storage:4 .1.s:m bits:8"));
std::shared_ptr<Distribution> distribution_r1(std::make_shared<Distribution>(Distribution::getDefaultDistributionConfig(1, 4)));
std::shared_ptr<Distribution> distribution_r2(std::make_shared<Distribution>(Distribution::getDefaultDistributionConfig(2, 4)));

}

struct DistributorBucketSpaceTest : public ::testing::Test
{
    using CountVector = std::vector<uint32_t>;

    DistributorBucketSpace bucket_space;

    DistributorBucketSpaceTest()
        : ::testing::Test(),
          bucket_space(0u)
    {
    }
    ~DistributorBucketSpaceTest() = default;

    // make normal buckets
    std::vector<BucketId> make_normal_buckets();

    // make deep split buckets. Ideal service layer nodes for a bucket changes for each split level when bucket used bits > 33.
    std::vector<BucketId> make_deep_split_buckets(std::function<bool(BucketId)> owned);

    // Count normal buckets using this distributor
    uint32_t count_distributor_buckets(const std::vector<BucketId>& buckets);
    // Count normal buckets using service layer node 0.
    CountVector count_service_layer_buckets(const std::vector<BucketId>& buckets);
    // Count normal buckets using this distributor and service layer node 0
    CountVector count_buckets();
    // Count deep split buckets using this distributor and service layer node 0.
    CountVector count_deep_split_buckets();
};

std::vector<BucketId>
DistributorBucketSpaceTest::make_normal_buckets()
{
    std::vector<BucketId> buckets;
    uint16_t distribution_bits = bucket_space.getClusterState().getDistributionBitCount();
    for (uint32_t i = 0; i < (1u << distribution_bits); ++i) {
        buckets.emplace_back(distribution_bits, i);
    }
    return buckets;
}

std::vector<BucketId>
DistributorBucketSpaceTest::make_deep_split_buckets(std::function<bool(BucketId)> owned)
{
    std::vector<BucketId> buckets;
    uint16_t distribution_bits = bucket_space.getClusterState().getDistributionBitCount();
    uint32_t bias = 0;
    uint32_t bias_max = std::min(1u << distribution_bits, 1000u);
    for (; bias < bias_max; ++bias) {
        BucketId bucket(distribution_bits, bias);
        if (owned(bucket)) {
            break;
        }
    }
    assert(bias < bias_max);
    for (uint32_t i = 0; i < 100; ++i) {
        buckets.emplace_back(42u, i * (1ul << 32) + bias);
    }
    return buckets;
}

uint32_t
DistributorBucketSpaceTest::count_distributor_buckets(const std::vector<BucketId>& buckets)
{
    uint32_t owned_buckets = 0;
    for (auto& bucket : buckets) {
        bool owned = bucket_space.check_ownership_in_pending_and_current_state(bucket).isOwned();
        if (owned) {
            ++owned_buckets;
        }
    }
    return owned_buckets;
}

DistributorBucketSpaceTest::CountVector
DistributorBucketSpaceTest::count_service_layer_buckets(const std::vector<BucketId>& buckets)
{
    CountVector result(3);
    std::vector<uint16_t> ideal_nodes;
    for (auto& bucket : buckets) {
        auto &ideal_nodes_bundle = bucket_space.get_ideal_service_layer_nodes_bundle(bucket);
        for (uint32_t i = 0; i < 3; ++i) {
            switch (i) {
            case 0:
                ideal_nodes = ideal_nodes_bundle.get_available_nodes();
                break;
            case 1:
                ideal_nodes = ideal_nodes_bundle.get_available_nonretired_nodes();
                break;
            case 2:
                ideal_nodes = ideal_nodes_bundle.get_available_nonretired_or_maintenance_nodes();
                break;
            default:
                ;
            }
            for (auto node : ideal_nodes) {
                if (node == 0u) {
                    ++result[i];
                }
            }
        }
    }
    return result;
}

DistributorBucketSpaceTest::CountVector
DistributorBucketSpaceTest::count_buckets()
{
    CountVector result;
    auto buckets = make_normal_buckets();
    result.push_back(count_distributor_buckets(buckets));
    auto service_layer_result = count_service_layer_buckets(buckets);
    result.insert(result.end(), service_layer_result.cbegin(), service_layer_result.cend());
    return result;
}

DistributorBucketSpaceTest::CountVector
DistributorBucketSpaceTest::count_deep_split_buckets()
{
    CountVector result;
    auto buckets = make_deep_split_buckets([this](BucketId bucket) { return bucket_space.check_ownership_in_pending_and_current_state(bucket).isOwned(); });
    result.push_back(count_distributor_buckets(buckets));
    auto service_layer_result = count_service_layer_buckets(buckets);
    result.insert(result.end(), service_layer_result.cbegin(), service_layer_result.cend());
    return result;
}

TEST_F(DistributorBucketSpaceTest, check_owned_buckets)
{
    bucket_space.setDistribution(distribution_r1);
    bucket_space.setClusterState(stable_state);
    EXPECT_EQ((CountVector{64u, 64u, 64u, 64u}), count_buckets());
    bucket_space.set_pending_cluster_state(node_1_down_state);
    EXPECT_EQ((CountVector{64u, 64u, 64u, 64u}), count_buckets());
    bucket_space.setClusterState(node_1_down_state);
    bucket_space.set_pending_cluster_state({});
    EXPECT_EQ((CountVector{86u, 86u, 86u, 86u}), count_buckets());
    bucket_space.set_pending_cluster_state(stable_state);
    EXPECT_EQ((CountVector{64u, 86u, 86u, 86u}), count_buckets());
    bucket_space.setClusterState(stable_state);
    bucket_space.set_pending_cluster_state({});
    EXPECT_EQ((CountVector{64u, 64u, 64u, 64u}), count_buckets());
    bucket_space.setDistribution(distribution_r2);
    EXPECT_EQ((CountVector{64u, 125u, 125u, 125u}), count_buckets());
    bucket_space.setClusterState(node_1_maintenance_state);
    bucket_space.setDistribution(distribution_r1);
    EXPECT_EQ((CountVector{86u, 86u, 86u, 64u}), count_buckets());
    bucket_space.setClusterState(node_1_retired_state);
    EXPECT_EQ((CountVector{86u, 64u, 86u, 86u}), count_buckets());
}

TEST_F(DistributorBucketSpaceTest, check_available_nodes)
{
    bucket_space.setDistribution(distribution_r1);
    bucket_space.setClusterState(stable_state);
    EXPECT_EQ((std::vector<bool>{true, true, true, true}), bucket_space.get_available_nodes());
    bucket_space.set_pending_cluster_state(node_1_down_state);
    EXPECT_EQ((std::vector<bool>{true, false, true, true}), bucket_space.get_available_nodes());
    bucket_space.setClusterState(node_1_down_state);
    bucket_space.set_pending_cluster_state({});
    EXPECT_EQ((std::vector<bool>{true, false, true, true}), bucket_space.get_available_nodes());
    bucket_space.set_pending_cluster_state(stable_state);
    EXPECT_EQ((std::vector<bool>{true, false, true, true}), bucket_space.get_available_nodes());
    bucket_space.setClusterState(stable_state);
    bucket_space.set_pending_cluster_state({});
    EXPECT_EQ((std::vector<bool>{true, true, true, true}), bucket_space.get_available_nodes());
}

TEST_F(DistributorBucketSpaceTest, check_owned_deep_split_buckets)
{
    bucket_space.setDistribution(distribution_r1);
    bucket_space.setClusterState(stable_state);
    EXPECT_EQ((CountVector{100u, 19u, 19u, 19u}), count_deep_split_buckets());
}

}
