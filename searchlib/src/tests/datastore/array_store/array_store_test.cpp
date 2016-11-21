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
using search::MemoryUsage;
using vespalib::ArrayRef;
using generation_t = vespalib::GenerationHandler::generation_t;

struct MemStats
{
    size_t _used;
    size_t _hold;
    size_t _dead;
    MemStats() : _used(0), _hold(0), _dead(0) {}
    MemStats(const MemoryUsage &usage)
        : _used(usage.usedBytes()),
          _hold(usage.allocatedBytesOnHold()),
          _dead(usage.deadBytes()) {}
    MemStats &used(size_t val) { _used += val; return *this; }
    MemStats &hold(size_t val) { _hold += val; return *this; }
    MemStats &dead(size_t val) { _dead += val; return *this; }
    MemStats &holdToDead(size_t val) {
        decHold(val);
        _dead += val;
        return *this;
    }
    MemStats &decHold(size_t val) {
        ASSERT_TRUE(_hold >= val);
        _hold -= val;
        return *this;
    }
};

template <typename EntryT, typename RefT = EntryRefT<17> >
struct Fixture
{
    using EntryRefType = RefT;
    using ArrayStoreType = ArrayStore<EntryT, RefT>;
    using LargeArray = typename ArrayStoreType::LargeArray;
    using ConstArrayRef = typename ArrayStoreType::ConstArrayRef;
    using EntryVector = std::vector<EntryT>;
    using value_type = EntryT;
    using ReferenceStore = std::map<EntryRef, EntryVector>;

    ArrayStoreType store;
    ReferenceStore refStore;
    generation_t generation;
    Fixture(uint32_t maxSmallArraySize)
        : store(maxSmallArraySize),
          refStore(),
          generation(1)
    {}
    void assertAdd(const EntryVector &input) {
        EntryRef ref = add(input);
        assertGet(ref, input);
    }
    EntryRef add(const EntryVector &input) {
        EntryRef result = store.add(ConstArrayRef(input));
        ASSERT_EQUAL(0u, refStore.count(result));
        refStore.insert(std::make_pair(result, input));
        return result;
    }
    void assertGet(EntryRef ref, const EntryVector &exp) const {
        ConstArrayRef act = store.get(ref);
        EXPECT_EQUAL(exp, EntryVector(act.begin(), act.end()));
    }
    void remove(EntryRef ref) {
        ASSERT_EQUAL(1u, refStore.count(ref));
        store.remove(ref);
        refStore.erase(ref);
    }
    void remove(const EntryVector &input) {
        remove(getEntryRef(input));
    }
    uint32_t getBufferId(EntryRef ref) const {
        return EntryRefType(ref).bufferId();
    }
    void assertBufferState(EntryRef ref, const MemStats expStats) const {
        EXPECT_EQUAL(expStats._used, store.bufferState(ref).size());
        EXPECT_EQUAL(expStats._hold, store.bufferState(ref).getHoldElems());
        EXPECT_EQUAL(expStats._dead, store.bufferState(ref).getDeadElems());
    }
    void assertMemoryUsage(const MemStats expStats) const {
        MemoryUsage act = store.getMemoryUsage();
        EXPECT_EQUAL(expStats._used, act.usedBytes());
        EXPECT_EQUAL(expStats._hold, act.allocatedBytesOnHold());
        EXPECT_EQUAL(expStats._dead, act.deadBytes());
    }
    void assertStoreContent() const {
        for (const auto &elem : refStore) {
            TEST_DO(assertGet(elem.first, elem.second));
        }
    }
    EntryRef getEntryRef(const EntryVector &input) {
        for (auto itr = refStore.begin(); itr != refStore.end(); ++itr) {
            if (itr->second == input) {
                return itr->first;
            }
        }
        return EntryRef();
    }
    void trimHoldLists() {
        store.transferHoldLists(generation++);
        store.trimHoldLists(generation);
    }
    void compactWorst() {
        ICompactionContext::UP ctx = store.compactWorst();
        std::vector<EntryRef> refs;
        for (auto itr = refStore.begin(); itr != refStore.end(); ++itr) {
            refs.push_back(itr->first);
        }
        std::vector<EntryRef> compactedRefs = refs;
        ctx->compact(ArrayRef<EntryRef>(compactedRefs));
        ReferenceStore compactedRefStore;
        for (size_t i = 0; i < refs.size(); ++i) {
            ASSERT_EQUAL(0u, compactedRefStore.count(compactedRefs[i]));
            ASSERT_EQUAL(1u, refStore.count(refs[i]));
            compactedRefStore.insert(std::make_pair(compactedRefs[i], refStore[refs[i]]));
        }
        refStore = compactedRefStore;
    }
    size_t entrySize() const { return sizeof(EntryT); }
    size_t largeArraySize() const { return sizeof(LargeArray); }
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
    TEST_DO(f.assertBufferState(ref, MemStats().used(3).hold(0)));
    f.store.remove(ref);
    TEST_DO(f.assertBufferState(ref, MemStats().used(3).hold(3)));
}

TEST_F("require that elements are put on hold when a large array is removed", NumberFixture(3))
{
    EntryRef ref = f.add({1,2,3,4});
    // Note: The first buffer have the first element reserved -> we expect 2 elements used here.
    TEST_DO(f.assertBufferState(ref, MemStats().used(2).hold(0).dead(1)));
    f.store.remove(ref);
    TEST_DO(f.assertBufferState(ref, MemStats().used(2).hold(1).dead(1)));
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

TEST_F("require that the buffer with most dead space is compacted", NumberFixture(2))
{
    EntryRef size1Ref = f.add({1});
    EntryRef size2Ref = f.add({2,2});
    EntryRef size3Ref = f.add({3,3,3});
    f.remove(f.add({5,5}));
    f.trimHoldLists();
    TEST_DO(f.assertBufferState(size1Ref, MemStats().used(1).dead(0)));
    TEST_DO(f.assertBufferState(size2Ref, MemStats().used(4).dead(2)));
    TEST_DO(f.assertBufferState(size3Ref, MemStats().used(2).dead(1))); // Note: First element is reserved
    uint32_t size1BufferId = f.getBufferId(size1Ref);
    uint32_t size2BufferId = f.getBufferId(size2Ref);
    uint32_t size3BufferId = f.getBufferId(size3Ref);

    EXPECT_EQUAL(3u, f.refStore.size());
    f.compactWorst();
    EXPECT_EQUAL(3u, f.refStore.size());
    f.assertStoreContent();

    EXPECT_EQUAL(size1BufferId, f.getBufferId(f.getEntryRef({1})));
    EXPECT_EQUAL(size3BufferId, f.getBufferId(f.getEntryRef({3,3,3})));
    // Buffer for size 2 arrays has been compacted
    EXPECT_NOT_EQUAL(size2BufferId, f.getBufferId(f.getEntryRef({2,2})));
    f.assertGet(size2Ref, {2,2}); // Old ref should still point to data.
    EXPECT_TRUE(f.store.bufferState(size2Ref).isOnHold());
    f.trimHoldLists();
    EXPECT_TRUE(f.store.bufferState(size2Ref).isFree());
}

TEST_F("require that used, onHold and dead memory usage is tracked for small arrays", NumberFixture(2))
{
    MemStats exp(f.store.getMemoryUsage());
    f.add({2,2});
    TEST_DO(f.assertMemoryUsage(exp.used(f.entrySize() * 2)));
    f.remove({2,2});
    TEST_DO(f.assertMemoryUsage(exp.hold(f.entrySize() * 2)));
    f.trimHoldLists();
    TEST_DO(f.assertMemoryUsage(exp.holdToDead(f.entrySize() * 2)));
}

TEST_F("require that used, onHold and dead memory usage is tracked for large arrays", NumberFixture(2))
{
    MemStats exp(f.store.getMemoryUsage());
    f.add({3,3,3});
    TEST_DO(f.assertMemoryUsage(exp.used(f.largeArraySize() + f.entrySize() * 3)));
    f.remove({3,3,3});
    TEST_DO(f.assertMemoryUsage(exp.hold(f.largeArraySize() + f.entrySize() * 3)));
    f.trimHoldLists();
    TEST_DO(f.assertMemoryUsage(exp.decHold(f.largeArraySize() + f.entrySize() * 3).
                                    dead(f.largeArraySize())));
}

TEST_F("require that address space usage is ratio between active buffers and number of possible buffers", NumberFixture(3))
{
    f.add({2,2});
    f.add({4,4,4});
    // All buffer types occupy 1 buffer each
    EXPECT_EQUAL(4.0, f.store.addressSpaceUsage().used());
    EXPECT_EQUAL(F1::EntryRefType::numBuffers(), f.store.addressSpaceUsage().limit());
}

TEST_MAIN() { TEST_RUN_ALL(); }
