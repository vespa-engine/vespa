// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/bucket_db_prune_elision.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <gtest/gtest.h>

using namespace ::testing;

namespace storage::distributor {

namespace {

lib::ClusterState state_of(const char* str) {
    return lib::ClusterState(str);
}

}

TEST(IdempotentStateTransitionTest, state_differing_only_in_version_allows_elision) {
    EXPECT_TRUE(db_pruning_may_be_elided(state_of("version:1 bits:8 distributor:3 storage:3"),
                                         state_of("version:2 bits:8 distributor:3 storage:3")));
}

TEST(IdempotentStateTransitionTest, differing_cluster_state_disallows_elision) {
    EXPECT_FALSE(db_pruning_may_be_elided(state_of("cluster:d distributor:3 storage:3"),
                                          state_of("distributor:3 storage:3")));
    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:3 storage:3"),
                                          state_of("cluster:d distributor:3 storage:3")));
}

TEST(IdempotentStateTransitionTest, differing_distribution_bit_count_disallows_elision) {
    EXPECT_FALSE(db_pruning_may_be_elided(state_of("bits:8 distributor:3 storage:3"),
                                          state_of("bits:9 distributor:3 storage:3")));
    // No explicit "bits:" implies 16 bits
    EXPECT_FALSE(db_pruning_may_be_elided(state_of("bits:8 distributor:3 storage:3"),
                                          state_of("distributor:3 storage:3")));
    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:3 storage:3"),
                                          state_of("bits:8 distributor:3 storage:3")));
}

TEST(IdempotentStateTransitionTest, same_implicit_distribution_bit_count_allows_elision) {
    EXPECT_TRUE(db_pruning_may_be_elided(state_of("distributor:3 storage:3"),
                                         state_of("bits:16 distributor:3 storage:3")));
}

TEST(IdempotentStateTransitionTest, changed_distributor_node_count_disallows_elision) {
    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:3 storage:3"),
                                          state_of("distributor:4 storage:3")));
    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:4 storage:3"),
                                          state_of("distributor:3 storage:3")));
}

TEST(IdempotentStateTransitionTest, changed_distributor_node_state_disallows_elision) {
    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:3 .0.s:d storage:3"),
                                          state_of("distributor:3 storage:3")));

    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:3 storage:3"),
                                          state_of("distributor:3 .0.s:d storage:3")));

    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:3 .0.s:d storage:3"),
                                          state_of("distributor:3 .0.s:u storage:3")));

    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:3 .0.s:d storage:3"),
                                          state_of("distributor:3 .1.s:d storage:3")));
}

TEST(IdempotentStateTransitionTest, changed_storage_node_count_disallows_elision) {
    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:3 storage:3"),
                                          state_of("distributor:3 storage:4")));
    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:3 storage:4"),
                                          state_of("distributor:3 storage:3")));
}

TEST(IdempotentStateTransitionTest, changed_storage_node_state_disallows_elision) {
    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:3 storage:3 .0.s:d"),
                                          state_of("distributor:3 storage:3")));

    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:3 storage:3"),
                                          state_of("distributor:3 storage:3 .0.s:d")));

    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:3 storage:3 .0.s:d"),
                                          state_of("distributor:3 storage:3 .0.s:u")));

    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:3 storage:3 .0.s:d"),
                                          state_of("distributor:3 storage:3 .1.s:d")));

    EXPECT_FALSE(db_pruning_may_be_elided(state_of("distributor:3 storage:3 .0.s:r"),
                                          state_of("distributor:3 storage:3 .0.s:d")));
}

TEST(IdempotentStateTransitionTest, may_elide_for_transition_between_different_effective_storage_down_states) {
    // Maintenance -> Down edge shall already have pruned DB on Maintenance edge
    EXPECT_TRUE(db_pruning_may_be_elided(state_of("distributor:3 storage:3 .0.s:m"),
                                         state_of("distributor:3 storage:3 .0.s:d")));

    // Down -> Maintenance edge shall already have pruned DB on Down edge
    EXPECT_TRUE(db_pruning_may_be_elided(state_of("distributor:3 storage:3 .0.s:d"),
                                         state_of("distributor:3 storage:3 .0.s:m")));
}

TEST(IdempotentStateTransitionTest, may_elide_for_transition_between_different_effective_storage_up_states) {
    EXPECT_TRUE(db_pruning_may_be_elided(state_of("distributor:3 storage:3 .0.s:i"),
                                         state_of("distributor:3 storage:3")));

    EXPECT_TRUE(db_pruning_may_be_elided(state_of("distributor:3 storage:3 .1.s:r"),
                                         state_of("distributor:3 storage:3")));
    EXPECT_TRUE(db_pruning_may_be_elided(state_of("distributor:3 storage:3"),
                                         state_of("distributor:3 storage:3 .2.s:r")));
}

// Changed startup timestamps imply that bucket info should be fetched from a node, but
// does not imply that pruning is required.
TEST(IdempotentStateTransitionTest, may_elide_changed_startup_timestamps) {
    EXPECT_TRUE(db_pruning_may_be_elided(state_of("distributor:3 storage:3"),
                                         state_of("distributor:3 storage:3 .0.t:123456")));
    EXPECT_TRUE(db_pruning_may_be_elided(state_of("distributor:3 storage:3 .0.t:123456"),
                                         state_of("distributor:3 storage:3")));
    EXPECT_TRUE(db_pruning_may_be_elided(state_of("distributor:3 storage:3 .0.t:123456"),
                                         state_of("distributor:3 storage:3 .0.t:654321")));
}

}
