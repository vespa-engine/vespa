// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/vespalib/datastore/compaction_spec.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/test/datastore/buffer_stats.h>
#include <vespa/vespalib/test/datastore/memstats.h>
#include <vespa/vespalib/test/memory_allocator_observer.h>
#include <vespa/vespalib/util/memory_allocator.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vector>

using namespace vespalib::datastore;
using generation_t = vespalib::GenerationHandler::generation_t;
using vespalib::ArrayRef;
using vespalib::MemoryUsage;
using vespalib::alloc::MemoryAllocator;
using vespalib::alloc::test::MemoryAllocatorObserver;

using AllocStats = MemoryAllocatorObserver::Stats;
using TestBufferStats = vespalib::datastore::test::BufferStats;
using MemStats = vespalib::datastore::test::MemStats;

namespace {

constexpr float ALLOC_GROW_FACTOR = 0.2;

}

template <typename TestT, typename ElemT, typename RefT = EntryRefT<19> >
struct ArrayStoreTest : public TestT
{
    using EntryRefType = RefT;
    using ArrayStoreType = ArrayStore<ElemT, RefT>;
    using LargeArray = typename ArrayStoreType::LargeArray;
    using ConstArrayRef = typename ArrayStoreType::ConstArrayRef;
    using ElemVector = std::vector<ElemT>;
    using value_type = ElemT;
    using ReferenceStore = vespalib::hash_map<EntryRef, ElemVector>;

    AllocStats     stats;
    ArrayStoreType store;
    ReferenceStore refStore;
    generation_t generation;
    bool add_using_allocate;
    ArrayStoreTest(uint32_t maxSmallArraySize = 3, bool enable_free_lists = true, bool add_using_allocate_in = false)
        : store(ArrayStoreConfig(maxSmallArraySize,
                                 ArrayStoreConfig::AllocSpec(16, RefT::offsetSize(), 8_Ki,
                                                             ALLOC_GROW_FACTOR)).enable_free_lists(enable_free_lists),
                std::make_unique<MemoryAllocatorObserver>(stats)),
          refStore(),
          generation(1),
          add_using_allocate(add_using_allocate_in)
    {}
    explicit ArrayStoreTest(const ArrayStoreConfig &storeCfg)
        : store(storeCfg, std::make_unique<MemoryAllocatorObserver>(stats)),
          refStore(),
          generation(1),
          add_using_allocate(false)
    {}
    ~ArrayStoreTest() override;
    void assertAdd(const ElemVector &input) {
        EntryRef ref = add(input);
        assertGet(ref, input);
    }
    EntryRef add(const ElemVector &input) {
        EntryRef result;
        if (add_using_allocate) {
            result = store.allocate(input.size());
            auto dest = store.get_writable(result);
            assert(dest.size() == input.size());
            for (size_t i = 0; i < input.size(); ++i) {
                dest[i] = input[i];
            }
        } else {
            // This is default and preferred way of adding an array.
            result = store.add(ConstArrayRef(input));
        }
        assert(refStore.count(result) == 0);
        refStore.insert(std::make_pair(result, input));
        return result;
    }
    void assertGet(EntryRef ref, const ElemVector &exp) const {
        ConstArrayRef act = store.get(ref);
        EXPECT_EQ(exp, ElemVector(act.begin(), act.end()));
    }
    void remove(EntryRef ref) {
        ASSERT_EQ(1u, refStore.count(ref));
        store.remove(ref);
        refStore.erase(ref);
    }
    void remove(const ElemVector &input) {
        remove(getEntryRef(input));
    }
    uint32_t getBufferId(EntryRef ref) const {
        return EntryRefType(ref).bufferId();
    }
    void assertBufferState(EntryRef ref, const MemStats& expStats) {
        EXPECT_EQ(expStats._used, store.bufferState(ref).size());
        EXPECT_EQ(expStats._hold, store.bufferState(ref).stats().hold_entries());
        EXPECT_EQ(expStats._dead, store.bufferState(ref).stats().dead_entries());
    }
    void assert_buffer_stats(EntryRef ref, const TestBufferStats& exp_stats) {
        const auto& state = store.bufferState(ref);
        EXPECT_EQ(exp_stats._used, state.size());
        EXPECT_EQ(exp_stats._hold, state.stats().hold_entries());
        EXPECT_EQ(exp_stats._dead, state.stats().dead_entries());
        EXPECT_EQ(exp_stats._extra_used, state.stats().extra_used_bytes());
        EXPECT_EQ(exp_stats._extra_hold, state.stats().extra_hold_bytes());
    }
    void assertMemoryUsage(const MemStats expStats) const {
        MemoryUsage act = store.getMemoryUsage();
        EXPECT_EQ(expStats._used, act.usedBytes());
        EXPECT_EQ(expStats._hold, act.allocatedBytesOnHold());
        EXPECT_EQ(expStats._dead, act.deadBytes());
    }
    void assertStoreContent() const {
        for (const auto &elem : refStore) {
            assertGet(elem.first, elem.second);
        }
    }
    void assert_ref_reused(const ElemVector& first, const ElemVector& second, bool should_reuse) {
        EntryRef ref1 = add(first);
        remove(ref1);
        reclaim_memory();
        EntryRef ref2 = add(second);
        EXPECT_EQ(should_reuse, (ref2 == ref1));
        assertGet(ref2, second);
    }
    EntryRef getEntryRef(const ElemVector &input) {
        for (auto itr = refStore.begin(); itr != refStore.end(); ++itr) {
            if (itr->second == input) {
                return itr->first;
            }
        }
        return EntryRef();
    }
    void reclaim_memory() {
        store.assign_generation(generation++);
        store.reclaim_memory(generation);
    }
    void compactWorst(bool compactMemory, bool compactAddressSpace) {
        CompactionSpec compaction_spec(compactMemory, compactAddressSpace);
        CompactionStrategy compaction_strategy;
        store.set_compaction_spec(compaction_spec);
        ICompactionContext::UP ctx = store.compact_worst(compaction_strategy);
        std::vector<AtomicEntryRef> refs;
        for (auto itr = refStore.begin(); itr != refStore.end(); ++itr) {
            refs.emplace_back(itr->first);
        }
        std::vector<AtomicEntryRef> compactedRefs = refs;
        ctx->compact(ArrayRef<AtomicEntryRef>(compactedRefs));
        ReferenceStore compactedRefStore;
        for (size_t i = 0; i < refs.size(); ++i) {
            ASSERT_EQ(0u, compactedRefStore.count(compactedRefs[i].load_relaxed()));
            ASSERT_EQ(1u, refStore.count(refs[i].load_relaxed()));
            compactedRefStore.insert(std::make_pair(compactedRefs[i].load_relaxed(), refStore[refs[i].load_relaxed()]));
        }
        refStore = compactedRefStore;
    }
    size_t elem_size() const { return sizeof(ElemT); }
    size_t largeArraySize() const { return sizeof(LargeArray); }
};

template <typename TestT, typename ElemT, typename RefT>
ArrayStoreTest<TestT, ElemT, RefT>::~ArrayStoreTest() = default;

struct TestParam {
    bool add_using_allocate;
    TestParam(bool add_using_allocate_in) : add_using_allocate(add_using_allocate_in) {}
};

std::ostream& operator<<(std::ostream& os, const TestParam& param)
{
    os << (param.add_using_allocate ? "add_using_allocate" : "basic_add");
    return os;
}

using NumberStoreTestWithParam = ArrayStoreTest<testing::TestWithParam<TestParam>, uint32_t>;

struct NumberStoreTest : public NumberStoreTestWithParam {
   NumberStoreTest() : NumberStoreTestWithParam(3, true, GetParam().add_using_allocate) {}
};

struct NumberStoreFreeListsDisabledTest : public NumberStoreTestWithParam {
    NumberStoreFreeListsDisabledTest() : NumberStoreTestWithParam(3, false, GetParam().add_using_allocate) {}
};

using NumberStoreBasicTest = ArrayStoreTest<testing::Test, uint32_t>;
using StringStoreTest = ArrayStoreTest<testing::Test, std::string>;
using SmallOffsetNumberStoreTest = ArrayStoreTest<testing::Test, uint32_t, EntryRefT<10>>;

TEST(BasicStoreTest, test_with_trivial_and_non_trivial_types)
{
    EXPECT_TRUE(vespalib::can_skip_destruction<NumberStoreBasicTest::value_type>);
    EXPECT_FALSE(vespalib::can_skip_destruction<StringStoreTest::value_type>);
}

INSTANTIATE_TEST_SUITE_P(NumberStoreMultiTest,
                         NumberStoreTest,
                         testing::Values(TestParam(false), TestParam(true)),
                         testing::PrintToStringParamName());

INSTANTIATE_TEST_SUITE_P(NumberStoreFreeListsDisabledMultiTest,
                         NumberStoreFreeListsDisabledTest,
                         testing::Values(TestParam(false), TestParam(true)),
                         testing::PrintToStringParamName());

TEST_P(NumberStoreTest, control_static_sizes) {
    static constexpr size_t sizeof_deque = vespalib::datastore::DataStoreBase::sizeof_entry_ref_hold_list_deque;
    EXPECT_EQ(408u + sizeof_deque, sizeof(store));
    EXPECT_EQ(240u + sizeof_deque, sizeof(NumberStoreTest::ArrayStoreType::DataStoreType));
    EXPECT_EQ(104u, sizeof(NumberStoreTest::ArrayStoreType::SmallBufferType));
    MemoryUsage usage = store.getMemoryUsage();
    EXPECT_EQ(202116u, usage.allocatedBytes());
    EXPECT_EQ(197656u, usage.usedBytes());
}

TEST_P(NumberStoreTest, add_and_get_small_arrays_of_trivial_type)
{
    assertAdd({});
    assertAdd({1});
    assertAdd({2,3});
    assertAdd({3,4,5});
}

TEST_F(StringStoreTest, add_and_get_small_arrays_of_non_trivial_type)
{
    assertAdd({});
    assertAdd({"aa"});
    assertAdd({"bbb", "ccc"});
    assertAdd({"ddd", "eeee", "fffff"});
}

TEST_P(NumberStoreTest, add_and_get_large_arrays_of_simple_type)
{
    assertAdd({1,2,3,4});
    assertAdd({2,3,4,5,6});
}

TEST_F(StringStoreTest, add_and_get_large_arrays_of_non_trivial_type)
{
    assertAdd({"aa", "bb", "cc", "dd"});
    assertAdd({"ddd", "eee", "ffff", "gggg", "hhhh"});
}

TEST_P(NumberStoreTest, entries_are_put_on_hold_when_a_small_array_is_removed)
{
    EntryRef ref = add({1,2,3});
    assertBufferState(ref, MemStats().used(1).hold(0));
    store.remove(ref);
    assertBufferState(ref, MemStats().used(1).hold(1));
}

TEST_P(NumberStoreTest, entries_are_put_on_hold_when_a_large_array_is_removed)
{
    EntryRef ref = add({1,2,3,4});
    // Note: The first buffer has the first element reserved -> we expect 2 elements used here.
    assertBufferState(ref, MemStats().used(2).hold(0).dead(1));
    store.remove(ref);
    assertBufferState(ref, MemStats().used(2).hold(1).dead(1));
}

TEST_P(NumberStoreTest, small_arrays_are_allocated_from_free_lists_when_enabled) {
    assert_ref_reused({1,2,3}, {4,5,6}, true);
}

TEST_P(NumberStoreTest, large_arrays_are_allocated_from_free_lists_when_enabled) {
    assert_ref_reused({1,2,3,4}, {5,6,7,8}, true);
}

TEST_P(NumberStoreFreeListsDisabledTest, small_arrays_are_NOT_allocated_from_free_lists_when_disabled) {
    assert_ref_reused({1,2,3}, {4,5,6}, false);
}

TEST_P(NumberStoreFreeListsDisabledTest, large_arrays_are_NOT_allocated_from_free_lists_when_disabled) {
    assert_ref_reused({1,2,3,4}, {5,6,7,8}, false);
}

TEST_P(NumberStoreTest, track_size_of_large_array_allocations_with_free_lists_enabled) {
    EntryRef ref = add({1,2,3,4});
    assert_buffer_stats(ref, TestBufferStats().used(2).hold(0).dead(1).extra_used(16));
    remove({1,2,3,4});
    assert_buffer_stats(ref, TestBufferStats().used(2).hold(1).dead(1).extra_hold(16).extra_used(16));
    reclaim_memory();
    assert_buffer_stats(ref, TestBufferStats().used(2).hold(0).dead(2).extra_used(0));
    add({5,6,7,8,9});
    assert_buffer_stats(ref, TestBufferStats().used(2).hold(0).dead(1).extra_used(20));
}

TEST_F(SmallOffsetNumberStoreTest, new_underlying_buffer_is_allocated_when_current_is_full)
{
    uint32_t firstBufferId = getBufferId(add({1,1}));
    for (uint32_t i = 0; i < (SmallOffsetNumberStoreTest::EntryRefType::offsetSize() - 1); ++i) {
        uint32_t bufferId = getBufferId(add({i, i+1}));
        EXPECT_EQ(firstBufferId, bufferId);
    }
    assertStoreContent();

    uint32_t secondBufferId = getBufferId(add({2,2}));
    EXPECT_NE(firstBufferId, secondBufferId);
    for (uint32_t i = 0; i < 10u; ++i) {
        uint32_t bufferId = getBufferId(add({i+2,i}));
        EXPECT_EQ(secondBufferId, bufferId);
    }
    assertStoreContent();
}

namespace {

void
test_compaction(NumberStoreBasicTest &f)
{
    EntryRef size1Ref = f.add({1});
    EntryRef size2Ref = f.add({2,2});
    EntryRef size3Ref = f.add({3,3,3});
    f.remove(f.add({5,5}));
    f.reclaim_memory();
    f.assertBufferState(size1Ref, MemStats().used(1).dead(0));
    f.assertBufferState(size2Ref, MemStats().used(2).dead(1));
    f.assertBufferState(size3Ref, MemStats().used(2).dead(1)); // Note: First element is reserved
    uint32_t size1BufferId = f.getBufferId(size1Ref);
    uint32_t size2BufferId = f.getBufferId(size2Ref);
    uint32_t size3BufferId = f.getBufferId(size3Ref);

    EXPECT_EQ(3u, f.refStore.size());
    f.compactWorst(true, false);
    EXPECT_EQ(3u, f.refStore.size());
    f.assertStoreContent();

    EXPECT_EQ(size1BufferId, f.getBufferId(f.getEntryRef({1})));
    EXPECT_EQ(size3BufferId, f.getBufferId(f.getEntryRef({3,3,3})));
    // Buffer for size 2 arrays has been compacted
    EXPECT_NE(size2BufferId, f.getBufferId(f.getEntryRef({2,2})));
    f.assertGet(size2Ref, {2,2}); // Old ref should still point to data.
    EXPECT_TRUE(f.store.bufferState(size2Ref).isOnHold());
    f.reclaim_memory();
    EXPECT_TRUE(f.store.bufferState(size2Ref).isFree());
}

}

struct NumberStoreTwoSmallBufferTypesTest : public NumberStoreBasicTest {
    NumberStoreTwoSmallBufferTypesTest() : NumberStoreBasicTest(2) {}
};

TEST_F(NumberStoreTwoSmallBufferTypesTest, buffer_with_most_dead_space_is_compacted)
{
    test_compaction(*this);
}

namespace {

void testCompaction(NumberStoreTest &f, bool compactMemory, bool compactAddressSpace)
{
    EntryRef size1Ref = f.add({1});
    EntryRef size2Ref = f.add({2,2});
    EntryRef size3Ref = f.add({3,3,3});
    f.remove(f.add({5,5,5}));
    f.remove(f.add({6}));
    f.remove(f.add({7}));
    f.reclaim_memory();
    f.assertBufferState(size1Ref, MemStats().used(3).dead(2));
    f.assertBufferState(size2Ref, MemStats().used(1).dead(0));
    f.assertBufferState(size3Ref, MemStats().used(2).dead(1));
    uint32_t size1BufferId = f.getBufferId(size1Ref);
    uint32_t size2BufferId = f.getBufferId(size2Ref);
    uint32_t size3BufferId = f.getBufferId(size3Ref);

    EXPECT_EQ(3u, f.refStore.size());
    f.compactWorst(compactMemory, compactAddressSpace);
    EXPECT_EQ(3u, f.refStore.size());
    f.assertStoreContent();

    if (compactMemory) {
        EXPECT_NE(size3BufferId, f.getBufferId(f.getEntryRef({3,3,3})));
    } else {
        EXPECT_EQ(size3BufferId, f.getBufferId(f.getEntryRef({3,3,3})));
    }
    if (compactAddressSpace) {
        EXPECT_NE(size1BufferId, f.getBufferId(f.getEntryRef({1})));
    } else {
        EXPECT_EQ(size1BufferId, f.getBufferId(f.getEntryRef({1})));
    }
    EXPECT_EQ(size2BufferId, f.getBufferId(f.getEntryRef({2,2})));
    f.assertGet(size1Ref, {1}); // Old ref should still point to data.
    f.assertGet(size3Ref, {3,3,3}); // Old ref should still point to data.
    if (compactMemory) {
        EXPECT_TRUE(f.store.bufferState(size3Ref).isOnHold());
    } else {
        EXPECT_FALSE(f.store.bufferState(size3Ref).isOnHold());
    }
    if (compactAddressSpace) {
        EXPECT_TRUE(f.store.bufferState(size1Ref).isOnHold());
    } else {
        EXPECT_FALSE(f.store.bufferState(size1Ref).isOnHold());
    }
    EXPECT_FALSE(f.store.bufferState(size2Ref).isOnHold());
    f.reclaim_memory();
    if (compactMemory) {
        EXPECT_TRUE(f.store.bufferState(size3Ref).isFree());
    } else {
        EXPECT_FALSE(f.store.bufferState(size3Ref).isFree());
    }
    if (compactAddressSpace) {
        EXPECT_TRUE(f.store.bufferState(size1Ref).isFree());
    } else {
        EXPECT_FALSE(f.store.bufferState(size1Ref).isFree());
    }
    EXPECT_FALSE(f.store.bufferState(size2Ref).isFree());
}

}

TEST_P(NumberStoreTest, compactWorst_selects_on_only_memory) {
    testCompaction(*this, true, false);
}

TEST_P(NumberStoreTest, compactWorst_selects_on_only_address_space) {
    testCompaction(*this, false, true);
}

TEST_P(NumberStoreTest, compactWorst_selects_on_both_memory_and_address_space) {
    testCompaction(*this, true, true);
}

TEST_P(NumberStoreTest, compactWorst_selects_on_neither_memory_nor_address_space) {
    testCompaction(*this, false, false);
}

TEST_P(NumberStoreTest, used_onHold_and_dead_memory_usage_is_tracked_for_small_arrays)
{
    MemStats exp(store.getMemoryUsage());
    add({1,2,3});
    assertMemoryUsage(exp.used(elem_size() * 3));
    remove({1,2,3});
    assertMemoryUsage(exp.hold(elem_size() * 3));
    reclaim_memory();
    assertMemoryUsage(exp.holdToDead(elem_size() * 3));
}

TEST_P(NumberStoreTest, used_onHold_and_dead_memory_usage_is_tracked_for_large_arrays)
{
    MemStats exp(store.getMemoryUsage());
    add({1,2,3,4});
    assertMemoryUsage(exp.used(largeArraySize() + elem_size() * 4));
    remove({1,2,3,4});
    assertMemoryUsage(exp.hold(largeArraySize() + elem_size() * 4));
    reclaim_memory();
    assertMemoryUsage(exp.decUsed(elem_size() * 4).decHold(largeArraySize() + elem_size() * 4).
            dead(largeArraySize()));
}

TEST_P(NumberStoreTest, address_space_usage_is_ratio_between_used_arrays_and_number_of_possible_arrays)
{
    add({2,2});
    add({3,3,3});
    // 1 array is reserved (buffer 0, offset 0).
    EXPECT_EQ(3u, store.addressSpaceUsage().used());
    EXPECT_EQ(1u, store.addressSpaceUsage().dead());
    size_t fourgig = (1ull << 32);
    /*
     * Expected limit is sum of allocated arrays for active buffers and
     * potentially allocated arrays for free buffers. If all buffers were
     * free then the limit would be 4 Gi.
     * Then we subtract arrays for 4 buffers that are not free (arraySize=1,2,3 + largeArray),
     * and add their actual number of allocated arrays (16 arrays per buffer).
     * Note: arraySize=3 has 21 arrays as allocated buffer is rounded up to power of 2:
     *   16 * 3 * sizeof(int) = 192 -> 256.
     *   allocated elements = 256 / sizeof(int) = 64.
     *   limit = 64 / 3 = 21.
     */
    size_t expLimit = fourgig - 4 * NumberStoreTest::EntryRefType::offsetSize() + 3 * 16 + 21;
    EXPECT_EQ(static_cast<double>(2)/ expLimit, store.addressSpaceUsage().usage());
    EXPECT_EQ(expLimit, store.addressSpaceUsage().limit());
}

struct ByteStoreTest : public ArrayStoreTest<testing::Test, uint8_t> {
    ByteStoreTest() : ArrayStoreTest<testing::Test, uint8_t>(ByteStoreTest::ArrayStoreType::
                                              optimizedConfigForHugePage(1023,
                                                                         vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                                         vespalib::alloc::MemoryAllocator::PAGE_SIZE,
                                                                         8_Ki, ALLOC_GROW_FACTOR)) {}
};

TEST_F(ByteStoreTest, offset_in_EntryRefT_is_within_bounds_when_allocating_memory_buffers_where_wanted_number_of_bytes_is_not_a_power_of_2_and_less_than_huge_page_size)
{
    // The array store config used in this test is equivalent to the one multi-value attribute uses when initializing multi-value mapping.
    // See similar test in datastore_test.cpp for more details on what happens during memory allocation.
    for (size_t i = 0; i < 1000000; ++i) {
        add({1, 2, 3});
    }
    assertStoreContent();
}

TEST_P(NumberStoreTest, provided_memory_allocator_is_used)
{
    EXPECT_EQ(AllocStats(4, 0), stats);
}

GTEST_MAIN_RUN_ALL_TESTS()
