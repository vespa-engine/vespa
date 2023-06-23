// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/vespalib/datastore/array_store_dynamic_type_mapper.hpp>
#include <vespa/vespalib/datastore/dynamic_array_buffer_type.hpp>
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

template <typename ElemT>
class MyArrayStoreSimpleTypeMapper : public ArrayStoreSimpleTypeMapper<ElemT> {
public:
    MyArrayStoreSimpleTypeMapper(uint32_t, double)
        : ArrayStoreSimpleTypeMapper<ElemT>()
    {
    }
};

}

template <typename TestT, typename ElemT, typename RefT = EntryRefT<19>, typename TypeMapper = ArrayStoreDynamicTypeMapper<ElemT>>
struct ArrayStoreTest : public TestT
{
    using EntryRefType = RefT;
    using ArrayStoreType = ArrayStore<ElemT, RefT, TypeMapper>;
    using LargeArray = typename ArrayStoreType::LargeArray;
    using ConstArrayRef = typename ArrayStoreType::ConstArrayRef;
    using ElemVector = std::vector<ElemT>;
    using value_type = ElemT;
    using ReferenceStore = vespalib::hash_map<EntryRef, ElemVector>;
    using TypeMapperType = TypeMapper;
    static constexpr bool simple_type_mapper = std::is_same_v<TypeMapperType,ArrayStoreSimpleTypeMapper<ElemT>>;
    using TypeMapperWrappedType = std::conditional_t<simple_type_mapper,MyArrayStoreSimpleTypeMapper<ElemT>,TypeMapperType>;

    AllocStats     stats;
    TypeMapperWrappedType type_mapper;
    ArrayStoreType store;
    ReferenceStore refStore;
    generation_t generation;
    bool add_using_allocate;
    double type_mapper_grow_factor;
    ArrayStoreTest(uint32_t max_type_id = 3, bool enable_free_lists = true, bool add_using_allocate_in = false, double type_mapper_grow_factor_in = 2.0)
        : type_mapper(max_type_id, type_mapper_grow_factor_in),
          store(ArrayStoreConfig(max_type_id,
                                 ArrayStoreConfig::AllocSpec(16, RefT::offsetSize(), 8_Ki,
                                                             ALLOC_GROW_FACTOR)).enable_free_lists(enable_free_lists),
                std::make_unique<MemoryAllocatorObserver>(stats),
                TypeMapperType(type_mapper)),
          refStore(),
          generation(1),
          add_using_allocate(add_using_allocate_in),
          type_mapper_grow_factor(type_mapper_grow_factor_in)
    {}
    explicit ArrayStoreTest(const ArrayStoreConfig &storeCfg)
        : type_mapper(storeCfg.max_type_id(), 2.0),
          store(storeCfg, std::make_unique<MemoryAllocatorObserver>(stats), TypeMapperType(type_mapper)),
          refStore(),
          generation(1),
          add_using_allocate(false),
          type_mapper_grow_factor(2.0)
    {}
    ~ArrayStoreTest() override;
    void assertAdd(const ElemVector &input) {
        EntryRef ref = add(input);
        assertGet(ref, input);
    }
    size_t reference_store_count(EntryRef ref) const __attribute__((noinline));
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
        assert(reference_store_count(result) == 0);
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
    bool simple_buffers() const { return simple_type_mapper || type_mapper_grow_factor == 1.0; }
};

template <typename TestT, typename ElemT, typename RefT, typename TypeMapper>
ArrayStoreTest<TestT, ElemT, RefT, TypeMapper>::~ArrayStoreTest() = default;

template <typename TestT, typename ElemT, typename RefT, typename TypeMapper>
size_t
ArrayStoreTest<TestT, ElemT, RefT, TypeMapper>::reference_store_count(EntryRef ref) const
{
    return refStore.count(ref);
}

struct SimpleTypeMapperAdd {
    using TypeMapper = ArrayStoreSimpleTypeMapper<uint32_t>;
    static constexpr bool add_using_allocate = false;
    static constexpr double type_mapper_grow_factor = 1.0;
};

struct SimpleTypeMapperAllocate {
    using TypeMapper = ArrayStoreSimpleTypeMapper<uint32_t>;
    static constexpr bool add_using_allocate = true;
    static constexpr double type_mapper_grow_factor = 1.0;
};

struct DynamicTypeMapperAddGrow1 {
    using TypeMapper = ArrayStoreDynamicTypeMapper<uint32_t>;
    static constexpr bool add_using_allocate = false;
    static constexpr double type_mapper_grow_factor = 1.0;
};

struct DynamicTypeMapperAllocateGrow1 {
    using TypeMapper = ArrayStoreDynamicTypeMapper<uint32_t>;
    static constexpr bool add_using_allocate = true;
    static constexpr double type_mapper_grow_factor = 1.0;
};

struct DynamicTypeMapperAddGrow2 {
    using TypeMapper = ArrayStoreDynamicTypeMapper<uint32_t>;
    static constexpr bool add_using_allocate = false;
    static constexpr double type_mapper_grow_factor = 2.0;
};

struct DynamicTypeMapperAllocateGrow2 {
    using TypeMapper = ArrayStoreDynamicTypeMapper<uint32_t>;
    static constexpr bool add_using_allocate = true;
    static constexpr double type_mapper_grow_factor = 2.0;
};

template <typename TypeMapper>
using NumberStoreTestWithParam = ArrayStoreTest<testing::Test, uint32_t, EntryRefT<19>, TypeMapper>;

template <typename Param>
struct NumberStoreTest : public NumberStoreTestWithParam<typename Param::TypeMapper> {
    using Parent = NumberStoreTestWithParam<typename Param::TypeMapper>;
    NumberStoreTest() : Parent(3, true, Param::add_using_allocate, Param::type_mapper_grow_factor) {}
};

template <typename Param>
struct NumberStoreFreeListsDisabledTest : public NumberStoreTestWithParam<typename Param::TypeMapper> {
    using Parent = NumberStoreTestWithParam<typename Param::TypeMapper>;
    NumberStoreFreeListsDisabledTest() : Parent(3, false, Param::add_using_allocate, Param::type_mapper_grow_factor) {}
};

using NumberStoreTestTypes = testing::Types<SimpleTypeMapperAdd, SimpleTypeMapperAllocate,
                                            DynamicTypeMapperAddGrow1, DynamicTypeMapperAllocateGrow1,
                                            DynamicTypeMapperAddGrow2, DynamicTypeMapperAllocateGrow2>;

TYPED_TEST_SUITE(NumberStoreTest, NumberStoreTestTypes);

TYPED_TEST_SUITE(NumberStoreFreeListsDisabledTest, NumberStoreTestTypes);

using NumberStoreBasicTest = ArrayStoreTest<testing::Test, uint32_t>;
using StringStoreTest = ArrayStoreTest<testing::Test, std::string>;
using SmallOffsetNumberStoreTest = ArrayStoreTest<testing::Test, uint32_t, EntryRefT<10>>;

TEST(BasicStoreTest, test_with_trivial_and_non_trivial_types)
{
    EXPECT_TRUE(vespalib::can_skip_destruction<NumberStoreBasicTest::value_type>);
    EXPECT_FALSE(vespalib::can_skip_destruction<StringStoreTest::value_type>);
}

TYPED_TEST(NumberStoreTest, control_static_sizes) {
    static constexpr size_t sizeof_deque = vespalib::datastore::DataStoreBase::sizeof_entry_ref_hold_list_deque;
    if constexpr (TestFixture::simple_type_mapper) {
        EXPECT_EQ(416u + sizeof_deque, sizeof(this->store));
    } else {
        EXPECT_EQ(464u + sizeof_deque, sizeof(this->store));
    }
    EXPECT_EQ(240u + sizeof_deque, sizeof(typename TestFixture::ArrayStoreType::DataStoreType));
    EXPECT_EQ(112u, sizeof(typename TestFixture::ArrayStoreType::SmallBufferType));
    MemoryUsage usage = this->store.getMemoryUsage();
    if (this->simple_buffers()) {
        EXPECT_EQ(202140u, usage.allocatedBytes());
        EXPECT_EQ(197680u, usage.usedBytes());
    } else {
        EXPECT_EQ(202328u, usage.allocatedBytes());
        EXPECT_EQ(197568u, usage.usedBytes());
    }
}

TYPED_TEST(NumberStoreTest, control_type_mapper)
{
    if constexpr (TestFixture::simple_type_mapper) {
        GTEST_SKIP() << "Skipping test due to using simple type mapper";
    } else {
        EXPECT_EQ(3, this->type_mapper.get_max_type_id(1000));
        EXPECT_FALSE(this->type_mapper.is_dynamic_buffer(0));
        EXPECT_FALSE(this->type_mapper.is_dynamic_buffer(1));
        EXPECT_EQ(1, this->type_mapper.get_array_size(1));
        EXPECT_FALSE(this->type_mapper.is_dynamic_buffer(2));
        EXPECT_EQ(2, this->type_mapper.get_array_size(2));
        if (this->type_mapper_grow_factor == 1.0) {
            EXPECT_FALSE(this->type_mapper.is_dynamic_buffer(3));
            EXPECT_EQ(3, this->type_mapper.get_array_size(3));
            EXPECT_EQ(0, this->type_mapper.count_dynamic_buffer_types(3));
        } else {
            EXPECT_TRUE(this->type_mapper.is_dynamic_buffer(3));
            EXPECT_EQ(4, this->type_mapper.get_array_size(3));
            EXPECT_EQ(1, this->type_mapper.count_dynamic_buffer_types(3));
        }
    }
}

TYPED_TEST(NumberStoreTest, add_and_get_small_arrays_of_trivial_type)
{
    this->assertAdd({});
    this->assertAdd({1});
    this->assertAdd({2,3});
    this->assertAdd({3,4,5});
}

TEST_F(StringStoreTest, add_and_get_small_arrays_of_non_trivial_type)
{
    assertAdd({});
    assertAdd({"aa"});
    assertAdd({"bbb", "ccc"});
    assertAdd({"ddd", "eeee", "fffff"});
}

TYPED_TEST(NumberStoreTest, add_and_get_large_arrays_of_simple_type)
{
    this->assertAdd({1,2,3,4,5});
    this->assertAdd({2,3,4,5,6,7});
}

TEST_F(StringStoreTest, add_and_get_large_arrays_of_non_trivial_type)
{
    assertAdd({"aa", "bb", "cc", "dd", "ee"});
    assertAdd({"ddd", "eee", "ffff", "gggg", "hhhh", "iiii"});
}

TYPED_TEST(NumberStoreTest, entries_are_put_on_hold_when_a_small_array_is_removed)
{
    EntryRef ref = this->add({1,2,3});
    this->assertBufferState(ref, MemStats().used(1).hold(0));
    this->store.remove(ref);
    this->assertBufferState(ref, MemStats().used(1).hold(1));
}

TYPED_TEST(NumberStoreTest, entries_are_put_on_hold_when_a_large_array_is_removed)
{
    EntryRef ref = this->add({1,2,3,4,5});
    // Note: The first buffer has the first element reserved -> we expect 2 elements used here.
    this->assertBufferState(ref, MemStats().used(2).hold(0).dead(1));
    this->store.remove(ref);
    this->assertBufferState(ref, MemStats().used(2).hold(1).dead(1));
}

TYPED_TEST(NumberStoreTest, small_arrays_are_allocated_from_free_lists_when_enabled) {
    this->assert_ref_reused({1,2,3}, {4,5,6}, true);
}

TYPED_TEST(NumberStoreTest, large_arrays_are_allocated_from_free_lists_when_enabled) {
    this->assert_ref_reused({1,2,3,4,5}, {5,6,7,8,9}, true);
}

TYPED_TEST(NumberStoreFreeListsDisabledTest, small_arrays_are_NOT_allocated_from_free_lists_when_disabled) {
    this->assert_ref_reused({1,2,3}, {4,5,6}, false);
}

TYPED_TEST(NumberStoreFreeListsDisabledTest, large_arrays_are_NOT_allocated_from_free_lists_when_disabled) {
    this->assert_ref_reused({1,2,3,4,5}, {5,6,7,8,9}, false);
}

TYPED_TEST(NumberStoreTest, track_size_of_large_array_allocations_with_free_lists_enabled) {
    EntryRef ref = this->add({1,2,3,4,5});
    this->assert_buffer_stats(ref, TestBufferStats().used(2).hold(0).dead(1).extra_used(20));
    this->remove({1,2,3,4,5});
    this->assert_buffer_stats(ref, TestBufferStats().used(2).hold(1).dead(1).extra_hold(20).extra_used(20));
    this->reclaim_memory();
    this->assert_buffer_stats(ref, TestBufferStats().used(2).hold(0).dead(2).extra_used(0));
    this->add({5,6,7,8,9,10});
    this->assert_buffer_stats(ref, TestBufferStats().used(2).hold(0).dead(1).extra_used(24));
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

template <typename Fixture>
void testCompaction(Fixture &f, bool compactMemory, bool compactAddressSpace)
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

TYPED_TEST(NumberStoreTest, compactWorst_selects_on_only_memory) {
    testCompaction<typename TestFixture::Parent>(*this, true, false);
}

TYPED_TEST(NumberStoreTest, compactWorst_selects_on_only_address_space) {
    testCompaction<typename TestFixture::Parent>(*this, false, true);
}

TYPED_TEST(NumberStoreTest, compactWorst_selects_on_both_memory_and_address_space) {
    testCompaction<typename TestFixture::Parent>(*this, true, true);
}

TYPED_TEST(NumberStoreTest, compactWorst_selects_on_neither_memory_nor_address_space) {
    testCompaction<typename TestFixture::Parent>(*this, false, false);
}

TYPED_TEST(NumberStoreTest, used_onHold_and_dead_memory_usage_is_tracked_for_small_arrays)
{
    MemStats exp(this->store.getMemoryUsage());
    this->add({1,2,3});
    uint32_t exp_entry_size = this->simple_buffers() ? (this->elem_size() * 3) : (this->elem_size() * 4 + 4);
    this->assertMemoryUsage(exp.used(exp_entry_size));
    this->remove({1,2,3});
    this->assertMemoryUsage(exp.hold(exp_entry_size));
    this->reclaim_memory();
    this->assertMemoryUsage(exp.holdToDead(exp_entry_size));
}

TYPED_TEST(NumberStoreTest, used_onHold_and_dead_memory_usage_is_tracked_for_large_arrays)
{
    MemStats exp(this->store.getMemoryUsage());
    this->add({1,2,3,4,5});
    this->assertMemoryUsage(exp.used(this->largeArraySize() + this->elem_size() * 5));
    this->remove({1,2,3,4,5});
    this->assertMemoryUsage(exp.hold(this->largeArraySize() + this->elem_size() * 5));
    this->reclaim_memory();
    this->assertMemoryUsage(exp.decUsed(this->elem_size() * 5).decHold(this->largeArraySize() + this->elem_size() * 5).
            dead(this->largeArraySize()));
}

TYPED_TEST(NumberStoreTest, address_space_usage_is_ratio_between_used_arrays_and_number_of_possible_arrays)
{
    this->add({2,2});
    this->add({3,3,3});
    // 1 array is reserved (buffer 0, offset 0).
    EXPECT_EQ(3u, this->store.addressSpaceUsage().used());
    EXPECT_EQ(1u, this->store.addressSpaceUsage().dead());
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
     *
     * For dynamic buffer 3, we have 16 * 5 * sizeof(int) => 320 -> 512 - 64
     * limit = (512 -64) / (5 * 4) = 22
     */
    size_t type_id_3_entries = this->simple_buffers() ? 21 : 22;
    size_t expLimit = fourgig - 4 * TestFixture::EntryRefType::offsetSize() + 3 * 16 + type_id_3_entries;
    EXPECT_EQ(static_cast<double>(2)/ expLimit, this->store.addressSpaceUsage().usage());
    EXPECT_EQ(expLimit, this->store.addressSpaceUsage().limit());
}

struct ByteStoreTest : public ArrayStoreTest<testing::Test, uint8_t, EntryRefT<19>, ArrayStoreSimpleTypeMapper<uint8_t>> {
    ByteStoreTest() : ArrayStoreTest<testing::Test, uint8_t, EntryRefT<19>, ArrayStoreSimpleTypeMapper<uint8_t>>(ByteStoreTest::ArrayStoreType::
                                              optimizedConfigForHugePage(1023,
                                                                         vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                                         vespalib::alloc::MemoryAllocator::PAGE_SIZE,
                                                                         ArrayStoreConfig::default_max_buffer_size,
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

TYPED_TEST(NumberStoreTest, provided_memory_allocator_is_used)
{
    EXPECT_EQ(AllocStats(4, 0), this->stats);
}

GTEST_MAIN_RUN_ALL_TESTS()
