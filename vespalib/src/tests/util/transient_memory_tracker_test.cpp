// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/transient_memory_tracker.h>

#include <utility>

using vespalib::TransientMemoryTracker;

class TransientMemoryTrackerTest : public testing::Test {
protected:
    TransientMemoryTracker::TotalTransientMemoryAndGeneration _initial_totals;

    TransientMemoryTrackerTest();
    ~TransientMemoryTrackerTest() override;
    static void set_transient_memory(TransientMemoryTracker& tracker, size_t value);
};

TransientMemoryTrackerTest::TransientMemoryTrackerTest()
    : testing::Test(), _initial_totals(TransientMemoryTracker::get_total_transient_memory()) {
}

TransientMemoryTrackerTest::~TransientMemoryTrackerTest() = default;

void TransientMemoryTrackerTest::set_transient_memory(TransientMemoryTracker& tracker, size_t value) {
    auto lock = tracker.get_lock();
    tracker.set_transient_memory(std::move(lock), value);
}

TEST_F(TransientMemoryTrackerTest, empty_transient_memory) {
    EXPECT_EQ(0, _initial_totals._total_transient_memory);
    TransientMemoryTracker transient_memory_tracker;
    auto                   totals = TransientMemoryTracker::get_total_transient_memory();
    EXPECT_EQ(0, totals._total_transient_memory);
    EXPECT_EQ(_initial_totals._generation, totals._generation);
}

TEST_F(TransientMemoryTrackerTest, normal_transient_memory_lifetime) {
    TransientMemoryTracker transient_memory_tracker;
    set_transient_memory(transient_memory_tracker, 42);
    auto totals = TransientMemoryTracker::get_total_transient_memory();
    EXPECT_EQ(42, totals._total_transient_memory);
    EXPECT_NE(_initial_totals._generation, totals._generation);
    TransientMemoryTracker().swap(transient_memory_tracker);
    auto totals2 = TransientMemoryTracker::get_total_transient_memory();
    EXPECT_EQ(0, totals2._total_transient_memory);
    EXPECT_NE(totals._generation, totals2._generation);
}

TEST_F(TransientMemoryTrackerTest, move_constructor_works) {
    TransientMemoryTracker transient_memory_tracker;
    set_transient_memory(transient_memory_tracker, 42);
    auto                   totals = TransientMemoryTracker::get_total_transient_memory();
    TransientMemoryTracker transient_memory_tracker2(std::move(transient_memory_tracker));
    TransientMemoryTracker().swap(transient_memory_tracker);
    auto totals2 = TransientMemoryTracker::get_total_transient_memory();
    EXPECT_EQ(42, totals2._total_transient_memory);
    EXPECT_EQ(totals._generation, totals2._generation);
    TransientMemoryTracker().swap(transient_memory_tracker2);
    auto totals3 = TransientMemoryTracker::get_total_transient_memory();
    EXPECT_EQ(0, totals3._total_transient_memory);
    EXPECT_NE(totals2._generation, totals3._generation);
}

TEST_F(TransientMemoryTrackerTest, move_assignment_works) {
    TransientMemoryTracker transient_memory_tracker;
    set_transient_memory(transient_memory_tracker, 42);
    auto                   totals = TransientMemoryTracker::get_total_transient_memory();
    TransientMemoryTracker transient_memory_tracker2;
    transient_memory_tracker2 = (std::move(transient_memory_tracker));
    TransientMemoryTracker().swap(transient_memory_tracker);
    auto totals2 = TransientMemoryTracker::get_total_transient_memory();
    EXPECT_EQ(42, totals2._total_transient_memory);
    EXPECT_EQ(totals._generation, totals2._generation);
    TransientMemoryTracker().swap(transient_memory_tracker2);
    auto totals3 = TransientMemoryTracker::get_total_transient_memory();
    EXPECT_EQ(0, totals3._total_transient_memory);
    EXPECT_NE(totals2._generation, totals3._generation);
}

TEST_F(TransientMemoryTrackerTest, swap_works) {
    TransientMemoryTracker transient_memory_tracker;
    set_transient_memory(transient_memory_tracker, 42);
    auto                   totals = TransientMemoryTracker::get_total_transient_memory();
    TransientMemoryTracker transient_memory_tracker2;
    std::swap(transient_memory_tracker, transient_memory_tracker2);
    TransientMemoryTracker().swap(transient_memory_tracker);
    auto totals2 = TransientMemoryTracker::get_total_transient_memory();
    EXPECT_EQ(42, totals2._total_transient_memory);
    EXPECT_EQ(totals._generation, totals2._generation);
    TransientMemoryTracker().swap(transient_memory_tracker2);
    auto totals3 = TransientMemoryTracker::get_total_transient_memory();
    EXPECT_EQ(0, totals3._total_transient_memory);
    EXPECT_NE(totals2._generation, totals3._generation);
}
