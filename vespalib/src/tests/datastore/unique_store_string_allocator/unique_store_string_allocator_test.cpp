// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/unique_store_string_allocator.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/datastore/buffer_stats.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/test/memory_allocator_observer.h>
#include <vespa/vespalib/util/traits.h>
#include <vector>

using namespace vespalib::datastore;
using vespalib::MemoryUsage;
using generation_t = vespalib::GenerationHandler::generation_t;
using TestBufferStats = vespalib::datastore::test::BufferStats;
using vespalib::alloc::MemoryAllocator;
using vespalib::alloc::test::MemoryAllocatorObserver;
using AllocStats = MemoryAllocatorObserver::Stats;


namespace {

std::string small("small");
std::string middle("middle long string");
std::string spaces1000(1000, ' ');

}

template <typename RefT = EntryRefT<22>>
struct TestBase : public ::testing::Test {
    using EntryRefType = RefT;

    AllocStats stats;
    UniqueStoreStringAllocator<EntryRefType> allocator;
    generation_t generation;
    TestBase()
        : stats(),
          allocator(std::make_unique<MemoryAllocatorObserver>(stats)),
          generation(1)
    {}
    void assert_add(const char *input) {
        EntryRef ref = add(input);
        assert_get(ref, input);
    }
    EntryRef add(const char *input) {
        return allocator.allocate(input);
    }
    void assert_get(EntryRef ref, const char *exp) const {
        const char *act = allocator.get(ref);
        EXPECT_STREQ(exp, act);
    }
    void remove(EntryRef ref) {
        allocator.hold(ref);
    }
    EntryRef move_on_compact(EntryRef ref) {
        return allocator.move_on_compact(ref);
    }
    uint32_t get_buffer_id(EntryRef ref) const {
        return EntryRefType(ref).bufferId();
    }
    const BufferState &buffer_state(EntryRef ref) const {
        return allocator.get_data_store().getBufferState(get_buffer_id(ref));
    }
    void assert_buffer_state(EntryRef ref, const TestBufferStats expStats) const {
        EXPECT_EQ(expStats._used, buffer_state(ref).size());
        EXPECT_EQ(expStats._hold, buffer_state(ref).stats().hold_elems());
        EXPECT_EQ(expStats._dead, buffer_state(ref).stats().dead_elems());
        EXPECT_EQ(expStats._extra_used, buffer_state(ref).stats().extra_used_bytes());
        EXPECT_EQ(expStats._extra_hold, buffer_state(ref).stats().extra_hold_bytes());
    }
    void reclaim_memory() {
        allocator.get_data_store().assign_generation(generation++);
        allocator.get_data_store().reclaim_memory(generation);
    }
};

using StringTest = TestBase<EntryRefT<22>>;
using SmallOffsetStringTest = TestBase<EntryRefT<10, 10>>;

TEST_F(StringTest, can_add_and_get_values)
{
    assert_add(small.c_str());
    assert_add(middle.c_str());
    assert_add(spaces1000.c_str());
}

TEST_F(StringTest, elements_are_put_on_hold_when_value_is_removed)
{
    EntryRef ref = add(small.c_str());
    assert_buffer_state(ref, TestBufferStats().used(16).hold(0).dead(0));
    remove(ref);
    assert_buffer_state(ref, TestBufferStats().used(16).hold(16).dead(0));
    reclaim_memory();
    assert_buffer_state(ref, TestBufferStats().used(16).hold(0).dead(16));
}

TEST_F(StringTest, extra_bytes_used_is_tracked)
{
    EntryRef ref = add(spaces1000.c_str());
    // Note: The first buffer have the first element reserved -> we expect 2 elements used here.
    assert_buffer_state(ref, TestBufferStats().used(2).hold(0).dead(1).extra_used(1001));
    remove(ref);
    assert_buffer_state(ref, TestBufferStats().used(2).hold(1).dead(1).extra_used(1001).extra_hold(1001));
    reclaim_memory();
    assert_buffer_state(ref, TestBufferStats().used(2).hold(0).dead(2));
    ref = add(spaces1000.c_str());
    assert_buffer_state(ref, TestBufferStats().used(2).hold(0).dead(1).extra_used(1001));
    EntryRef ref2 = move_on_compact(ref);
    assert_get(ref2, spaces1000.c_str());
    assert_buffer_state(ref, TestBufferStats().used(3).hold(0).dead(1).extra_used(2002));
    remove(ref);
    remove(ref2);
    assert_buffer_state(ref, TestBufferStats().used(3).hold(2).dead(1).extra_used(2002).extra_hold(2002));
    reclaim_memory();
    assert_buffer_state(ref, TestBufferStats().used(3).hold(0).dead(3));
}

TEST_F(StringTest, string_length_determines_buffer)
{
    EntryRef ref1 = add(small.c_str());
    EntryRef ref2 = add(middle.c_str());
    EntryRef ref3 = add(spaces1000.c_str());
    EXPECT_NE(get_buffer_id(ref1), get_buffer_id(ref2));
    EXPECT_NE(get_buffer_id(ref1), get_buffer_id(ref3));
    EXPECT_NE(get_buffer_id(ref2), get_buffer_id(ref3));
    EntryRef ref4 = add(small.c_str());
    EXPECT_NE(ref1, ref4);
    EXPECT_EQ(get_buffer_id(ref1), get_buffer_id(ref4));
}

TEST_F(StringTest, free_list_is_used_when_enabled)
{
    // Free lists are default enabled for UniqueStoreStringAllocator
    EntryRef ref1 = add(small.c_str());
    EntryRef ref2 = add(spaces1000.c_str());
    remove(ref1);
    remove(ref2);
    reclaim_memory();
    EntryRef ref3 = add(small.c_str());
    EntryRef ref4 = add(spaces1000.c_str());
    EXPECT_EQ(ref1, ref3);
    EXPECT_EQ(ref2, ref4);
    assert_buffer_state(ref1, TestBufferStats().used(16).hold(0).dead(0));
    assert_buffer_state(ref2, TestBufferStats().used(2).hold(0).dead(1).extra_used(1001));
}

TEST_F(StringTest, free_list_is_not_used_when_disabled)
{
    allocator.get_data_store().disableFreeLists();
    EntryRef ref1 = add(small.c_str());
    EntryRef ref2 = add(spaces1000.c_str());
    remove(ref1);
    remove(ref2);
    reclaim_memory();
    EntryRef ref3 = add(small.c_str());
    EntryRef ref4 = add(spaces1000.c_str());
    EXPECT_NE(ref1, ref3);
    EXPECT_NE(ref2, ref4);
    assert_buffer_state(ref1, TestBufferStats().used(32).hold(0).dead(16));
    assert_buffer_state(ref2, TestBufferStats().used(3).hold(0).dead(2).extra_used(1001));
}

TEST_F(StringTest, free_list_is_never_used_for_move_on_compact)
{
    // Free lists are default enabled for UniqueStoreStringAllocator
    EntryRef ref1 = add(small.c_str());
    EntryRef ref2 = add(spaces1000.c_str());
    EntryRef ref3 = add(small.c_str());
    EntryRef ref4 = add(spaces1000.c_str());
    remove(ref3);
    remove(ref4);
    reclaim_memory();
    EntryRef ref5 = move_on_compact(ref1);
    EntryRef ref6 = move_on_compact(ref2);
    EXPECT_NE(ref5, ref3);
    EXPECT_NE(ref6, ref4);
    assert_buffer_state(ref1, TestBufferStats().used(48).hold(0).dead(16));
    assert_buffer_state(ref2, TestBufferStats().used(4).hold(0).dead(2).extra_used(2002));
}

TEST_F(StringTest, provided_memory_allocator_is_used)
{
    EXPECT_EQ(AllocStats(18, 0), stats);
}

TEST_F(SmallOffsetStringTest, new_underlying_buffer_is_allocated_when_current_is_full)
{
    uint32_t first_buffer_id = get_buffer_id(add(small.c_str()));
    for (uint32_t i = 0; i < (SmallOffsetStringTest::EntryRefType::offsetSize() - 1); ++i) {
        uint32_t buffer_id = get_buffer_id(add(small.c_str()));
        EXPECT_EQ(first_buffer_id, buffer_id);
    }

    uint32_t second_buffer_id = get_buffer_id(add(small.c_str()));
    EXPECT_NE(first_buffer_id, second_buffer_id);
    for (uint32_t i = 0; i < 10u; ++i) {
        uint32_t buffer_id = get_buffer_id(add(small.c_str()));
        EXPECT_EQ(second_buffer_id, buffer_id);
    }
    EXPECT_LT(18, stats.alloc_cnt);
}

GTEST_MAIN_RUN_ALL_TESTS()
