// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/datastore/datastore.h>
#include <vespa/searchlib/datastore/datastore.hpp>
#include <vespa/vespalib/test/insertion_operators.h>

#include <vespa/log/log.h>
LOG_SETUP("datastore_test");

namespace search {
namespace datastore {

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

constexpr float ALLOC_GROW_FACTOR = 0.5;

class GrowStore
{
    using Store = DataStoreT<EntryRefT<22>>;
    using RefType = Store::RefType;
    Store _store;
    BufferType<int> _firstType;
    BufferType<int> _type;
    uint32_t _typeId;
public:
    GrowStore(size_t minSize, size_t minSwitch)
        : _store(),
          _firstType(1, 1, 64, 0, ALLOC_GROW_FACTOR),
          _type(1, minSize, 64, minSwitch, ALLOC_GROW_FACTOR),
          _typeId(0)
    {
        (void) _store.addType(&_firstType);
        _typeId = _store.addType(&_type);
        _store.initActiveBuffers();
    }
    ~GrowStore() { _store.dropBuffers(); }

    GrowthStats getGrowthStats(size_t bufs) {
        GrowthStats sizes;
        int i = 0;
        int previ = 0;
        int prevBufferId = -1;
        while (sizes.size() < bufs) {
            RefType iRef(_store.allocator<int>(_typeId).alloc().ref);
            int bufferId = iRef.bufferId();
            if (bufferId != prevBufferId) {
                if (prevBufferId >= 0) {
                    sizes.push_back(i - previ);
                    previ = i;
                }
                prevBufferId = bufferId;
            }
            ++i;
        }
        return sizes;
    }
    GrowthStats getFirstBufGrowStats() {
        GrowthStats sizes;
        int i = 0;
        int prevBuffer = -1;
        size_t prevAllocated = _store.getMemoryUsage().allocatedBytes();
        for (;;) {
            RefType iRef = _store.allocator<int>(_typeId).alloc().ref;
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

bool
assertMemStats(const DataStoreBase::MemStats &exp,
               const DataStoreBase::MemStats &act)
{
    if (!EXPECT_EQUAL(exp._allocElems, act._allocElems)) return false;
    if (!EXPECT_EQUAL(exp._usedElems, act._usedElems)) return false;
    if (!EXPECT_EQUAL(exp._deadElems, act._deadElems)) return false;
    if (!EXPECT_EQUAL(exp._holdElems, act._holdElems)) return false;
    if (!EXPECT_EQUAL(exp._freeBuffers, act._freeBuffers)) return false;
    if (!EXPECT_EQUAL(exp._activeBuffers, act._activeBuffers)) return false;
    if (!EXPECT_EQUAL(exp._holdBuffers, act._holdBuffers)) return false;
    return true;
}

TEST("require that entry ref is working")
{
    using MyRefType = EntryRefT<22>;
    EXPECT_EQUAL(4194304u, MyRefType::offsetSize());
    EXPECT_EQUAL(1024u, MyRefType::numBuffers());
    {
        MyRefType r(0, 0);
        EXPECT_EQUAL(0u, r.offset());
        EXPECT_EQUAL(0u, r.bufferId());
    }
    {
        MyRefType r(237, 13);
        EXPECT_EQUAL(237u, r.offset());
        EXPECT_EQUAL(13u, r.bufferId());
    }
    {
        MyRefType r(4194303, 1023);
        EXPECT_EQUAL(4194303u, r.offset());
        EXPECT_EQUAL(1023u, r.bufferId());
    }
    {
        MyRefType r1(6498, 76);
        MyRefType r2(r1);
        EXPECT_EQUAL(r1.offset(), r2.offset());
        EXPECT_EQUAL(r1.bufferId(), r2.bufferId());
    }
}

TEST("require that aligned entry ref is working")
{
    using MyRefType = AlignedEntryRefT<22, 2>; // 4 byte alignement
    EXPECT_EQUAL(4 * 4194304u, MyRefType::offsetSize());
    EXPECT_EQUAL(1024u, MyRefType::numBuffers());
    EXPECT_EQUAL(0u, MyRefType::align(0));
    EXPECT_EQUAL(4u, MyRefType::align(1));
    EXPECT_EQUAL(4u, MyRefType::align(2));
    EXPECT_EQUAL(4u, MyRefType::align(3));
    EXPECT_EQUAL(4u, MyRefType::align(4));
    EXPECT_EQUAL(8u, MyRefType::align(5));
    {
        MyRefType r(0, 0);
        EXPECT_EQUAL(0u, r.offset());
        EXPECT_EQUAL(0u, r.bufferId());
    }
    {
        MyRefType r(237, 13);
        EXPECT_EQUAL(MyRefType::align(237), r.offset());
        EXPECT_EQUAL(13u, r.bufferId());
    }
    {
        MyRefType r(MyRefType::offsetSize() - 4, 1023);
        EXPECT_EQUAL(MyRefType::align(MyRefType::offsetSize() - 4), r.offset());
        EXPECT_EQUAL(1023u, r.bufferId());
    }
}

TEST("require that entries can be added and retrieved")
{
    using IntStore = DataStore<int>;
    IntStore ds;
    EntryRef r1 = ds.addEntry(10);
    EntryRef r2 = ds.addEntry(20);
    EntryRef r3 = ds.addEntry(30);
    EXPECT_EQUAL(1u, IntStore::RefType(r1).offset());
    EXPECT_EQUAL(2u, IntStore::RefType(r2).offset());
    EXPECT_EQUAL(3u, IntStore::RefType(r3).offset());
    EXPECT_EQUAL(0u, IntStore::RefType(r1).bufferId());
    EXPECT_EQUAL(0u, IntStore::RefType(r2).bufferId());
    EXPECT_EQUAL(0u, IntStore::RefType(r3).bufferId());
    EXPECT_EQUAL(10, ds.getEntry(r1));
    EXPECT_EQUAL(20, ds.getEntry(r2));
    EXPECT_EQUAL(30, ds.getEntry(r3));
}

TEST("require that add entry triggers change of buffer")
{
    using Store = DataStore<uint64_t, EntryRefT<10, 10> >;
    Store s;
    uint64_t num = 0;
    uint32_t lastId = 0;
    uint64_t lastNum = 0;
    for (;;++num) {
        EntryRef r = s.addEntry(num);
        EXPECT_EQUAL(num, s.getEntry(r));
        uint32_t bufferId = Store::RefType(r).bufferId();
        if (bufferId > lastId) {
            LOG(info, "Changed to bufferId %u after %" PRIu64 " nums", bufferId, num);
            EXPECT_EQUAL(Store::RefType::offsetSize() - (lastId == 0),
                       num - lastNum);
            lastId = bufferId;
            lastNum = num;
        }
        if (bufferId == 2) {
            break;
        }
    }
    EXPECT_EQUAL(Store::RefType::offsetSize() * 2 - 1, num);
    LOG(info, "Added %" PRIu64 " nums in 2 buffers", num);
}

TEST("require that we can hold and trim buffers")
{
    MyStore s;
    EXPECT_EQUAL(0u, MyRef(s.addEntry(1)).bufferId());
    s.switchActiveBuffer();
    EXPECT_EQUAL(1u, s.activeBufferId());
    s.holdBuffer(0); // hold last buffer
    s.transferHoldLists(10);

    EXPECT_EQUAL(1u, MyRef(s.addEntry(2)).bufferId());
    s.switchActiveBuffer();
    EXPECT_EQUAL(2u, s.activeBufferId());
    s.holdBuffer(1); // hold last buffer
    s.transferHoldLists(20);

    EXPECT_EQUAL(2u, MyRef(s.addEntry(3)).bufferId());
    s.switchActiveBuffer();
    EXPECT_EQUAL(3u, s.activeBufferId());
    s.holdBuffer(2); // hold last buffer
    s.transferHoldLists(30);

    EXPECT_EQUAL(3u, MyRef(s.addEntry(4)).bufferId());
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
    EXPECT_EQUAL(0u, s.activeBufferId());
    EXPECT_EQUAL(0u, MyRef(s.addEntry(5)).bufferId());
    s.trimHoldLists(41);
    EXPECT_TRUE(s.getBufferState(0).size() != 0);
    EXPECT_TRUE(s.getBufferState(1).size() == 0);
    EXPECT_TRUE(s.getBufferState(2).size() == 0);
    EXPECT_TRUE(s.getBufferState(3).size() == 0);
}

TEST("require that we can hold and trim elements")
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
    EXPECT_EQUAL(1, s.getEntry(r1));
    EXPECT_EQUAL(2, s.getEntry(r2));
    EXPECT_EQUAL(3, s.getEntry(r3));
    s.trimElemHoldList(11);
    EXPECT_EQUAL(0, s.getEntry(r1));
    EXPECT_EQUAL(2, s.getEntry(r2));
    EXPECT_EQUAL(3, s.getEntry(r3));
    s.trimElemHoldList(31);
    EXPECT_EQUAL(0, s.getEntry(r1));
    EXPECT_EQUAL(0, s.getEntry(r2));
    EXPECT_EQUAL(0, s.getEntry(r3));
}

MyRef
toRef(Handle<int> handle)
{
    return MyRef(handle.ref);
}

TEST("require that we can use free lists")
{
    MyStore s;
    s.enableFreeLists();
    auto allocator = s.freeListAllocator<IntReclaimer>();
    auto h1 = allocator.alloc(1);
    s.holdElem(h1.ref, 1);
    s.transferHoldLists(10);
    auto h2 = allocator.alloc(2);
    s.holdElem(h2.ref, 1);
    s.transferHoldLists(20);
    s.trimElemHoldList(11);
    auto h3 = allocator.alloc(3); // reuse h1.ref
    EXPECT_EQUAL(toRef(h1).offset(), toRef(h3).offset());
    EXPECT_EQUAL(toRef(h1).bufferId(), toRef(h3).bufferId());
    auto h4 = allocator.alloc(4);
    EXPECT_EQUAL(toRef(h2).offset() + 1, toRef(h4).offset());
    s.trimElemHoldList(21);
    auto h5 = allocator.alloc(5); // reuse h2.ref
    EXPECT_EQUAL(toRef(h2).offset(), toRef(h5).offset());
    EXPECT_EQUAL(toRef(h2).bufferId(), toRef(h5).bufferId());
    auto h6 = allocator.alloc(6);
    EXPECT_EQUAL(toRef(h4).offset() + 1, toRef(h6).offset());
    EXPECT_EQUAL(3, s.getEntry(h1.ref));
    EXPECT_EQUAL(5, s.getEntry(h2.ref));
    EXPECT_EQUAL(3, s.getEntry(h3.ref));
    EXPECT_EQUAL(4, s.getEntry(h4.ref));
    EXPECT_EQUAL(5, s.getEntry(h5.ref));
    EXPECT_EQUAL(6, s.getEntry(h6.ref));
}

TEST("require that memory stats are calculated")
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
    EXPECT_TRUE(assertMemStats(m, s.getMemStats()));

    // add entry
    MyRef r = s.addEntry(10);
    m._usedElems++;
    EXPECT_TRUE(assertMemStats(m, s.getMemStats()));

    // inc dead
    s.incDead(r, 1);
    m._deadElems++;
    EXPECT_TRUE(assertMemStats(m, s.getMemStats()));

    // hold buffer
    s.addEntry(20);
    s.addEntry(30);
    s.holdBuffer(r.bufferId());
    s.transferHoldLists(100);
    m._usedElems += 2;
    m._holdElems += 2; // used - dead
    m._activeBuffers--;
    m._holdBuffers++;
    EXPECT_TRUE(assertMemStats(m, s.getMemStats()));

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
    EXPECT_TRUE(assertMemStats(m, s.getMemStats()));
}

TEST("require that memory usage is calculated")
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
    EXPECT_EQUAL(MyRef::offsetSize() * sizeof(int), m.allocatedBytes());
    EXPECT_EQUAL(5 * sizeof(int), m.usedBytes());
    EXPECT_EQUAL(2 * sizeof(int), m.deadBytes());
    EXPECT_EQUAL(3 * sizeof(int), m.allocatedBytesOnHold());
    s.trimHoldLists(101);
}

TEST("require that we can disable elemement hold list")
{
    MyStore s;
    MyRef r1 = s.addEntry(10);
    MyRef r2 = s.addEntry(20);
    MyRef r3 = s.addEntry(30);
    (void) r3;
    MemoryUsage m = s.getMemoryUsage();
    EXPECT_EQUAL(MyRef::offsetSize() * sizeof(int), m.allocatedBytes());
    EXPECT_EQUAL(4 * sizeof(int), m.usedBytes());
    EXPECT_EQUAL(1 * sizeof(int), m.deadBytes());
    EXPECT_EQUAL(0 * sizeof(int), m.allocatedBytesOnHold());
    s.holdElem(r1, 1);
    m = s.getMemoryUsage();
    EXPECT_EQUAL(MyRef::offsetSize() * sizeof(int), m.allocatedBytes());
    EXPECT_EQUAL(4 * sizeof(int), m.usedBytes());
    EXPECT_EQUAL(1 * sizeof(int), m.deadBytes());
    EXPECT_EQUAL(1 * sizeof(int), m.allocatedBytesOnHold());
    s.disableElemHoldList();
    s.holdElem(r2, 1);
    m = s.getMemoryUsage();
    EXPECT_EQUAL(MyRef::offsetSize() * sizeof(int), m.allocatedBytes());
    EXPECT_EQUAL(4 * sizeof(int), m.usedBytes());
    EXPECT_EQUAL(2 * sizeof(int), m.deadBytes());
    EXPECT_EQUAL(1 * sizeof(int), m.allocatedBytesOnHold());
    s.transferHoldLists(100);
    s.trimHoldLists(101);
}

namespace {

void assertGrowStats(GrowthStats expSizes,
                     GrowthStats expFirstBufSizes,
                     size_t expInitMemUsage,
                     size_t minSize, size_t minSwitch)
{
    EXPECT_EQUAL(expSizes, GrowStore(minSize, minSwitch).getGrowthStats(expSizes.size()));
    EXPECT_EQUAL(expFirstBufSizes, GrowStore(minSize, minSwitch).getFirstBufGrowStats());
    EXPECT_EQUAL(expInitMemUsage, GrowStore(minSize, minSwitch).getMemoryUsage().allocatedBytes());
}

}

TEST("require that buffer growth works")
{
    // Always switch to new buffer, min size 4
    TEST_DO(assertGrowStats({ 4, 4, 4, 6, 9, 13, 20, 30, 45, 64 },
                            { 4 }, 20, 4, 0));
    // Resize if buffer size is less than 4, min size 0
    TEST_DO(assertGrowStats({ 3, 3, 3, 4, 6, 9, 14, 21, 31, 47 },
                            { 0, 1, 2, 3 }, 4, 0, 4));
    // Always switch to new buffer, min size 16
    TEST_DO(assertGrowStats({ 16, 16, 16, 24, 36, 54, 64, 64, 64 },
                            { 16 }, 68, 16, 0));
    // Resize if buffer size is less than 16, min size 0
    TEST_DO(assertGrowStats({ 19, 19, 19, 28, 42, 63, 64, 64, 64 },
                            { 0, 1, 2, 3, 4, 6, 9, 13, 19 }, 4, 0, 16));
    // Resize if buffer size is less than 16, min size 4
    TEST_DO(assertGrowStats({ 19, 19, 19, 28, 42, 63, 64, 64, 64 },
                            { 4, 6, 9, 13, 19 }, 20, 4, 16));
    // Always switch to new buffer, min size 0
    TEST_DO(assertGrowStats({ 1, 1, 1, 1, 2, 3, 4, 6, 9 },
                            { 0, 1 }, 4, 0, 0));
}

}
}

TEST_MAIN() { TEST_RUN_ALL(); }

