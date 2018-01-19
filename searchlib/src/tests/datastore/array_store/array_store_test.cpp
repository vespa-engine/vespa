// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/datastore/array_store.hpp>
#include <vespa/searchlib/test/datastore/memstats.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/util/traits.h>
#include <vector>

using namespace search::datastore;
using search::MemoryUsage;
using vespalib::ArrayRef;
using generation_t = vespalib::GenerationHandler::generation_t;
using MemStats = search::datastore::test::MemStats;

constexpr float ALLOC_GROW_FACTOR = 0.2;

template <typename EntryT, typename RefT = EntryRefT<19> >
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
        : store(ArrayStoreConfig(maxSmallArraySize,
                                 ArrayStoreConfig::AllocSpec(16, RefT::offsetSize(), 8 * 1024,
                                                             ALLOC_GROW_FACTOR))),
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
    void compactWorst(bool compactMemory, bool compactAddressSpace) {
        ICompactionContext::UP ctx = store.compactWorst(compactMemory, compactAddressSpace);
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
    f.compactWorst(true, false);
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

namespace {

void testCompaction(NumberFixture &f, bool compactMemory, bool compactAddressSpace)
{
    EntryRef size1Ref = f.add({1});
    EntryRef size2Ref = f.add({2,2});
    EntryRef size3Ref = f.add({3,3,3});
    f.remove(f.add({5,5,5}));
    f.remove(f.add({6}));
    f.remove(f.add({7}));
    f.trimHoldLists();
    TEST_DO(f.assertBufferState(size1Ref, MemStats().used(3).dead(2)));
    TEST_DO(f.assertBufferState(size2Ref, MemStats().used(2).dead(0)));
    TEST_DO(f.assertBufferState(size3Ref, MemStats().used(6).dead(3)));
    uint32_t size1BufferId = f.getBufferId(size1Ref);
    uint32_t size2BufferId = f.getBufferId(size2Ref);
    uint32_t size3BufferId = f.getBufferId(size3Ref);

    EXPECT_EQUAL(3u, f.refStore.size());
    f.compactWorst(compactMemory, compactAddressSpace);
    EXPECT_EQUAL(3u, f.refStore.size());
    f.assertStoreContent();

    if (compactMemory) {
        EXPECT_NOT_EQUAL(size3BufferId, f.getBufferId(f.getEntryRef({3,3,3})));
    } else {
        EXPECT_EQUAL(size3BufferId, f.getBufferId(f.getEntryRef({3,3,3})));
    }
    if (compactAddressSpace) {
        EXPECT_NOT_EQUAL(size1BufferId, f.getBufferId(f.getEntryRef({1})));
    } else {
        EXPECT_EQUAL(size1BufferId, f.getBufferId(f.getEntryRef({1})));
    }
    EXPECT_EQUAL(size2BufferId, f.getBufferId(f.getEntryRef({2,2})));
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
    f.trimHoldLists();
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

TEST_F("require that compactWorst selects on only memory", NumberFixture(3)) {
    testCompaction(f, true, false);
}

TEST_F("require that compactWorst selects on only address space", NumberFixture(3)) {
    testCompaction(f, false, true);
}

TEST_F("require that compactWorst selects on both memory and address space", NumberFixture(3)) {
    testCompaction(f, true, true);
}

TEST_F("require that compactWorst selects on neither memory nor address space", NumberFixture(3)) {
    testCompaction(f, false, false);
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

TEST_F("require that address space usage is ratio between used clusters and number of possible clusters", NumberFixture(3))
{
    f.add({2,2});
    f.add({4,4,4});
    // 1 cluster is reserved (buffer 0, offset 0).
    EXPECT_EQUAL(3u, f.store.addressSpaceUsage().used());
    EXPECT_EQUAL(1u, f.store.addressSpaceUsage().dead());
    size_t fourgig = (1ull << 32);
    /*
     * Expected limit is sum of allocated clusters for active buffers and
     * potentially allocated clusters for free buffers. If all buffers were
     * free then the limit would be 4 Gi.  Then we subtract clusters for 4
     * buffers that are not free, and add their actual number of allocated
     * clusters (16 clusters per buffer).
     */
    size_t expLimit = fourgig - 4 * F1::EntryRefType::offsetSize() + 4 * 16;
    EXPECT_EQUAL(static_cast<double>(2)/ expLimit, f.store.addressSpaceUsage().usage());
    EXPECT_EQUAL(expLimit, f.store.addressSpaceUsage().limit());
}

TEST_MAIN() { TEST_RUN_ALL(); }
