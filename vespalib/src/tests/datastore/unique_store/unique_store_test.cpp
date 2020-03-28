// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/datastore/unique_store.hpp>
#include <vespa/vespalib/datastore/unique_store_remapper.h>
#include <vespa/vespalib/datastore/unique_store_string_allocator.hpp>
#include <vespa/vespalib/datastore/unique_store_string_comparator.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/datastore/buffer_stats.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/util/traits.h>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("unique_store_test");

using namespace search::datastore;
using vespalib::ArrayRef;
using generation_t = vespalib::GenerationHandler::generation_t;
using search::datastore::test::BufferStats;

template <typename UniqueStoreT>
struct TestBase : public ::testing::Test {
    using EntryRefType = typename UniqueStoreT::RefType;
    using UniqueStoreType = UniqueStoreT;
    using ValueType = typename UniqueStoreT::EntryType;
    using ValueConstRefType = typename UniqueStoreT::EntryConstRefType;
    using ReferenceStoreValueType = std::conditional_t<std::is_same_v<ValueType, const char *>, std::string, ValueType>;
    using ReferenceStore = std::map<EntryRef, std::pair<ReferenceStoreValueType,uint32_t>>;

    UniqueStoreType store;
    ReferenceStore refStore;
    generation_t generation;

    static std::vector<ValueType> values;

    TestBase();
    ~TestBase() override;
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
    void assertBufferState(EntryRef ref, const BufferStats expStats) const {
        EXPECT_EQ(expStats._used, store.bufferState(ref).size());
        EXPECT_EQ(expStats._hold, store.bufferState(ref).getHoldElems());
        EXPECT_EQ(expStats._dead, store.bufferState(ref).getDeadElems());
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
    void trimHoldLists() {
        store.freeze();
        store.transferHoldLists(generation++);
        store.trimHoldLists(generation);
    }
    void compactWorst() {
        auto remapper = store.compact_worst(true, true);
        std::vector<EntryRef> refs;
        for (const auto &elem : refStore) {
            refs.push_back(elem.first);
        }
        refs.push_back(EntryRef());
        std::vector<EntryRef> compactedRefs = refs;
        remapper->remap(ArrayRef<EntryRef>(compactedRefs));
        remapper->done();
        remapper.reset();
        ASSERT_FALSE(refs.back().valid());
        refs.pop_back();
        ReferenceStore compactedRefStore;
        for (size_t i = 0; i < refs.size(); ++i) {
            ASSERT_EQ(0u, compactedRefStore.count(compactedRefs[i]));
            ASSERT_EQ(1u, refStore.count(refs[i]));
            compactedRefStore.insert(std::make_pair(compactedRefs[i], refStore[refs[i]]));
        }
        refStore = compactedRefStore;
    }
    size_t entrySize() const { return sizeof(ValueType); }
    auto getBuilder(uint32_t uniqueValuesHint) { return store.getBuilder(uniqueValuesHint); }
    auto getEnumerator() { return store.getEnumerator(); }
    size_t get_reserved(EntryRef ref) {
        return store.bufferState(ref).getTypeHandler()->getReservedElements(getBufferId(ref));
    }
    size_t get_array_size(EntryRef ref) {
        return store.bufferState(ref).getArraySize();
    }
};

template <typename UniqueStoreT>
TestBase<UniqueStoreT>::TestBase()
    : store(),
      refStore(),
      generation(1)
{
}

template <typename UniqueStoreT>
TestBase<UniqueStoreT>::~TestBase() = default;

using NumberUniqueStore  = UniqueStore<uint32_t>;
using StringUniqueStore  = UniqueStore<std::string>;
using CStringUniqueStore = UniqueStore<const char *, EntryRefT<22>, UniqueStoreStringComparator<EntryRefT<22>>, UniqueStoreStringAllocator<EntryRefT<22>>>;
using DoubleUniqueStore  = UniqueStore<double>;
using SmallOffsetNumberUniqueStore = UniqueStore<uint32_t, EntryRefT<10,10>>;

template <>
std::vector<uint32_t> TestBase<NumberUniqueStore>::values{10, 20, 30, 10 };
template <>
std::vector<std::string> TestBase<StringUniqueStore>::values{ "aa", "bbb", "ccc", "aa" };
template <>
std::vector<const char *> TestBase<CStringUniqueStore>::values{ "aa", "bbb", "ccc", "aa" };
template <>
std::vector<double> TestBase<DoubleUniqueStore>::values{ 10.0, 20.0, 30.0, 10.0 };

using UniqueStoreTestTypes = ::testing::Types<NumberUniqueStore, StringUniqueStore, CStringUniqueStore, DoubleUniqueStore>;
#ifdef TYPED_TEST_SUITE
TYPED_TEST_SUITE(TestBase, UniqueStoreTestTypes);
#else
TYPED_TEST_CASE(TestBase, UniqueStoreTestTypes);
#endif

// Disable warnings emitted by gtest generated files when using typed tests
#pragma GCC diagnostic push
#ifndef __clang__
#pragma GCC diagnostic ignored "-Wsuggest-override"
#endif

using NumberTest = TestBase<NumberUniqueStore>;
using StringTest = TestBase<StringUniqueStore>;
using CStringTest = TestBase<CStringUniqueStore>;
using DoubleTest = TestBase<DoubleUniqueStore>;
using SmallOffsetNumberTest = TestBase<SmallOffsetNumberUniqueStore>;

TEST(UniqueStoreTest, trivial_and_non_trivial_types_are_tested)
{
    EXPECT_TRUE(vespalib::can_skip_destruction<NumberTest::ValueType>::value);
    EXPECT_FALSE(vespalib::can_skip_destruction<StringTest::ValueType>::value);
}

TYPED_TEST(TestBase, can_add_and_get_values)
{
    for (auto &val : this->values) {
        this->assertAdd(val);
    }
}

TYPED_TEST(TestBase, elements_are_put_on_hold_when_value_is_removed)
{
    EntryRef ref = this->add(this->values[0]);
    size_t reserved = this->get_reserved(ref);
    size_t array_size = this->get_array_size(ref);
    this->assertBufferState(ref, BufferStats().used(array_size + reserved).hold(0).dead(reserved));
    this->store.remove(ref);
    this->assertBufferState(ref, BufferStats().used(array_size + reserved).hold(array_size).dead(reserved));
}

TYPED_TEST(TestBase, elements_are_reference_counted)
{
    EntryRef ref = this->add(this->values[0]);
    EntryRef ref2 = this->add(this->values[0]);
    EXPECT_EQ(ref.ref(), ref2.ref());
    // Note: The first buffer have the first element reserved -> we expect 2 elements used here.
    size_t reserved = this->get_reserved(ref);
    size_t array_size = this->get_array_size(ref);
    this->assertBufferState(ref, BufferStats().used(array_size + reserved).hold(0).dead(reserved));
    this->store.remove(ref);
    this->assertBufferState(ref, BufferStats().used(array_size + reserved).hold(0).dead(reserved));
    this->store.remove(ref);
    this->assertBufferState(ref, BufferStats().used(array_size + reserved).hold(array_size).dead(reserved));
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
    EntryRef val0Ref = this->add(this->values[0]);
    EntryRef val1Ref = this->add(this->values[1]);
    this->remove(this->add(this->values[2]));
    this->trimHoldLists();
    size_t reserved = this->get_reserved(val0Ref);
    size_t array_size = this->get_array_size(val0Ref);
    this->assertBufferState(val0Ref, BufferStats().used(reserved + 3 * array_size).dead(reserved + array_size));
    uint32_t val1BufferId = this->getBufferId(val0Ref);

    EXPECT_EQ(2u, this->refStore.size());
    this->compactWorst();
    EXPECT_EQ(2u, this->refStore.size());
    this->assertStoreContent();

    // Buffer has been compacted
    EXPECT_NE(val1BufferId, this->getBufferId(this->getEntryRef(this->values[0])));
    // Old ref should still point to data.
    this->assertGet(val0Ref, this->values[0]);
    this->assertGet(val1Ref, this->values[1]);
    EXPECT_TRUE(this->store.bufferState(val0Ref).isOnHold());
    this->trimHoldLists();
    EXPECT_TRUE(this->store.bufferState(val0Ref).isFree());
    this->assertStoreContent();
}

TYPED_TEST(TestBase, store_can_be_instantiated_with_builder)
{
    auto builder = this->getBuilder(2);
    builder.add(this->values[0]);
    builder.add(this->values[1]);
    builder.setupRefCounts();
    EntryRef val0Ref = builder.mapEnumValueToEntryRef(1);
    EntryRef val1Ref = builder.mapEnumValueToEntryRef(2);
    size_t reserved = this->get_reserved(val0Ref);
    size_t array_size = this->get_array_size(val0Ref);
    this->assertBufferState(val0Ref, BufferStats().used(2 * array_size + reserved).dead(reserved)); // Note: First element is reserved
    EXPECT_TRUE(val0Ref.valid());
    EXPECT_TRUE(val1Ref.valid());
    EXPECT_NE(val0Ref.ref(), val1Ref.ref());
    this->assertGet(val0Ref, this->values[0]);
    this->assertGet(val1Ref, this->values[1]);
    builder.makeDictionary();
    // Align refstore with the two entries added by builder.
    this->alignRefStore(val0Ref, this->values[0], 1);
    this->alignRefStore(val1Ref, this->values[1], 1);
    EXPECT_EQ(val0Ref.ref(), this->add(this->values[0]).ref());
    EXPECT_EQ(val1Ref.ref(), this->add(this->values[1]).ref());
}

TYPED_TEST(TestBase, store_can_be_enumerated)
{
    EntryRef val0Ref = this->add(this->values[0]);
    EntryRef val1Ref = this->add(this->values[1]);
    this->remove(this->add(this->values[2]));
    this->trimHoldLists();

    auto enumerator = this->getEnumerator();
    std::vector<uint32_t> refs;
    enumerator.foreach_key([&](EntryRef ref) { refs.push_back(ref.ref()); });
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

#pragma GCC diagnostic pop

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
    trimHoldLists();
    EXPECT_TRUE(std::isnan(store.get(refs[1])));
    EXPECT_TRUE(std::signbit(store.get(refs[1])));
    EXPECT_TRUE(std::isinf(store.get(refs[2])));
    EXPECT_FALSE(std::signbit(store.get(refs[2])));
    EXPECT_TRUE(std::isinf(store.get(refs[3])));
    EXPECT_TRUE(std::signbit(store.get(refs[3])));
    auto enumerator = getEnumerator();
    enumerator.enumerateValues();
    std::vector<uint32_t> enumerated;
    for (auto &ref : refs) {
        enumerated.push_back(enumerator.mapEntryRefToEnumValue(ref));
    }
    std::vector<uint32_t> exp_enumerated = { 0, 1, 4, 2, 3, 1, 4, 2 };
    EXPECT_EQ(exp_enumerated, enumerated);
}    
                
GTEST_MAIN_RUN_ALL_TESTS()
