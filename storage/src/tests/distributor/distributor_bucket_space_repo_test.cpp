// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributor_bucket_space_repo.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <memory>

namespace storage::distributor {

using document::FixedBucketSpaces;
using namespace ::testing;

struct DistributorBucketSpaceRepoTest : Test {
    DistributorBucketSpaceRepo _repo;

    DistributorBucketSpaceRepoTest() : _repo(123) {}
};

namespace {

lib::ClusterStateBundle bundle_with_global_merges() {
    auto global_state   = std::make_shared<lib::ClusterState>("distributor:1 storage:2");
    auto default_state  = std::make_shared<lib::ClusterState>("distributor:1 storage:2 .1.s:m");
    return lib::ClusterStateBundle(*global_state, {{FixedBucketSpaces::default_space(), default_state},
                                                   {FixedBucketSpaces::global_space(), global_state}});
}

lib::ClusterStateBundle bundle_without_global_merges() {
    auto global_state   = std::make_shared<lib::ClusterState>("distributor:1 storage:2");
    auto default_state  = std::make_shared<lib::ClusterState>("distributor:1 storage:2");
    return lib::ClusterStateBundle(*global_state, {{FixedBucketSpaces::default_space(), default_state},
                                                   {FixedBucketSpaces::global_space(), global_state}});
}

}

TEST_F(DistributorBucketSpaceRepoTest, bucket_spaces_are_initially_not_tagged_as_merge_inhibited) {
    EXPECT_FALSE(_repo.get(FixedBucketSpaces::default_space()).merges_inhibited());
    EXPECT_FALSE(_repo.get(FixedBucketSpaces::global_space()).merges_inhibited());
}

TEST_F(DistributorBucketSpaceRepoTest, enabled_bundle_with_pending_global_merges_tags_default_space_as_merge_inhibited) {
    _repo.enable_cluster_state_bundle(bundle_with_global_merges());
    EXPECT_TRUE(_repo.get(FixedBucketSpaces::default_space()).merges_inhibited());
    EXPECT_FALSE(_repo.get(FixedBucketSpaces::global_space()).merges_inhibited());
}

TEST_F(DistributorBucketSpaceRepoTest, enabled_bundle_without_pending_global_merges_unsets_merge_inhibition) {
    _repo.enable_cluster_state_bundle(bundle_with_global_merges());
    _repo.enable_cluster_state_bundle(bundle_without_global_merges());
    EXPECT_FALSE(_repo.get(FixedBucketSpaces::default_space()).merges_inhibited());
    EXPECT_FALSE(_repo.get(FixedBucketSpaces::global_space()).merges_inhibited());
}

TEST_F(DistributorBucketSpaceRepoTest, pending_bundle_with_pending_global_merges_tags_default_space_as_merge_inhibited) {
    _repo.enable_cluster_state_bundle(bundle_without_global_merges());
    _repo.set_pending_cluster_state_bundle(bundle_with_global_merges());
    EXPECT_TRUE(_repo.get(FixedBucketSpaces::default_space()).merges_inhibited());
    EXPECT_FALSE(_repo.get(FixedBucketSpaces::global_space()).merges_inhibited());
}

TEST_F(DistributorBucketSpaceRepoTest, pending_bundle_without_pending_global_unsets_merge_inhibition) {
    _repo.enable_cluster_state_bundle(bundle_with_global_merges());
    _repo.set_pending_cluster_state_bundle(bundle_without_global_merges());
    EXPECT_FALSE(_repo.get(FixedBucketSpaces::default_space()).merges_inhibited());
    EXPECT_FALSE(_repo.get(FixedBucketSpaces::global_space()).merges_inhibited());
}

}
