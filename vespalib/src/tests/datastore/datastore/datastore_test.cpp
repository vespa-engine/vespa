// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/datastore/datastore.h>
#include <vespa/vespalib/datastore/datastore.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/test/memory_allocator_observer.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
LOG_SETUP("datastore_test");

namespace vespalib::datastore {

using vespalib::alloc::MemoryAllocator;

class MyStore : public DataStore<int, EntryRefT<3, 2> > {
private:
    using ParentType = DataStore<int, EntryRefT<3, 2> >;
public:
    MyStore() {}
    explicit MyStore(std::unique_ptr<BufferType<int>> type)
        : ParentType(std::move(type))
    {}
    void holdBuffer(uint32_t bufferId) {
        ParentType::holdBuffer(bufferId);
    }
    void holdElem(EntryRef ref, uint64_t len) {
        ParentType::holdElem(ref, len);
    }
    void assign_generation(generation_t current_gen) {
        ParentType::assign_generation(current_gen);
    }
    void reclaim_entry_refs(generation_t oldest_used_gen) override {
        ParentType::reclaim_entry_refs(oldest_used_gen);
    }
    void ensureBufferCapacity(size_t sizeNeeded) {
        ParentType::ensureBufferCapacity(0, sizeNeeded);
    }
    void enableFreeLists() {
        ParentType::enableFreeLists();
    }
    void switch_primary_buffer() {
        ParentType::switch_primary_buffer(0, 0u);
    }
    size_t primary_buffer_id() const { return get_primary_buffer_id(0); }
    BufferState& get_active_buffer_state() {
        return ParentType::getBufferState(primary_buffer_id());
    }
};


using GrowthStats = std::vector<int>;
using BufferIds = std::vector<int>;

constexpr float ALLOC_GROW_FACTOR = 0.4;
constexpr size_t HUGE_PAGE_ARRAY_SIZE = (MemoryAllocator::HUGEPAGE_SIZE / sizeof(int));

template <typename DataType, typename RefType>
class GrowStore
{
    using Store = DataStoreT<RefType>;
    Store _store;
    BufferType<DataType> _firstType;
    BufferType<DataType> _type;
    uint32_t _typeId;
public:
    GrowStore(size_t arraySize, size_t minArrays, size_t maxArrays, size_t numArraysForNewBuffer)
        : _store(),
          _firstType(1, 1, maxArrays, 0, ALLOC_GROW_FACTOR),
          _type(arraySize, minArrays, maxArrays, numArraysForNewBuffer, ALLOC_GROW_FACTOR),
          _typeId(0)
    {
        (void) _store.addType(&_firstType);
        _typeId = _store.addType(&_type);
        _store.init_primary_buffers();
    }
    ~GrowStore() { _store.dropBuffers(); }

    Store &store() { return _store; }
    uint32_t typeId() const { return _typeId; }

    GrowthStats getGrowthStats(size_t bufs) {
        GrowthStats sizes;
        int prevBufferId = -1;
        while (sizes.size() < bufs) {
            RefType iRef = (_type.getArraySize() == 1) ?
                           (_store.template allocator<DataType>(_typeId).alloc().ref) :
                           (_store.template allocator<DataType>(_typeId).allocArray(_type.getArraySize()).ref);
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
    BufferIds getBuffers(size_t bufs) {
        BufferIds buffers;
        while (buffers.size() < bufs) {
            RefType iRef = (_type.getArraySize() == 1) ?
                           (_store.template allocator<DataType>(_typeId).alloc().ref) :
                           (_store.template allocator<DataType>(_typeId).allocArray(_type.getArraySize()).ref);
            int buffer_id = iRef.bufferId();
            if (buffers.empty() || buffers.back() != buffer_id) {
                buffers.push_back(buffer_id);
            }
        }
        return buffers;
    }
    vespalib::MemoryUsage getMemoryUsage() const { return _store.getMemoryUsage(); }
};

using MyRef = MyStore::RefType;

void
assertMemStats(const MemoryStats &exp,
               const MemoryStats &act)
{
    EXPECT_EQ(exp._allocElems, act._allocElems);
    EXPECT_EQ(exp._usedElems, act._usedElems);
    EXPECT_EQ(exp._deadElems, act._deadElems);
    EXPECT_EQ(exp._holdElems, act._holdElems);
    EXPECT_EQ(exp._freeBuffers, act._freeBuffers);
    EXPECT_EQ(exp._activeBuffers, act._activeBuffers);
    EXPECT_EQ(exp._holdBuffers, act._holdBuffers);
}

TEST(DataStoreTest, require_that_invalid_entry_ref_can_be_ordered) {
    EntryRef inValid;
    EntryRef a(1);
    EXPECT_EQ(inValid, inValid);
    EXPECT_EQ(a, a);
    EXPECT_NE(inValid, a);
    EXPECT_NE(a, inValid);
    EXPECT_LT(inValid, a);
    EXPECT_LE(inValid, a);
}

TEST(DataStoreTest, require_that_entry_ref_can_be_ordered) {
    EntryRef a(1);
    EntryRef b(2);
    EntryRef c(3);
    EXPECT_EQ(a, a);
    EXPECT_EQ(b, b);
    EXPECT_EQ(c, c);
    EXPECT_NE(a, b);
    EXPECT_NE(a, c);
    EXPECT_NE(b, c);
    EXPECT_LT(a, b);
    EXPECT_LT(b, c);
    EXPECT_LT(a, c);
    EXPECT_LE(a, a);
    EXPECT_LE(b, b);
    EXPECT_LE(c, c);
    EXPECT_LE(a, b);
    EXPECT_LE(b, c);
    EXPECT_LE(a, c);
}

TEST(DataStoreTest, require_that_entry_ref_is_working)
{
    using MyRefType = EntryRefT<22>;
    EXPECT_EQ(4_Mi, MyRefType::offsetSize());
    EXPECT_EQ(1_Ki, MyRefType::numBuffers());
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
    s.switch_primary_buffer();
    EXPECT_EQ(1u, s.primary_buffer_id());
    s.holdBuffer(0); // hold last buffer
    s.assign_generation(10);

    EXPECT_EQ(1u, MyRef(s.addEntry(2)).bufferId());
    s.switch_primary_buffer();
    EXPECT_EQ(2u, s.primary_buffer_id());
    s.holdBuffer(1); // hold last buffer
    s.assign_generation(20);

    EXPECT_EQ(2u, MyRef(s.addEntry(3)).bufferId());
    s.switch_primary_buffer();
    EXPECT_EQ(3u, s.primary_buffer_id());
    s.holdBuffer(2); // hold last buffer
    s.assign_generation(30);

    EXPECT_EQ(3u, MyRef(s.addEntry(4)).bufferId());
    s.holdBuffer(3); // hold current buffer
    s.assign_generation(40);

    EXPECT_TRUE(s.getBufferState(0).size() != 0);
    EXPECT_TRUE(s.getBufferState(1).size() != 0);
    EXPECT_TRUE(s.getBufferState(2).size() != 0);
    EXPECT_TRUE(s.getBufferState(3).size() != 0);
    s.reclaim_memory(11);
    EXPECT_TRUE(s.getBufferState(0).size() == 0);
    EXPECT_TRUE(s.getBufferState(1).size() != 0);
    EXPECT_TRUE(s.getBufferState(2).size() != 0);
    EXPECT_TRUE(s.getBufferState(3).size() != 0);

    s.switch_primary_buffer();
    EXPECT_EQ(0u, s.primary_buffer_id());
    EXPECT_EQ(0u, MyRef(s.addEntry(5)).bufferId());
    s.reclaim_memory(41);
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
    s.assign_generation(10);
    MyRef r2 = s.addEntry(2);
    s.holdElem(r2, 1);
    s.assign_generation(20);
    MyRef r3 = s.addEntry(3);
    s.holdElem(r3, 1);
    s.assign_generation(30);
    EXPECT_EQ(1, s.getEntry(r1));
    EXPECT_EQ(2, s.getEntry(r2));
    EXPECT_EQ(3, s.getEntry(r3));
    s.reclaim_entry_refs(11);
    EXPECT_EQ(0, s.getEntry(r1));
    EXPECT_EQ(2, s.getEntry(r2));
    EXPECT_EQ(3, s.getEntry(r3));
    s.reclaim_entry_refs(31);
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
expect_successive_refs(EntryRef first, EntryRef second)
{
    EXPECT_EQ(MyRef(first).offset() + 1, MyRef(second).offset());
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
    auto r1 = s.addEntry(1);
    s.holdElem(r1, 1);
    s.assign_generation(10);
    auto r2 = s.addEntry(2);
    expect_successive_refs(r1, r2);
    s.holdElem(r2, 1);
    s.assign_generation(20);
    s.reclaim_entry_refs(11);
    auto r3 = s.addEntry(3); // reuse r1
    EXPECT_EQ(r1, r3);
    auto r4 = s.addEntry(4);
    expect_successive_refs(r2, r4);
    s.reclaim_entry_refs(21);
    auto r5 = s.addEntry(5); // reuse r2
    EXPECT_EQ(r2, r5);
    auto r6 = s.addEntry(6);
    expect_successive_refs(r4, r6);
    EXPECT_EQ(3, s.getEntry(r1));
    EXPECT_EQ(5, s.getEntry(r2));
    EXPECT_EQ(3, s.getEntry(r3));
    EXPECT_EQ(4, s.getEntry(r4));
    EXPECT_EQ(5, s.getEntry(r5));
    EXPECT_EQ(6, s.getEntry(r6));
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
    s.assign_generation(10);
    s.reclaim_entry_refs(11);

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
    MemoryStats m;
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

    // hold buffer
    s.addEntry(20);
    s.addEntry(30);
    s.holdBuffer(r.bufferId());
    s.assign_generation(100);
    m._usedElems += 2;
    m._holdElems = m._usedElems;
    m._deadElems = 0;
    m._activeBuffers--;
    m._holdBuffers++;
    assertMemStats(m, s.getMemStats());

    // new active buffer
    s.switch_primary_buffer();
    s.addEntry(40);
    m._allocElems += MyRef::offsetSize();
    m._usedElems++;
    m._activeBuffers++;
    m._freeBuffers--;

    // trim hold buffer
    s.reclaim_memory(101);
    m._allocElems -= MyRef::offsetSize();
    m._usedElems = 1;
    m._deadElems = 0;
    m._holdElems = 0;
    m._freeBuffers = MyRef::numBuffers() - 1;
    m._holdBuffers = 0;
    assertMemStats(m, s.getMemStats());

    { // increase extra used bytes
        auto prev_stats = s.getMemStats();
        s.get_active_buffer_state().stats().inc_extra_used_bytes(50);
        auto curr_stats = s.getMemStats();
        EXPECT_EQ(prev_stats._allocBytes + 50, curr_stats._allocBytes);
        EXPECT_EQ(prev_stats._usedBytes + 50, curr_stats._usedBytes);
    }

    { // increase extra hold bytes
        auto prev_stats = s.getMemStats();
        s.get_active_buffer_state().hold_elems(0, 30);
        auto curr_stats = s.getMemStats();
        EXPECT_EQ(prev_stats._holdBytes + 30, curr_stats._holdBytes);
    }
}

TEST(DataStoreTest, require_that_memory_usage_is_calculated)
{
    MyStore s;
    MyRef r = s.addEntry(10);
    s.addEntry(20);
    s.addEntry(30);
    s.addEntry(40);
    s.holdBuffer(r.bufferId());
    s.assign_generation(100);
    vespalib::MemoryUsage m = s.getMemoryUsage();
    EXPECT_EQ(MyRef::offsetSize() * sizeof(int), m.allocatedBytes());
    EXPECT_EQ(5 * sizeof(int), m.usedBytes());
    EXPECT_EQ(0 * sizeof(int), m.deadBytes());
    EXPECT_EQ(5 * sizeof(int), m.allocatedBytesOnHold());
    s.reclaim_memory(101);
}

TEST(DataStoreTest, require_that_we_can_disable_elemement_hold_list)
{
    MyStore s;
    MyRef r1 = s.addEntry(10);
    MyRef r2 = s.addEntry(20);
    MyRef r3 = s.addEntry(30);
    (void) r3;
    vespalib::MemoryUsage m = s.getMemoryUsage();
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
    s.assign_generation(100);
    s.reclaim_memory(101);
}

using IntGrowStore = GrowStore<int, EntryRefT<24>>;

namespace {

void assertGrowStats(GrowthStats expSizes,
                     GrowthStats expFirstBufSizes,
                     size_t expInitMemUsage,
                     size_t minArrays, size_t numArraysForNewBuffer, size_t maxArrays = 128)
{
    EXPECT_EQ(expSizes, IntGrowStore(1, minArrays, maxArrays, numArraysForNewBuffer).getGrowthStats(expSizes.size()));
    EXPECT_EQ(expFirstBufSizes, IntGrowStore(1, minArrays, maxArrays, numArraysForNewBuffer).getFirstBufGrowStats());
    EXPECT_EQ(expInitMemUsage, IntGrowStore(1, minArrays, maxArrays, numArraysForNewBuffer).getMemoryUsage().allocatedBytes());
}

}

TEST(DataStoreTest, require_that_buffer_growth_works)
{
    // Always switch to new buffer, min size 4
    assertGrowStats({ 4, 4, 4, 4, 8, 16, 16, 32, 64, 64 },
                    { 4 }, 20, 4, 0);
    // Resize if buffer size is less than 4, min size 0
    assertGrowStats({ 4, 4, 8, 32, 32, 64, 64, 128, 128, 128 },
                    { 0, 1, 2, 4 }, 4, 0, 4);
    // Always switch to new buffer, min size 16
    assertGrowStats({ 16, 16, 16, 32, 32, 64, 128, 128, 128 },
                    { 16 }, 68, 16, 0);
    // Resize if buffer size is less than 16, min size 0
    assertGrowStats({ 16, 32, 32, 128, 128, 128, 128, 128, 128 },
                    { 0, 1, 2, 4, 8, 16 }, 4, 0, 16);
    // Resize if buffer size is less than 16, min size 4
    assertGrowStats({ 16, 32, 32, 128, 128, 128, 128, 128, 128 },
                    { 4, 8, 16 }, 20, 4, 16);
    // Always switch to new buffer, min size 0
    assertGrowStats({ 1, 1, 1, 1, 1, 2, 2, 4, 8, 8, 16, 32 },
                    { 0, 1 }, 4, 0, 0);

    // Buffers with sizes larger than the huge page size of the mmap allocator.
    ASSERT_EQ(524288u, HUGE_PAGE_ARRAY_SIZE);
    assertGrowStats({ 262144, 524288, 524288, 524288 * 3, 524288 * 3, 524288 * 5, 524288 * 5, 524288 * 5, 524288 * 5, 524288 * 5 },
                    { 0, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144 },
                    4, 0, HUGE_PAGE_ARRAY_SIZE / 2, HUGE_PAGE_ARRAY_SIZE * 5);
}

using RefType15 = EntryRefT<15>; // offsetSize=32768

namespace {

template <typename DataType>
void assertGrowStats(GrowthStats expSizes, uint32_t arraySize)
{
    uint32_t minArrays = 2048;
    uint32_t maxArrays = RefType15::offsetSize();
    uint32_t numArraysForNewBuffer = 2048;
    GrowStore<DataType, RefType15> store(arraySize, minArrays, maxArrays, numArraysForNewBuffer);
    EXPECT_EQ(expSizes, store.getGrowthStats(expSizes.size()));
}

}

TEST(DataStoreTest, require_that_offset_in_EntryRefT_is_within_bounds_when_allocating_memory_buffers_where_wanted_number_of_bytes_is_not_a_power_of_2_and_less_than_huge_page_size)
{
    /*
     * When allocating new memory buffers for the data store the following happens (ref. calcAllocation() in bufferstate.cpp):
     *   1) Calculate how many arrays to alloc.
     *      In this case we alloc a minimum of 2048 and a maximum of 32768.
     *   2) Calculate how many bytes to alloc: arraysToAlloc * arraySize * elementSize.
     *      In this case elementSize is (1 or 4) and arraySize varies (3, 5, 7).
     *   3) Round up bytes to alloc to match the underlying allocator (power of 2 if less than huge page size):
     *      After this we might end up with more bytes than the offset in EntryRef can handle. In this case this is 32768.
     *   4) Cap bytes to alloc to the max offset EntryRef can handle.
     *      The max bytes to alloc is: maxArrays * arraySize * elementSize.
     */
    assertGrowStats<uint8_t>({8192,16384,16384,65536,65536,98304,98304,98304,98304,98304,98304,98304}, 3);
    assertGrowStats<uint8_t>({16384,16384,65536,65536,131072,131072,163840,163840,163840,163840,163840,163840}, 5);
    assertGrowStats<uint8_t>({16384,32768,32768,131072,131072,229376,229376,229376,229376,229376,229376,229376}, 7);
    assertGrowStats<uint32_t>({8192,16384,16384,65536,65536,98304,98304,98304,98304,98304,98304,98304}, 3);
    assertGrowStats<uint32_t>({16384,16384,65536,65536,131072,131072,163840,163840,163840,163840,163840,163840}, 5);
    assertGrowStats<uint32_t>({16384,32768,32768,131072,131072,229376,229376,229376,229376,229376,229376,229376}, 7);
}

namespace {

using MyMemoryAllocator = vespalib::alloc::test::MemoryAllocatorObserver;
using AllocStats = MyMemoryAllocator::Stats;

class MyBufferType : public BufferType<int>
{
    std::unique_ptr<alloc::MemoryAllocator> _allocator;
public:
    MyBufferType(std::unique_ptr<alloc::MemoryAllocator> allocator, uint32_t max_arrays)
        : BufferType<int>(1, 2, max_arrays, max_arrays, 0.2),
          _allocator(std::move(allocator))
    {
    }
    const alloc::MemoryAllocator* get_memory_allocator() const override {
        return _allocator.get();
    }
};

}

TEST(DataStoreTest, can_set_memory_allocator)
{
    AllocStats stats;
    {
        MyStore s(std::make_unique<MyBufferType>(std::make_unique<MyMemoryAllocator>(stats), MyStore::RefType::offsetSize()));
        EXPECT_EQ(AllocStats(1, 0), stats);
        auto ref = s.addEntry(42);
        EXPECT_EQ(0u, MyRef(ref).bufferId());
        EXPECT_EQ(AllocStats(1, 0), stats);
        auto ref2 = s.addEntry(43);
        EXPECT_EQ(0u, MyRef(ref2).bufferId());
        EXPECT_EQ(AllocStats(2, 0), stats);
        s.switch_primary_buffer();
        EXPECT_EQ(AllocStats(3, 0), stats);
        s.holdBuffer(0);
        s.assign_generation(10);
        EXPECT_EQ(AllocStats(3, 0), stats);
        s.reclaim_memory(11);
        EXPECT_EQ(AllocStats(3, 2), stats);
    }
    EXPECT_EQ(AllocStats(3, 3), stats);
}

namespace {

void
assertBuffers(BufferIds exp_buffers, size_t num_arrays_for_new_buffer)
{
    EXPECT_EQ(exp_buffers, IntGrowStore(1, 1, 1024, num_arrays_for_new_buffer).getBuffers(exp_buffers.size()));
}

}

TEST(DataStoreTest, can_reuse_active_buffer_as_primary_buffer)
{
    assertBuffers({ 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, 0);
    assertBuffers({ 1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3}, 16);
}

TEST(DataStoreTest, control_static_sizes) {
    EXPECT_EQ(96, sizeof(BufferTypeBase));
    EXPECT_EQ(24, sizeof(FreeList));
    EXPECT_EQ(56, sizeof(BufferFreeList));
    EXPECT_EQ(1, sizeof(BufferState::State));
    EXPECT_EQ(144, sizeof(BufferState));
    BufferState bs;
    EXPECT_EQ(0, bs.size());
}

namespace {

void test_free_element_to_held_buffer(bool before_hold_buffer)
{
    MyStore s;
    auto ref = s.addEntry(1);
    EXPECT_EQ(0u, MyRef(ref).bufferId());
    s.switch_primary_buffer();
    EXPECT_EQ(1u, s.primary_buffer_id());
    
    if (before_hold_buffer) {
        s.holdElem(ref, 1);
    }
    s.holdBuffer(0); // hold last buffer
    if (!before_hold_buffer) {
        ASSERT_DEATH({ s.holdElem(ref, 1); }, "isActive\\(\\)");
    }
    s.assign_generation(100);
    s.reclaim_memory(101);
}

}

TEST(DataStoreTest, hold_to_active_then_held_buffer_is_ok)
{
    test_free_element_to_held_buffer(true);
}

#ifndef NDEBUG
TEST(DataStoreDeathTest, hold_to_held_buffer_is_not_ok)
{
    test_free_element_to_held_buffer(false);
}
#endif

}

GTEST_MAIN_RUN_ALL_TESTS()
