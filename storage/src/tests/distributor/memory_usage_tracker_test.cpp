// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/memory_usage_token.h>
#include <gtest/gtest.h>

namespace storage::distributor {

TEST(MemoryTrackerTest, memory_usage_is_intially_zero) {
    MemoryUsageTracker t;
    EXPECT_EQ(t.bytes_total(), 0);
    EXPECT_EQ(t.max_observed_bytes(), 0);
    auto snap = t.relaxed_snapshot();
    EXPECT_EQ(snap.bytes_total, 0);
    EXPECT_EQ(snap.max_observed_bytes, 0);
}

TEST(MemoryTrackerTest, memory_token_has_scope_semantics) {
    MemoryUsageTracker t;
    {
        MemoryUsageToken t1(t, 1000);
        EXPECT_EQ(t.bytes_total(), 1000);
        EXPECT_EQ(t.max_observed_bytes(), 1000);
        auto snap = t.relaxed_snapshot();
        EXPECT_EQ(snap.bytes_total, 1000);
        EXPECT_EQ(snap.max_observed_bytes, 1000);
        {
            MemoryUsageToken t2(t, 2000);
            EXPECT_EQ(t.bytes_total(), 3000);
            EXPECT_EQ(t.max_observed_bytes(), 3000);
            snap = t.relaxed_snapshot();
            EXPECT_EQ(snap.bytes_total, 3000);
            EXPECT_EQ(snap.max_observed_bytes, 3000);
        }
        EXPECT_EQ(t.bytes_total(), 1000);
        EXPECT_EQ(t.max_observed_bytes(), 3000);
        snap = t.relaxed_snapshot();
        EXPECT_EQ(snap.bytes_total, 1000);
        EXPECT_EQ(snap.max_observed_bytes, 3000);
    }
    EXPECT_EQ(t.bytes_total(), 0);
    EXPECT_EQ(t.max_observed_bytes(), 3000);
    auto snap = t.relaxed_snapshot();
    EXPECT_EQ(snap.bytes_total, 0);
    EXPECT_EQ(snap.max_observed_bytes, 3000);
}

TEST(MemoryTrackerTest, can_change_size_of_token) {
    MemoryUsageTracker t;
    MemoryUsageToken t1(t, 1000);
    t1.update(1500);
    EXPECT_EQ(t.bytes_total(), 1500);
    EXPECT_EQ(t.max_observed_bytes(), 1500);
    t1.update(200);
    EXPECT_EQ(t.bytes_total(), 200);
    EXPECT_EQ(t.max_observed_bytes(), 1500);
}

TEST(MemoryTrackerTest, can_reset_max_observed_bytes) {
    MemoryUsageTracker t;
    {
        MemoryUsageToken t1(t, 1000);
        EXPECT_EQ(t.max_observed_bytes(), 1000);
        t.reset_max_observed_bytes();
        EXPECT_EQ(t.max_observed_bytes(), 0);
        // Special case: we should observe max of 1000 even though we've reset the max
        // to zero since we're currently holding an active token.
        auto snap = t.relaxed_snapshot();
        EXPECT_EQ(snap.bytes_total, 1000);
        EXPECT_EQ(snap.max_observed_bytes, 1000);
    }
    auto snap = t.relaxed_snapshot();
    EXPECT_EQ(snap.bytes_total, 0);
    EXPECT_EQ(snap.max_observed_bytes, 0);
}

} // storage::distributor
