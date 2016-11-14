// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("array_store_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/util/traits.h>
#include <vespa/searchlib/datastore/array_store.hpp>
#include <vector>

using namespace search::datastore;

template <typename EntryT, typename RefT = EntryRefT<17> >
struct Fixture
{
    using EntryRefType = RefT;
    using ArrayStoreType = ArrayStore<EntryT, RefT>;
    using ConstArrayRef = typename ArrayStoreType::ConstArrayRef;
    using EntryVector = std::vector<EntryT>;
    using value_type = EntryT;
    using ReferenceStore = std::map<EntryRef, EntryVector>;

    ArrayStoreType store;
    ReferenceStore refStore;
    Fixture(uint32_t maxSmallArraySize)
        : store(maxSmallArraySize),
          refStore()
    {}
    void assertAdd(const EntryVector &input) {
        EntryRef ref = add(input);
        ConstArrayRef output = store.get(ref);
        EXPECT_EQUAL(input, EntryVector(output.begin(), output.end()));
    }
    EntryRef add(const EntryVector &input) {
        EntryRef result = store.add(ConstArrayRef(input));
        EXPECT_EQUAL(0u, refStore.count(result));
        refStore.insert(std::make_pair(result, input));
        return result;
    }
    uint32_t getBufferId(EntryRef ref) const {
        return EntryRefType(ref).bufferId();
    }
    void assertBufferState(EntryRef ref, size_t expUsedElems, size_t expHoldElems) const {
        EXPECT_EQUAL(expUsedElems, store.bufferState(ref)._usedElems);
        EXPECT_EQUAL(expHoldElems, store.bufferState(ref)._holdElems);
    }
    void assertStoreContent() const {
        for (const auto &elem : refStore) {
            const EntryVector &exp = elem.second;
            ConstArrayRef act = store.get(elem.first);
            EXPECT_EQUAL(exp, EntryVector(act.begin(), act.end()));
        }
    }
};

using NumberFixture = Fixture<uint32_t>;
using StringFixture = Fixture<std::string>;
using SmallOffsetNumberFixture = Fixture<uint32_t, EntryRefT<10>>;

TEST("require that we test with trivial and non-trivial types")
{
    EXPECT_TRUE(vespalib::can_skip_destruction<NumberFixture::value_type>::value);
    EXPECT_FALSE(vespalib::can_skip_destruction<StringFixture::value_type>::value);
}

TEST_F("require that we can add and get small arrays of trivial type", NumberFixture(3))
{
    TEST_DO(f.assertAdd({}));
    TEST_DO(f.assertAdd({1}));
    TEST_DO(f.assertAdd({2,3}));
    TEST_DO(f.assertAdd({3,4,5}));
}

TEST_F("require that we can add and get small arrays of non-trivial type", StringFixture(3))
{
    TEST_DO(f.assertAdd({}));
    TEST_DO(f.assertAdd({"aa"}));
    TEST_DO(f.assertAdd({"bbb", "ccc"}));
    TEST_DO(f.assertAdd({"ddd", "eeee", "fffff"}));
}

TEST_F("require that we can add and get large arrays of simple type", NumberFixture(3))
{
    TEST_DO(f.assertAdd({1,2,3,4}));
    TEST_DO(f.assertAdd({2,3,4,5,6}));
}

TEST_F("require that we can add and get large arrays of non-trivial type", StringFixture(3))
{
    TEST_DO(f.assertAdd({"aa", "bb", "cc", "dd"}));
    TEST_DO(f.assertAdd({"ddd", "eee", "ffff", "gggg", "hhhh"}));
}

TEST_F("require that elements are put on hold when a small array is removed", NumberFixture(3))
{
    EntryRef ref = f.add({1,2,3});
    TEST_DO(f.assertBufferState(ref, 3, 0));
    f.store.remove(ref);
    TEST_DO(f.assertBufferState(ref, 3, 3));
}

TEST_F("require that elements are put on hold when a large array is removed", NumberFixture(3))
{
    EntryRef ref = f.add({1,2,3,4});
    // Note: The first buffer have the first element reserved -> we expect 2 elements used here.
    TEST_DO(f.assertBufferState(ref, 2, 0));
    f.store.remove(ref);
    TEST_DO(f.assertBufferState(ref, 2, 1));
}

TEST_F("require that new underlying buffer is allocated when current is full", SmallOffsetNumberFixture(3))
{
    uint32_t firstBufferId = f.getBufferId(f.add({1,1}));
    for (uint32_t i = 0; i < (F1::EntryRefType::offsetSize() - 1); ++i) {
        uint32_t bufferId = f.getBufferId(f.add({i, i+1}));
        EXPECT_EQUAL(firstBufferId, bufferId);
    }
    TEST_DO(f.assertStoreContent());

    uint32_t secondBufferId = f.getBufferId(f.add({2,2}));
    EXPECT_NOT_EQUAL(firstBufferId, secondBufferId);
    for (uint32_t i = 0; i < 10u; ++i) {
        uint32_t bufferId = f.getBufferId(f.add({i+2,i}));
        EXPECT_EQUAL(secondBufferId, bufferId);
    }
    TEST_DO(f.assertStoreContent());
}

TEST_MAIN() { TEST_RUN_ALL(); }
