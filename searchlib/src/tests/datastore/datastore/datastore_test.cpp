// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/datastore/datastore.h>
#include <vespa/searchlib/datastore/datastore.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/insertion_operators.h>

#include <vespa/log/log.h>
LOG_SETUP("datastore_test");

namespace search::datastore {

using vespalib::alloc::MemoryAllocator;

struct IntReclaimer
{
    static void reclaim(int *) {}
};

class MyStore : public DataStore<int, EntryRefT<3, 2> > {
private:
    using ParentType = DataStore<int, EntryRefT<3, 2> >;
    using ParentType::_activeBufferIds;
public:
    MyStore() {}

    void
    holdBuffer(uint32_t bufferId)
    {
        ParentType::holdBuffer(bufferId);
    }

    void
    holdElem(EntryRef ref, uint64_t len)
    {
        ParentType::holdElem(ref, len);
    }

    void
    transferHoldLists(generation_t generation)
    {
        ParentType::transferHoldLists(generation);
    }

    void trimElemHoldList(generation_t usedGen) override {
        ParentType::trimElemHoldList(usedGen);
    }
    void incDead(EntryRef ref, uint64_t dead) {
        ParentType::incDead(ref, dead);
    }
    void ensureBufferCapacity(size_t sizeNeeded) {
        ParentType::ensureBufferCapacity(0, sizeNeeded);
    }
    void enableFreeLists() {
        ParentType::enableFreeLists();
    }

    void
    switchActiveBuffer()
    {
        ParentType::switchActiveBuffer(0, 0u);
    }
    size_t activeBufferId() const { return _activeBufferIds[0]; }
};


using GrowthStats = std::vector<int>;

constexpr float ALLOC_GROW_FACTOR = 0.4;
constexpr size_t HUGE_PAGE_CLUSTER_SIZE = (MemoryAllocator::HUGEPAGE_SIZE / sizeof(int));

template <typename DataType, typename RefType>
class GrowStore
{
    using Store = DataStoreT<RefType>;
    Store _store;
    BufferType<DataType> _firstType;
    BufferType<DataType> _type;
    uint32_t _typeId;
public:
    GrowStore(size_t clusterSize, size_t minClusters, size_t maxClusters, size_t numClustersForNewBuffer)
        : _store(),
          _firstType(1, 1, maxClusters, 0, ALLOC_GROW_FACTOR),
          _type(clusterSize, minClusters, maxClusters, numClustersForNewBuffer, ALLOC_GROW_FACTOR),
          _typeId(0)
    {
        (void) _store.addType(&_firstType);
        _typeId = _store.addType(&_type);
        _store.initActiveBuffers();
    }
    ~GrowStore() { _store.dropBuffers(); }

    Store &store() { return _store; }
    uint32_t typeId() const { return _typeId; }

    GrowthStats getGrowthStats(size_t bufs) {
        GrowthStats sizes;
        int prevBufferId = -1;
        while (sizes.size() < bufs) {
            RefType iRef = (_type.getClusterSize() == 1) ?
                           (_store.template allocator<DataType>(_typeId).alloc().ref) :
                           (_store.template allocator<DataType>(_typeId).allocArray(_type.getClusterSize()).ref);
            int bufferId = iRef.bufferId();
            if (bufferId != prevBufferId) {
                if (prevBufferId >= 0) {
                    const auto &state = _store.getBufferState(prevBufferId);
                    sizes.push_back(state.capacity());
                }
                prevBufferId = bufferId;
            }
        }
        return sizes;
    }
    GrowthStats getFirstBufGrowStats() {
        GrowthStats sizes;
        int i = 0;
        int prevBuffer = -1;
        size_t prevAllocated = _store.getMemoryUsage().allocatedBytes();
        for (;;) {
            RefType iRef = _store.template allocator<DataType>(_typeId).alloc().ref;
            size_t allocated = _store.getMemoryUsage().allocatedBytes();
            if (allocated != prevAllocated) {
                sizes.push_back(i);
                prevAllocated = allocated;
            }
            int buffer = iRef.bufferId();
            if (buffer != prevBuffer) {
                if (prevBuffer >= 0) {
                    return sizes;
                }
                prevBuffer = buffer;
            }
            ++i;
        }
    }
    MemoryUsage getMemoryUsage() const { return _store.getMemoryUsage(); }
};

using MyRef = MyStore::RefType;

void
assertMemStats(const DataStoreBase::MemStats &exp,
               const DataStoreBase::MemStats &act)
{
    EXPECT_EQ(exp._allocElems, act._allocElems);
    EXPECT_EQ(exp._usedElems, act._usedElems);
    EXPECT_EQ(exp._deadElems, act._deadElems);
    EXPECT_EQ(exp._holdElems, act._holdElems);
    EXPECT_EQ(exp._freeBuffers, act._freeBuffers);
    EXPECT_EQ(exp._activeBuffers, act._activeBuffers);
    EXPECT_EQ(exp._holdBuffers, act._holdBuffers);
}

TEST(DataStoreTest, require_that_entry_ref_is_working)
{
    using MyRefType = EntryRefT<22>;
    EXPECT_EQ(4194304u, MyRefType::offsetSize());
    EXPECT_EQ(1024u, MyRefType::numBuffers());
    {
        MyRefType r(0, 0);
        EXPECT_EQ(0u, r.offset());
        EXPECT_EQ(0u, r.bufferId());
    }
    {
        MyRefType r(237, 13);
        EXPECT_EQ(237u, r.offset());
        EXPECT_EQ(13u, r.bufferId());
    }
    {
        MyRefType r(4194303, 1023);
        EXPECT_EQ(4194303u, r.offset());
        EXPECT_EQ(1023u, r.bufferId());
    }
    {
        MyRefType r1(6498, 76);
        MyRefType r2(r1);
        EXPECT_EQ(r1.offset(), r2.offset());
        EXPECT_EQ(r1.bufferId(), r2.bufferId());
    }
}

TEST(DataStoreTest, require_that_aligned_entry_ref_is_working)
{
    using MyRefType = AlignedEntryRefT<22, 2>; // 4 byte alignement
    EXPECT_EQ(4 * 4194304u, MyRefType::offsetSize());
    EXPECT_EQ(1024u, MyRefType::numBuffers());
    EXPECT_EQ(0u, MyRefType::align(0));
    EXPECT_EQ(4u, MyRefType::align(1));
    EXPECT_EQ(4u, MyRefType::align(2));
    EXPECT_EQ(4u, MyRefType::align(3));
    EXPECT_EQ(4u, MyRefType::align(4));
    EXPECT_EQ(8u, MyRefType::align(5));
    {
        MyRefType r(0, 0);
        EXPECT_EQ(0u, r.offset());
        EXPECT_EQ(0u, r.bufferId());
    }
    {
        MyRefType r(237, 13);
        EXPECT_EQ(MyRefType::align(237), r.offset());
        EXPECT_EQ(13u, r.bufferId());
    }
    {
        MyRefType r(MyRefType::offsetSize() - 4, 1023);
        EXPECT_EQ(MyRefType::align(MyRefType::offsetSize() - 4), r.offset());
        EXPECT_EQ(1023u, r.bufferId());
    }
}

TEST(DataStoreTest, require_that_entries_can_be_added_and_retrieved)
{
    using IntStore = DataStore<int>;
    IntStore ds;
    EntryRef r1 = ds.addEntry(10);
    EntryRef r2 = ds.addEntry(20);
    EntryRef r3 = ds.addEntry(30);
    EXPECT_EQ(1u, IntStore::RefType(r1).offset());
    EXPECT_EQ(2u, IntStore::RefType(r2).offset());
    EXPECT_EQ(3u, IntStore::RefType(r3).offset());
    EXPECT_EQ(0u, IntStore::RefType(r1).bufferId());
    EXPECT_EQ(0u, IntStore::RefType(r2).bufferId());
    EXPECT_EQ(0u, IntStore::RefType(r3).bufferId());
    EXPECT_EQ(10, ds.getEntry(r1));
    EXPECT_EQ(20, ds.getEntry(r2));
    EXPECT_EQ(30, ds.getEntry(r3));
}

TEST(DataStoreTest, require_that_add_entry_triggers_change_of_buffer)
{
    using Store = DataStore<uint64_t, EntryRefT<10, 10> >;
    Store s;
    uint64_t num = 0;
    uint32_t lastId = 0;
    uint64_t lastNum = 0;
    for (;;++num) {
        EntryRef r = s.addEntry(num);
        EXPECT_EQ(num, s.getEntry(r));
        uint32_t bufferId = Store::RefType(r).bufferId();
        if (bufferId > lastId) {
            LOG(info, "Changed to bufferId %u after %" PRIu64 " nums", bufferId, num);
            EXPECT_EQ(Store::RefType::offsetSize() - (lastId == 0), num - lastNum);
            lastId = bufferId;
            lastNum = num;
        }
        if (bufferId == 2) {
            break;
        }
    }
    EXPECT_EQ(Store::RefType::offsetSize() * 2 - 1, num);
    LOG(info, "Added %" PRIu64 " nums in 2 buffers", num);
}

TEST(DataStoreTest, require_that_we_can_hold_and_trim_buffers)
{
    MyStore s;
    EXPECT_EQ(0u, MyRef(s.addEntry(1)).bufferId());
    s.switchActiveBuffer();
    EXPECT_EQ(1u, s.activeBufferId());
    s.holdBuffer(0); // hold last buffer
    s.transferHoldLists(10);

    EXPECT_EQ(1u, MyRef(s.addEntry(2)).bufferId());
    s.switchActiveBuffer();
    EXPECT_EQ(2u, s.activeBufferId());
    s.holdBuffer(1); // hold last buffer
    s.transferHoldLists(20);

    EXPECT_EQ(2u, MyRef(s.addEntry(3)).bufferId());
    s.switchActiveBuffer();
    EXPECT_EQ(3u, s.activeBufferId());
    s.holdBuffer(2); // hold last buffer
    s.transferHoldLists(30);

    EXPECT_EQ(3u, MyRef(s.addEntry(4)).bufferId());
    s.holdBuffer(3); // hold current buffer
    s.transferHoldLists(40);

    EXPECT_TRUE(s.getBufferState(0).size() != 0);
    EXPECT_TRUE(s.getBufferState(1).size() != 0);
    EXPECT_TRUE(s.getBufferState(2).size() != 0);
    EXPECT_TRUE(s.getBufferState(3).size() != 0);
    s.trimHoldLists(11);
    EXPECT_TRUE(s.getBufferState(0).size() == 0);
    EXPECT_TRUE(s.getBufferState(1).size() != 0);
    EXPECT_TRUE(s.getBufferState(2).size() != 0);
    EXPECT_TRUE(s.getBufferState(3).size() != 0);

    s.switchActiveBuffer();
    EXPECT_EQ(0u, s.activeBufferId());
    EXPECT_EQ(0u, MyRef(s.addEntry(5)).bufferId());
    s.trimHoldLists(41);
    EXPECT_TRUE(s.getBufferState(0).size() != 0);
    EXPECT_TRUE(s.getBufferState(1).size() == 0);
    EXPECT_TRUE(s.getBufferState(2).size() == 0);
    EXPECT_TRUE(s.getBufferState(3).size() == 0);
}

TEST(DataStoreTest, require_that_we_can_hold_and_trim_elements)
{
    MyStore s;
    MyRef r1 = s.addEntry(1);
    s.holdElem(r1, 1);
    s.transferHoldLists(10);
    MyRef r2 = s.addEntry(2);
    s.holdElem(r2, 1);
    s.transferHoldLists(20);
    MyRef r3 = s.addEntry(3);
    s.holdElem(r3, 1);
    s.transferHoldLists(30);
    EXPECT_EQ(1, s.getEntry(r1));
    EXPECT_EQ(2, s.getEntry(r2));
    EXPECT_EQ(3, s.getEntry(r3));
    s.trimElemHoldList(11);
    EXPECT_EQ(0, s.getEntry(r1));
    EXPECT_EQ(2, s.getEntry(r2));
    EXPECT_EQ(3, s.getEntry(r3));
    s.trimElemHoldList(31);
    EXPECT_EQ(0, s.getEntry(r1));
    EXPECT_EQ(0, s.getEntry(r2));
    EXPECT_EQ(0, s.getEntry(r3));
}

using IntHandle = Handle<int>;

MyRef
to_ref(IntHandle handle)
{
    return MyRef(handle.ref);
}

std::ostream&
operator<<(std::ostream &os, const IntHandle &rhs)
{
    MyRef ref(rhs.ref);
    os << "{ref.bufferId=" << ref.bufferId() << ", ref.offset=" << ref.offset() << ", data=" << rhs.data << "}";
    return os;
}

void
expect_successive_handles(const IntHandle &first, const IntHandle &second)
{
    EXPECT_EQ(to_ref(first).offset() + 1, to_ref(second).offset());
}

TEST(DataStoreTest, require_that_we_can_use_free_lists)
{
    MyStore s;
    s.enableFreeLists();
    auto allocator = s.freeListAllocator<IntReclaimer>();
    auto h1 = allocator.alloc(1);
    s.holdElem(h1.ref, 1);
    s.transferHoldLists(10);
    auto h2 = allocator.alloc(2);
    expect_successive_handles(h1, h2);
    s.holdElem(h2.ref, 1);
    s.transferHoldLists(20);
    s.trimElemHoldList(11);
    auto h3 = allocator.alloc(3); // reuse h1.ref
    EXPECT_EQ(h1, h3);
    auto h4 = allocator.alloc(4);
    expect_successive_handles(h2, h4);
    s.trimElemHoldList(21);
    auto h5 = allocator.alloc(5); // reuse h2.ref
    EXPECT_EQ(h2, h5);
    auto h6 = allocator.alloc(6);
    expect_successive_handles(h4, h6);
    EXPECT_EQ(3, s.getEntry(h1.ref));
    EXPECT_EQ(5, s.getEntry(h2.ref));
    EXPECT_EQ(3, s.getEntry(h3.ref));
    EXPECT_EQ(4, s.getEntry(h4.ref));
    EXPECT_EQ(5, s.getEntry(h5.ref));
    EXPECT_EQ(6, s.getEntry(h6.ref));
}

TEST(DataStoreTest, require_that_we_can_use_free_lists_with_raw_allocator)
{
    GrowStore<int, MyRef> grow_store(3, 64, 64, 64);
    auto &s = grow_store.store();
    s.enableFreeLists();
    auto allocator = s.freeListRawAllocator<int>(grow_store.typeId());

    auto h1 = allocator.alloc(3);
    auto h2 = allocator.alloc(3);
    expect_successive_handles(h1, h2);
    s.holdElem(h1.ref, 3);
    s.holdElem(h2.ref, 3);
    s.transferHoldLists(10);
    s.trimElemHoldList(11);

    auto h3 = allocator.alloc(3); // reuse h2.ref from free list
    EXPECT_EQ(h2, h3);

    auto h4 = allocator.alloc(3); // reuse h1.ref from free list
    EXPECT_EQ(h1, h4);

    auto h5 = allocator.alloc(3);
    expect_successive_handles(h2, h5);
    expect_successive_handles(h3, h5);
}

TEST(DataStoreTest, require_that_memory_stats_are_calculated)
{
    MyStore s;
    DataStoreBase::MemStats m;
    m._allocElems = MyRef::offsetSize();
    m._usedElems = 1; // ref = 0 is reserved
    m._deadElems = 1; // ref = 0 is reserved
    m._holdElems = 0;
    m._activeBuffers = 1;
    m._freeBuffers = MyRef::numBuffers() - 1;
    m._holdBuffers = 0;
    assertMemStats(m, s.getMemStats());

    // add entry
    MyRef r = s.addEntry(10);
    m._usedElems++;
    assertMemStats(m, s.getMemStats());

    // inc dead
    s.incDead(r, 1);
    m._deadElems++;
    assertMemStats(m, s.getMemStats());

    // hold buffer
    s.addEntry(20);
    s.addEntry(30);
    s.holdBuffer(r.bufferId());
    s.transferHoldLists(100);
    m._usedElems += 2;
    m._holdElems += 2; // used - dead
    m._activeBuffers--;
    m._holdBuffers++;
    assertMemStats(m, s.getMemStats());

    // new active buffer
    s.switchActiveBuffer();
    s.addEntry(40);
    m._allocElems += MyRef::offsetSize();
    m._usedElems++;
    m._activeBuffers++;
    m._freeBuffers--;

    // trim hold buffer
    s.trimHoldLists(101);
    m._allocElems -= MyRef::offsetSize();
    m._usedElems = 1;
    m._deadElems = 0;
    m._holdElems = 0;
    m._freeBuffers = MyRef::numBuffers() - 1;
    m._holdBuffers = 0;
    assertMemStats(m, s.getMemStats());
}

TEST(DataStoreTest, require_that_memory_usage_is_calculated)
{
    MyStore s;
    MyRef r = s.addEntry(10);
    s.addEntry(20);
    s.addEntry(30);
    s.addEntry(40);
    s.incDead(r, 1);
    s.holdBuffer(r.bufferId());
    s.transferHoldLists(100);
    MemoryUsage m = s.getMemoryUsage();
    EXPECT_EQ(MyRef::offsetSize() * sizeof(int), m.allocatedBytes());
    EXPECT_EQ(5 * sizeof(int), m.usedBytes());
    EXPECT_EQ(2 * sizeof(int), m.deadBytes());
    EXPECT_EQ(3 * sizeof(int), m.allocatedBytesOnHold());
    s.trimHoldLists(101);
}

TEST(DataStoreTest, require_that_we_can_disable_elemement_hold_list)
{
    MyStore s;
    MyRef r1 = s.addEntry(10);
    MyRef r2 = s.addEntry(20);
    MyRef r3 = s.addEntry(30);
    (void) r3;
    MemoryUsage m = s.getMemoryUsage();
    EXPECT_EQ(MyRef::offsetSize() * sizeof(int), m.allocatedBytes());
    EXPECT_EQ(4 * sizeof(int), m.usedBytes());
    EXPECT_EQ(1 * sizeof(int), m.deadBytes());
    EXPECT_EQ(0 * sizeof(int), m.allocatedBytesOnHold());
    s.holdElem(r1, 1);
    m = s.getMemoryUsage();
    EXPECT_EQ(MyRef::offsetSize() * sizeof(int), m.allocatedBytes());
    EXPECT_EQ(4 * sizeof(int), m.usedBytes());
    EXPECT_EQ(1 * sizeof(int), m.deadBytes());
    EXPECT_EQ(1 * sizeof(int), m.allocatedBytesOnHold());
    s.disableElemHoldList();
    s.holdElem(r2, 1);
    m = s.getMemoryUsage();
    EXPECT_EQ(MyRef::offsetSize() * sizeof(int), m.allocatedBytes());
    EXPECT_EQ(4 * sizeof(int), m.usedBytes());
    EXPECT_EQ(2 * sizeof(int), m.deadBytes());
    EXPECT_EQ(1 * sizeof(int), m.allocatedBytesOnHold());
    s.transferHoldLists(100);
    s.trimHoldLists(101);
}

using IntGrowStore = GrowStore<int, EntryRefT<24>>;

namespace {

void assertGrowStats(GrowthStats expSizes,
                     GrowthStats expFirstBufSizes,
                     size_t expInitMemUsage,
                     size_t minClusters, size_t numClustersForNewBuffer, size_t maxClusters = 128)
{
    EXPECT_EQ(expSizes, IntGrowStore(1, minClusters, maxClusters, numClustersForNewBuffer).getGrowthStats(expSizes.size()));
    EXPECT_EQ(expFirstBufSizes, IntGrowStore(1, minClusters, maxClusters, numClustersForNewBuffer).getFirstBufGrowStats());
    EXPECT_EQ(expInitMemUsage, IntGrowStore(1, minClusters, maxClusters, numClustersForNewBuffer).getMemoryUsage().allocatedBytes());
}

}

TEST(DataStoreTest, require_that_buffer_growth_works)
{
    // Always switch to new buffer, min size 4
    assertGrowStats({ 4, 4, 4, 4, 8, 16, 16, 32, 64, 64 },
                    { 4 }, 20, 4, 0);
    // Resize if buffer size is less than 4, min size 0
    assertGrowStats({ 4, 4, 4, 4, 8, 16, 16, 32, 64, 64 },
                    { 0, 1, 2, 4 }, 4, 0, 4);
    // Always switch to new buffer, min size 16
    assertGrowStats({ 16, 16, 16, 32, 32, 64, 128, 128, 128 },
                    { 16 }, 68, 16, 0);
    // Resize if buffer size is less than 16, min size 0
    assertGrowStats({ 16, 16, 16, 32, 32, 64, 128, 128, 128 },
                    { 0, 1, 2, 4, 8, 16 }, 4, 0, 16);
    // Resize if buffer size is less than 16, min size 4
    assertGrowStats({ 16, 16, 16, 32, 32, 64, 128, 128, 128 },
                    { 4, 8, 16 }, 20, 4, 16);
    // Always switch to new buffer, min size 0
    assertGrowStats({ 1, 1, 1, 1, 1, 2, 2, 4, 8, 8, 16, 32 },
                    { 0, 1 }, 4, 0, 0);

    // Buffers with sizes larger than the huge page size of the mmap allocator.
    ASSERT_EQ(524288u, HUGE_PAGE_CLUSTER_SIZE);
    assertGrowStats({ 262144, 262144, 262144, 524288, 524288, 524288 * 2, 524288 * 3, 524288 * 4, 524288 * 5, 524288 * 5 },
                    { 0, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144 },
                    4, 0, HUGE_PAGE_CLUSTER_SIZE / 2, HUGE_PAGE_CLUSTER_SIZE * 5);
}

using RefType15 = EntryRefT<15>; // offsetSize=32768

namespace {

template <typename DataType>
void assertGrowStats(GrowthStats expSizes, uint32_t clusterSize)
{
    uint32_t minClusters = 2048;
    uint32_t maxClusters = RefType15::offsetSize();
    uint32_t numClustersForNewBuffer = 2048;
    GrowStore<DataType, RefType15> store(clusterSize, minClusters, maxClusters, numClustersForNewBuffer);
    EXPECT_EQ(expSizes, store.getGrowthStats(expSizes.size()));
}

}

TEST(DataStoreTest, require_that_offset_in_EntryRefT_is_within_bounds_when_allocating_memory_buffers_where_wanted_number_of_bytes_is_not_a_power_of_2_and_less_than_huge_page_size)
{
    /*
     * When allocating new memory buffers for the data store the following happens (ref. calcAllocation() in bufferstate.cpp):
     *   1) Calculate how many clusters to alloc.
     *      In this case we alloc a minimum of 2048 and a maximum of 32768.
     *   2) Calculate how many bytes to alloc: clustersToAlloc * clusterSize * elementSize.
     *      In this case elementSize is (1 or 4) and clusterSize varies (3, 5, 7).
     *   3) Round up bytes to alloc to match the underlying allocator (power of 2 if less than huge page size):
     *      After this we might end up with more bytes than the offset in EntryRef can handle. In this case this is 32768.
     *   4) Cap bytes to alloc to the max offset EntryRef can handle.
     *      The max bytes to alloc is: maxClusters * clusterSize * elementSize.
     */
    assertGrowStats<uint8_t>({8192,8192,8192,16384,16384,32768,65536,65536,98304,98304,98304,98304}, 3);
    assertGrowStats<uint8_t>({16384,16384,16384,32768,32768,65536,131072,131072,163840,163840,163840,163840}, 5);
    assertGrowStats<uint8_t>({16384,16384,16384,32768,32768,65536,131072,131072,229376,229376,229376,229376}, 7);
    assertGrowStats<uint32_t>({8192,8192,8192,16384,16384,32768,65536,65536,98304,98304,98304,98304}, 3);
    assertGrowStats<uint32_t>({16384,16384,16384,32768,32768,65536,131072,131072,163840,163840,163840,163840}, 5);
    assertGrowStats<uint32_t>({16384,16384,16384,32768,32768,65536,131072,131072,229376,229376,229376,229376}, 7);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
