// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/transient_memory_tracker.h>

#include <utility>

using vespalib::TransientMemoryTracker;

class TransientMemoryTrackerTest : public testing::Test {
protected:
    size_t _initial_total_transient_memory;

    TransientMemoryTrackerTest();
    ~TransientMemoryTrackerTest() override;
    static void set_transient_memory(TransientMemoryTracker& tracker, size_t value);
    static size_t get_total_transient_memory() noexcept;
};

TransientMemoryTrackerTest::TransientMemoryTrackerTest()
    : testing::Test(), _initial_total_transient_memory(get_total_transient_memory()) {
}

TransientMemoryTrackerTest::~TransientMemoryTrackerTest() = default;

void TransientMemoryTrackerTest::set_transient_memory(TransientMemoryTracker& tracker, size_t value) {
    auto lock = tracker.acquire_lock();
    tracker.set_transient_memory(std::move(lock), value);
}

size_t TransientMemoryTrackerTest::get_total_transient_memory() noexcept {
    auto lock = TransientMemoryTracker::acquire_lock();
    return TransientMemoryTracker::get_total_transient_memory(std::move(lock));
}

TEST_F(TransientMemoryTrackerTest, empty_transient_memory) {
    EXPECT_EQ(0, _initial_total_transient_memory);
    TransientMemoryTracker transient_memory_tracker;
    EXPECT_EQ(0, get_total_transient_memory());
}

TEST_F(TransientMemoryTrackerTest, normal_transient_memory_lifetime) {
    TransientMemoryTracker transient_memory_tracker;
    set_transient_memory(transient_memory_tracker, 42);
    EXPECT_EQ(42, get_total_transient_memory());
    TransientMemoryTracker().swap(transient_memory_tracker);
    EXPECT_EQ(0, get_total_transient_memory());
}

TEST_F(TransientMemoryTrackerTest, move_constructor_works) {
    TransientMemoryTracker transient_memory_tracker;
    set_transient_memory(transient_memory_tracker, 42);
    EXPECT_EQ(42, get_total_transient_memory());
    TransientMemoryTracker transient_memory_tracker2(std::move(transient_memory_tracker));
    TransientMemoryTracker().swap(transient_memory_tracker);
    EXPECT_EQ(42, get_total_transient_memory());
    TransientMemoryTracker().swap(transient_memory_tracker2);
    EXPECT_EQ(0, get_total_transient_memory());
}

TEST_F(TransientMemoryTrackerTest, move_assignment_works) {
    TransientMemoryTracker transient_memory_tracker;
    set_transient_memory(transient_memory_tracker, 42);
    EXPECT_EQ(42, get_total_transient_memory());
    TransientMemoryTracker transient_memory_tracker2;
    transient_memory_tracker2 = (std::move(transient_memory_tracker));
    TransientMemoryTracker().swap(transient_memory_tracker);
    EXPECT_EQ(42, get_total_transient_memory());
    TransientMemoryTracker().swap(transient_memory_tracker2);
    EXPECT_EQ(0, get_total_transient_memory());
}

TEST_F(TransientMemoryTrackerTest, swap_works) {
    TransientMemoryTracker transient_memory_tracker;
    set_transient_memory(transient_memory_tracker, 42);
    EXPECT_EQ(42, get_total_transient_memory());
    TransientMemoryTracker transient_memory_tracker2;
    std::swap(transient_memory_tracker, transient_memory_tracker2);
    TransientMemoryTracker().swap(transient_memory_tracker);
    EXPECT_EQ(42, get_total_transient_memory());
    TransientMemoryTracker().swap(transient_memory_tracker2);
    EXPECT_EQ(0, get_total_transient_memory());
}

TEST_F(TransientMemoryTrackerTest, adjust_transient_memory) {
    TransientMemoryTracker transient_memory_tracker1;
    TransientMemoryTracker transient_memory_tracker2;
    set_transient_memory(transient_memory_tracker1, 200);
    set_transient_memory(transient_memory_tracker2, 2000);
    EXPECT_EQ(2200, get_total_transient_memory());
    set_transient_memory(transient_memory_tracker1, 100);
    EXPECT_EQ(2100, get_total_transient_memory());
    set_transient_memory(transient_memory_tracker2, 3000);
    EXPECT_EQ(3100, get_total_transient_memory());
}
