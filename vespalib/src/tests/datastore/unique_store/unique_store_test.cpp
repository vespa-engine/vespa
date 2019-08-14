// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/datastore/unique_store.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/datastore/memstats.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/util/traits.h>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("unique_store_test");

using namespace search::datastore;
using vespalib::MemoryUsage;
using vespalib::ArrayRef;
using generation_t = vespalib::GenerationHandler::generation_t;
using MemStats = search::datastore::test::MemStats;

template <typename EntryT, typename RefT = EntryRefT<22> >
struct TestBase : public ::testing::Test {
    using EntryRefType = RefT;
    using UniqueStoreType = UniqueStore<EntryT, RefT>;
    using value_type = EntryT;
    using ReferenceStore = std::map<EntryRef, std::pair<EntryT,uint32_t>>;

    UniqueStoreType store;
    ReferenceStore refStore;
    generation_t generation;
    TestBase()
        : store(),
          refStore(),
          generation(1)
    {}
    void assertAdd(const EntryT &input) {
        EntryRef ref = add(input);
        assertGet(ref, input);
    }
    EntryRef add(const EntryT &input) {
        UniqueStoreAddResult addResult = store.add(input);
        EntryRef result = addResult.ref();
        auto insres = refStore.insert(std::make_pair(result, std::make_pair(input, 1u)));
        EXPECT_EQ(insres.second, addResult.inserted());
        if (!insres.second) {
            ++insres.first->second.second;
        }
        return result;
    }
    void alignRefStore(EntryRef ref, const EntryT &input, uint32_t refcnt) {
        if (refcnt > 0) {
            auto insres = refStore.insert(std::make_pair(ref, std::make_pair(input, refcnt)));
            if (!insres.second) {
                insres.first->second.second = refcnt;
            }
        } else {
            refStore.erase(ref);
        }
    }
    void assertGet(EntryRef ref, const EntryT &exp) const {
        EntryT act = store.get(ref);
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
    void remove(const EntryT &input) {
        remove(getEntryRef(input));
    }
    uint32_t getBufferId(EntryRef ref) const {
        return EntryRefType(ref).bufferId();
    }
    void assertBufferState(EntryRef ref, const MemStats expStats) const {
        EXPECT_EQ(expStats._used, store.bufferState(ref).size());
        EXPECT_EQ(expStats._hold, store.bufferState(ref).getHoldElems());
        EXPECT_EQ(expStats._dead, store.bufferState(ref).getDeadElems());
    }
    void assertMemoryUsage(const MemStats expStats) const {
        MemoryUsage act = store.getMemoryUsage();
        EXPECT_EQ(expStats._used, act.usedBytes());
        EXPECT_EQ(expStats._hold, act.allocatedBytesOnHold());
        EXPECT_EQ(expStats._dead, act.deadBytes());
    }
    void assertStoreContent() const {
        for (const auto &elem : refStore) {
            assertGet(elem.first, elem.second.first);
        }
    }
    EntryRef getEntryRef(const EntryT &input) {
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
        ICompactionContext::UP ctx = store.compactWorst();
        std::vector<EntryRef> refs;
        for (const auto &elem : refStore) {
            refs.push_back(elem.first);
        }
        refs.push_back(EntryRef());
        std::vector<EntryRef> compactedRefs = refs;
        ctx->compact(ArrayRef<EntryRef>(compactedRefs));
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
    size_t entrySize() const { return sizeof(EntryT); }
    auto getBuilder(uint32_t uniqueValuesHint) { return store.getBuilder(uniqueValuesHint); }
    auto getSaver() { return store.getSaver(); }
};

using NumberTest = TestBase<uint32_t>;
using StringTest = TestBase<std::string>;
using SmallOffsetNumberTest = TestBase<uint32_t, EntryRefT<10>>;

TEST(UniqueStoreTest, trivial_and_non_trivial_types_are_tested)
{
    EXPECT_TRUE(vespalib::can_skip_destruction<NumberTest::value_type>::value);
    EXPECT_FALSE(vespalib::can_skip_destruction<StringTest::value_type>::value);
}

TEST_F(NumberTest, can_add_and_get_values_of_trivial_type)
{
    assertAdd(1);
    assertAdd(2);
    assertAdd(3);
    assertAdd(1);
}

TEST_F(StringTest, can_add_and_get_values_of_non_trivial_type)
{
    assertAdd("aa");
    assertAdd("bbb");
    assertAdd("ccc");
    assertAdd("aa");
}

TEST_F(NumberTest, elements_are_put_on_hold_when_value_is_removed)
{
    EntryRef ref = add(1);
    // Note: The first buffer have the first element reserved -> we expect 2 elements used here.
    assertBufferState(ref, MemStats().used(2).hold(0).dead(1));
    store.remove(ref);
    assertBufferState(ref, MemStats().used(2).hold(1).dead(1));
}

TEST_F(NumberTest, elements_are_reference_counted)
{
    EntryRef ref = add(1);
    EntryRef ref2 = add(1);
    EXPECT_EQ(ref.ref(), ref2.ref());
    // Note: The first buffer have the first element reserved -> we expect 2 elements used here.
    assertBufferState(ref, MemStats().used(2).hold(0).dead(1));
    store.remove(ref);
    assertBufferState(ref, MemStats().used(2).hold(0).dead(1));
    store.remove(ref);
    assertBufferState(ref, MemStats().used(2).hold(1).dead(1));
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

TEST_F(NumberTest, store_can_be_compacted)
{
    EntryRef val1Ref = add(1);
    EntryRef val2Ref = add(2);
    remove(add(4));
    trimHoldLists();
    assertBufferState(val1Ref, MemStats().used(4).dead(2)); // Note: First element is reserved
    uint32_t val1BufferId = getBufferId(val1Ref);

    EXPECT_EQ(2u, refStore.size());
    compactWorst();
    EXPECT_EQ(2u, refStore.size());
    assertStoreContent();

    // Buffer has been compacted
    EXPECT_NE(val1BufferId, getBufferId(getEntryRef(1)));
    // Old ref should still point to data.
    assertGet(val1Ref, 1);
    assertGet(val2Ref, 2);
    EXPECT_TRUE(store.bufferState(val1Ref).isOnHold());
    trimHoldLists();
    EXPECT_TRUE(store.bufferState(val1Ref).isFree());
    assertStoreContent();
}

TEST_F(NumberTest, store_can_be_instantiated_with_builder)
{
    auto builder = getBuilder(2);
    builder.add(10);
    builder.add(20);
    builder.setupRefCounts();
    EntryRef val10Ref = builder.mapEnumValueToEntryRef(1);
    EntryRef val20Ref = builder.mapEnumValueToEntryRef(2);
    assertBufferState(val10Ref, MemStats().used(3).dead(1)); // Note: First element is reserved
    EXPECT_TRUE(val10Ref.valid());
    EXPECT_TRUE(val20Ref.valid());
    EXPECT_NE(val10Ref.ref(), val20Ref.ref());
    assertGet(val10Ref, 10);
    assertGet(val20Ref, 20);
    builder.makeDictionary();
    // Align refstore with the two entries added by builder.
    alignRefStore(val10Ref, 10, 1);
    alignRefStore(val20Ref, 20, 1);
    EXPECT_EQ(val10Ref.ref(), add(10).ref());
    EXPECT_EQ(val20Ref.ref(), add(20).ref());
}

TEST_F(NumberTest, store_can_be_saved)
{
    EntryRef val10Ref = add(10);
    EntryRef val20Ref = add(20);
    remove(add(40));
    trimHoldLists();

    auto saver = getSaver();
    std::vector<uint32_t> refs;
    saver.foreach_key([&](EntryRef ref) { refs.push_back(ref.ref()); });
    std::vector<uint32_t> expRefs;
    expRefs.push_back(val10Ref.ref());
    expRefs.push_back(val20Ref.ref());
    EXPECT_EQ(expRefs, refs);
    saver.enumerateValues();
    uint32_t invalidEnum = saver.mapEntryRefToEnumValue(EntryRef());
    uint32_t enumValue10 = saver.mapEntryRefToEnumValue(val10Ref);
    uint32_t enumValue20 = saver.mapEntryRefToEnumValue(val20Ref);
    EXPECT_EQ(0u, invalidEnum);
    EXPECT_EQ(1u, enumValue10);
    EXPECT_EQ(2u, enumValue20);
}

GTEST_MAIN_RUN_ALL_TESTS()
