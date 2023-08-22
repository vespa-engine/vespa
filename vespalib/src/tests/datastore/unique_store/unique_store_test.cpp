// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/datastore/compaction_spec.h>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/datastore/unique_store.hpp>
#include <vespa/vespalib/datastore/unique_store_remapper.h>
#include <vespa/vespalib/datastore/unique_store_string_allocator.hpp>
#include <vespa/vespalib/datastore/unique_store_string_comparator.h>
#include <vespa/vespalib/datastore/sharded_hash_map.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/datastore/buffer_stats.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/test/memory_allocator_observer.h>
#include <vespa/vespalib/util/traits.h>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("unique_store_test");

enum class DictionaryType { BTREE, HASH, BTREE_AND_HASH };

using namespace vespalib::datastore;
using vespalib::ArrayRef;
using generation_t = vespalib::GenerationHandler::generation_t;
using vespalib::alloc::MemoryAllocator;
using vespalib::alloc::test::MemoryAllocatorObserver;
using AllocStats = MemoryAllocatorObserver::Stats;
using TestBufferStats = vespalib::datastore::test::BufferStats;

template <typename UniqueStoreT>
struct TestBaseValues {
    using UniqueStoreType = UniqueStoreT;
    using ValueType = typename UniqueStoreType::EntryType;
    static std::vector<ValueType> values;
};

template <typename UniqueStoreTypeAndDictionaryType>
struct TestBase : public ::testing::Test {
    using UniqueStoreType = typename UniqueStoreTypeAndDictionaryType::UniqueStoreType;
    using EntryRefType = typename UniqueStoreType::RefType;
    using ValueType = typename UniqueStoreType::EntryType;
    using ValueConstRefType = typename UniqueStoreType::EntryConstRefType;
    using CompareType = typename UniqueStoreType::CompareType;
    using ReferenceStoreValueType = std::conditional_t<std::is_same_v<ValueType, const char *>, std::string, ValueType>;
    using ReferenceStore = std::map<EntryRef, std::pair<ReferenceStoreValueType,uint32_t>>;

    AllocStats stats;
    UniqueStoreType store;
    ReferenceStore refStore;
    generation_t generation;

    TestBase();
    ~TestBase() override;
    const std::vector<ValueType>& values() const noexcept { return TestBaseValues<UniqueStoreType>::values; }
    void assertAdd(ValueConstRefType input) {
        EntryRef ref = add(input);
        assertGet(ref, input);
    }
    EntryRef add(ValueConstRefType input) {
        UniqueStoreAddResult addResult = store.add(input);
        EntryRef result = addResult.ref();
        auto insres = refStore.insert(std::make_pair(result, std::make_pair(ReferenceStoreValueType(input), 1u)));
        EXPECT_EQ(insres.second, addResult.inserted());
        if (!insres.second) {
            ++insres.first->second.second;
        }
        return result;
    }
    void alignRefStore(EntryRef ref, ValueConstRefType input, uint32_t refcnt) {
        if (refcnt > 0) {
            auto insres = refStore.insert(std::make_pair(ref, std::make_pair(ReferenceStoreValueType(input), refcnt)));
            if (!insres.second) {
                insres.first->second.second = refcnt;
            }
        } else {
            refStore.erase(ref);
        }
    }
    void assertGet(EntryRef ref, ReferenceStoreValueType exp) const {
        ReferenceStoreValueType act = store.get(ref);
        EXPECT_EQ(exp, act);
    }
    void remove(EntryRef ref) {
        ASSERT_EQ(1u, refStore.count(ref));
        store.remove(ref);
        if (refStore[ref].second > 1) {
            --refStore[ref].second;
        } else {
            refStore.erase(ref);
        }
    }
    void remove(ValueConstRefType input) {
        remove(getEntryRef(input));
    }
    uint32_t getBufferId(EntryRef ref) const {
        return EntryRefType(ref).bufferId();
    }
    void assertBufferState(EntryRef ref, const TestBufferStats expStats) const {
        EXPECT_EQ(expStats._used, store.bufferState(ref).size());
        EXPECT_EQ(expStats._hold, store.bufferState(ref).stats().hold_entries());
        EXPECT_EQ(expStats._dead, store.bufferState(ref).stats().dead_entries());
    }
    void assertStoreContent() const {
        for (const auto &elem : refStore) {
            assertGet(elem.first, elem.second.first);
        }
    }
    EntryRef getEntryRef(ValueConstRefType input) {
        for (const auto &elem : refStore) {
            if (elem.second.first == input) {
                return elem.first;
            }
        }
        return EntryRef();
    }
    void reclaim_memory() {
        store.freeze();
        store.assign_generation(generation++);
        store.reclaim_memory(generation);
    }
    void compactWorst() {
        CompactionSpec compaction_spec(true, true);
        // Use a compaction strategy that will compact all active buffers
        auto compaction_strategy = CompactionStrategy::make_compact_all_active_buffers_strategy();
        auto remapper = store.compact_worst(compaction_spec, compaction_strategy);
        std::vector<AtomicEntryRef> refs;
        for (const auto &elem : refStore) {
            refs.push_back(AtomicEntryRef(elem.first));
        }
        refs.push_back(AtomicEntryRef());
        std::vector<AtomicEntryRef> compactedRefs = refs;
        remapper->remap(ArrayRef<AtomicEntryRef>(compactedRefs));
        remapper->done();
        remapper.reset();
        ASSERT_FALSE(refs.back().load_relaxed().valid());
        refs.pop_back();
        ASSERT_FALSE(compactedRefs.back().load_relaxed().valid());
        compactedRefs.pop_back();
        ReferenceStore compactedRefStore;
        for (size_t i = 0; i < refs.size(); ++i) {
            ASSERT_EQ(0u, compactedRefStore.count(compactedRefs[i].load_relaxed()));
            ASSERT_EQ(1u, refStore.count(refs[i].load_relaxed()));
            compactedRefStore.insert(std::make_pair(compactedRefs[i].load_relaxed(), refStore[refs[i].load_relaxed()]));
        }
        refStore = compactedRefStore;
    }
    size_t entrySize() const { return sizeof(ValueType); }
    auto getBuilder(uint32_t uniqueValuesHint) { return store.getBuilder(uniqueValuesHint); }
    auto getEnumerator(bool sort_unique_values) { return store.getEnumerator(sort_unique_values); }
    size_t get_reserved(EntryRef ref) {
        return store.bufferState(ref).getTypeHandler()->get_reserved_entries(getBufferId(ref));
    }
};

template <typename UniqueStoreTypeAndDictionaryType>
TestBase<UniqueStoreTypeAndDictionaryType>::TestBase()
    : stats(),
      store(std::make_unique<MemoryAllocatorObserver>(stats)),
      refStore(),
      generation(1)
{
    switch (UniqueStoreTypeAndDictionaryType::dictionary_type) {
    case DictionaryType::BTREE:
        EXPECT_TRUE(store.get_dictionary().get_has_btree_dictionary());
        EXPECT_FALSE(store.get_dictionary().get_has_hash_dictionary());
        break;
    case DictionaryType::BTREE_AND_HASH:
        store.set_dictionary(std::make_unique<UniqueStoreDictionary<uniquestore::DefaultDictionary, IUniqueStoreDictionary, ShardedHashMap>>(std::make_unique<CompareType>(store.get_data_store())));
        EXPECT_TRUE(store.get_dictionary().get_has_btree_dictionary());
        EXPECT_TRUE(store.get_dictionary().get_has_hash_dictionary());
        break;
    case DictionaryType::HASH:
    default:
        store.set_dictionary(std::make_unique<UniqueStoreDictionary<NoBTreeDictionary, IUniqueStoreDictionary, ShardedHashMap>>(std::make_unique<CompareType>(store.get_data_store())));
        EXPECT_FALSE(store.get_dictionary().get_has_btree_dictionary());
        EXPECT_TRUE(store.get_dictionary().get_has_hash_dictionary());
    }
}

template <typename UniqueStoreTypeAndDictionaryType>
TestBase<UniqueStoreTypeAndDictionaryType>::~TestBase() = default;

using NumberUniqueStore  = UniqueStore<uint32_t>;
using StringUniqueStore  = UniqueStore<std::string>;
using CStringUniqueStore = UniqueStore<const char *, EntryRefT<22>, UniqueStoreStringComparator<EntryRefT<22>>, UniqueStoreStringAllocator<EntryRefT<22>>>;
using DoubleUniqueStore  = UniqueStore<double>;
using SmallOffsetNumberUniqueStore = UniqueStore<uint32_t, EntryRefT<10,10>>;

template <>
std::vector<uint32_t> TestBaseValues<NumberUniqueStore>::values{10, 20, 30, 10 };
template <>
std::vector<std::string> TestBaseValues<StringUniqueStore>::values{ "aa", "bbb", "ccc", "aa" };
template <>
std::vector<const char *> TestBaseValues<CStringUniqueStore>::values{ "aa", "bbb", "ccc", "aa" };
template <>
std::vector<double> TestBaseValues<DoubleUniqueStore>::values{ 10.0, 20.0, 30.0, 10.0 };

struct BTreeNumberUniqueStore
{
    using UniqueStoreType = NumberUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::BTREE;
};

struct BTreeStringUniqueStore
{
    using UniqueStoreType = StringUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::BTREE;
};

struct BTreeCStringUniqueStore
{
    using UniqueStoreType = CStringUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::BTREE;
};

struct BTreeDoubleUniqueStore
{
    using UniqueStoreType = DoubleUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::BTREE;
};

struct BTreeSmallOffsetNumberUniqueStore
{
    using UniqueStoreType = SmallOffsetNumberUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::BTREE;
};

struct HybridNumberUniqueStore
{
    using UniqueStoreType = NumberUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::BTREE_AND_HASH;
};

struct HybridStringUniqueStore
{
    using UniqueStoreType = StringUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::BTREE_AND_HASH;
};

struct HybridCStringUniqueStore
{
    using UniqueStoreType = CStringUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::BTREE_AND_HASH;
};

struct HybridDoubleUniqueStore
{
    using UniqueStoreType = DoubleUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::BTREE_AND_HASH;
};

struct HybridSmallOffsetNumberUniqueStore
{
    using UniqueStoreType = SmallOffsetNumberUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::BTREE_AND_HASH;
};

struct HashNumberUniqueStore
{
    using UniqueStoreType = NumberUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::HASH;
};

struct HashStringUniqueStore
{
    using UniqueStoreType = StringUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::HASH;
};

struct HashCStringUniqueStore
{
    using UniqueStoreType = CStringUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::HASH;
};

struct HashDoubleUniqueStore
{
    using UniqueStoreType = DoubleUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::HASH;
};

struct HashSmallOffsetNumberUniqueStore
{
    using UniqueStoreType = SmallOffsetNumberUniqueStore;
    static constexpr DictionaryType dictionary_type = DictionaryType::HASH;
};

using UniqueStoreTestTypes = ::testing::Types<BTreeNumberUniqueStore, BTreeStringUniqueStore, BTreeCStringUniqueStore, BTreeDoubleUniqueStore, HybridNumberUniqueStore, HybridStringUniqueStore, HybridCStringUniqueStore, HybridDoubleUniqueStore, HashNumberUniqueStore, HashStringUniqueStore, HashCStringUniqueStore, HashDoubleUniqueStore>;
TYPED_TEST_SUITE(TestBase, UniqueStoreTestTypes);

using NumberTest = TestBase<BTreeNumberUniqueStore>;
using StringTest = TestBase<BTreeStringUniqueStore>;
using CStringTest = TestBase<BTreeCStringUniqueStore>;
using DoubleTest = TestBase<BTreeDoubleUniqueStore>;
using SmallOffsetNumberTest = TestBase<BTreeSmallOffsetNumberUniqueStore>;

TEST(UniqueStoreTest, trivial_and_non_trivial_types_are_tested)
{
    EXPECT_TRUE(vespalib::can_skip_destruction<NumberTest::ValueType>);
    EXPECT_FALSE(vespalib::can_skip_destruction<StringTest::ValueType>);
}

TYPED_TEST(TestBase, can_add_and_get_values)
{
    for (auto &val : this->values()) {
        this->assertAdd(val);
    }
}

TYPED_TEST(TestBase, entries_are_put_on_hold_when_value_is_removed)
{
    EntryRef ref = this->add(this->values()[0]);
    size_t reserved = this->get_reserved(ref);
    this->assertBufferState(ref, TestBufferStats().used(1 + reserved).hold(0).dead(reserved));
    this->store.remove(ref);
    this->assertBufferState(ref, TestBufferStats().used(1 + reserved).hold(1).dead(reserved));
}

TYPED_TEST(TestBase, entries_are_reference_counted)
{
    EntryRef ref = this->add(this->values()[0]);
    EntryRef ref2 = this->add(this->values()[0]);
    EXPECT_EQ(ref.ref(), ref2.ref());
    // Note: The first buffer have the first entry reserved -> we expect 2 entries used here.
    size_t reserved = this->get_reserved(ref);
    this->assertBufferState(ref, TestBufferStats().used(1 + reserved).hold(0).dead(reserved));
    this->store.remove(ref);
    this->assertBufferState(ref, TestBufferStats().used(1 + reserved).hold(0).dead(reserved));
    this->store.remove(ref);
    this->assertBufferState(ref, TestBufferStats().used(1 + reserved).hold(1).dead(reserved));
}

TEST_F(SmallOffsetNumberTest, new_underlying_buffer_is_allocated_when_current_is_full)
{
    uint32_t firstBufferId = getBufferId(add(1));
    for (uint32_t i = 0; i < (SmallOffsetNumberTest::EntryRefType::offsetSize() - 2); ++i) {
        uint32_t bufferId = getBufferId(add(i + 2));
        EXPECT_EQ(firstBufferId, bufferId);
    }
    assertStoreContent();

    uint32_t bias = SmallOffsetNumberTest::EntryRefType::offsetSize();
    uint32_t secondBufferId = getBufferId(add(bias + 1));
    EXPECT_NE(firstBufferId, secondBufferId);
    for (uint32_t i = 0; i < 10u; ++i) {
        uint32_t bufferId = getBufferId(add(bias + i + 2));
        EXPECT_EQ(secondBufferId, bufferId);
    }
    assertStoreContent();
}

TYPED_TEST(TestBase, store_can_be_compacted)
{
    EntryRef val0Ref = this->add(this->values()[0]);
    EntryRef val1Ref = this->add(this->values()[1]);
    this->remove(this->add(this->values()[2]));
    this->reclaim_memory();
    size_t reserved = this->get_reserved(val0Ref);
    this->assertBufferState(val0Ref, TestBufferStats().used(reserved + 3).dead(reserved + 1));
    uint32_t val1BufferId = this->getBufferId(val0Ref);

    EXPECT_EQ(2u, this->refStore.size());
    this->compactWorst();
    EXPECT_EQ(2u, this->refStore.size());
    this->assertStoreContent();

    // Buffer has been compacted
    EXPECT_NE(val1BufferId, this->getBufferId(this->getEntryRef(this->values()[0])));
    // Old ref should still point to data.
    this->assertGet(val0Ref, this->values()[0]);
    this->assertGet(val1Ref, this->values()[1]);
    EXPECT_TRUE(this->store.bufferState(val0Ref).isOnHold());
    this->reclaim_memory();
    EXPECT_TRUE(this->store.bufferState(val0Ref).isFree());
    this->assertStoreContent();
}

TYPED_TEST(TestBase, store_can_be_instantiated_with_builder)
{
    auto builder = this->getBuilder(2);
    builder.add(this->values()[0]);
    builder.add(this->values()[1]);
    builder.setupRefCounts();
    EntryRef val0Ref = builder.mapEnumValueToEntryRef(1);
    EntryRef val1Ref = builder.mapEnumValueToEntryRef(2);
    size_t reserved = this->get_reserved(val0Ref);
    this->assertBufferState(val0Ref, TestBufferStats().used(2 + reserved).dead(reserved)); // Note: First entry is reserved
    EXPECT_TRUE(val0Ref.valid());
    EXPECT_TRUE(val1Ref.valid());
    EXPECT_NE(val0Ref.ref(), val1Ref.ref());
    this->assertGet(val0Ref, this->values()[0]);
    this->assertGet(val1Ref, this->values()[1]);
    builder.makeDictionary();
    // Align refstore with the two entries added by builder.
    this->alignRefStore(val0Ref, this->values()[0], 1);
    this->alignRefStore(val1Ref, this->values()[1], 1);
    EXPECT_EQ(val0Ref.ref(), this->add(this->values()[0]).ref());
    EXPECT_EQ(val1Ref.ref(), this->add(this->values()[1]).ref());
}

TYPED_TEST(TestBase, store_can_be_enumerated)
{
    EntryRef val0Ref = this->add(this->values()[0]);
    EntryRef val1Ref = this->add(this->values()[1]);
    this->remove(this->add(this->values()[2]));
    this->reclaim_memory();

    auto enumerator = this->getEnumerator(true);
    std::vector<uint32_t> refs;
    enumerator.foreach_key([&](const AtomicEntryRef& ref) { refs.push_back(ref.load_relaxed().ref()); });
    std::vector<uint32_t> expRefs;
    expRefs.push_back(val0Ref.ref());
    expRefs.push_back(val1Ref.ref());
    EXPECT_EQ(expRefs, refs);
    enumerator.enumerateValues();
    uint32_t invalidEnum = enumerator.mapEntryRefToEnumValue(EntryRef());
    uint32_t enumValue1 = enumerator.mapEntryRefToEnumValue(val0Ref);
    uint32_t enumValue2 = enumerator.mapEntryRefToEnumValue(val1Ref);
    EXPECT_EQ(0u, invalidEnum);
    EXPECT_EQ(1u, enumValue1);
    EXPECT_EQ(2u, enumValue2);
}

TYPED_TEST(TestBase, provided_memory_allocator_is_used)
{
    if constexpr (std::is_same_v<const char *, typename TestFixture::ValueType>) {
        EXPECT_EQ(AllocStats(18, 0), this->stats);
    } else {
        EXPECT_EQ(AllocStats(1, 0), this->stats);
    }
}

TEST_F(DoubleTest, nan_is_handled)
{
    std::vector<double> myvalues = {
        std::numeric_limits<double>::quiet_NaN(),
        std::numeric_limits<double>::infinity(),
        -std::numeric_limits<double>::infinity(),
        10.0,
        -std::numeric_limits<double>::quiet_NaN(),
        std::numeric_limits<double>::infinity(),
        -std::numeric_limits<double>::infinity()
    };
    std::vector<EntryRef> refs;
    refs.push_back(EntryRef());
    for (auto &value : myvalues) {
        refs.emplace_back(add(value));
    }
    reclaim_memory();
    EXPECT_TRUE(std::isnan(store.get(refs[1])));
    EXPECT_TRUE(std::signbit(store.get(refs[1])));
    EXPECT_TRUE(std::isinf(store.get(refs[2])));
    EXPECT_FALSE(std::signbit(store.get(refs[2])));
    EXPECT_TRUE(std::isinf(store.get(refs[3])));
    EXPECT_TRUE(std::signbit(store.get(refs[3])));
    auto enumerator = getEnumerator(true);
    enumerator.enumerateValues();
    std::vector<uint32_t> enumerated;
    for (auto &ref : refs) {
        enumerated.push_back(enumerator.mapEntryRefToEnumValue(ref));
    }
    std::vector<uint32_t> exp_enumerated = { 0, 1, 4, 2, 3, 1, 4, 2 };
    EXPECT_EQ(exp_enumerated, enumerated);
}

TEST_F(DoubleTest, control_memory_usage) {
    static constexpr size_t sizeof_deque = vespalib::datastore::DataStoreBase::sizeof_entry_ref_hold_list_deque;
    EXPECT_EQ(376u + sizeof_deque, sizeof(store));
    EXPECT_EQ(120u, sizeof(BufferState));
    EXPECT_EQ(28740u, store.get_values_memory_usage().allocatedBytes());
    EXPECT_EQ(24780u, store.get_values_memory_usage().usedBytes());
    EXPECT_EQ(126952u, store.get_dictionary_memory_usage().allocatedBytes());
    EXPECT_EQ(25200u, store.get_dictionary_memory_usage().usedBytes());
    EXPECT_EQ(155692u, store.getMemoryUsage().allocatedBytes());
    EXPECT_EQ(49980u, store.getMemoryUsage().usedBytes());
}
                
GTEST_MAIN_RUN_ALL_TESTS()
