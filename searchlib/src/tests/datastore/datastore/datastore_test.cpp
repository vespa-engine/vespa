// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("datastore_test");
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchlib/datastore/datastore.h>
#include <vespa/searchlib/datastore/datastore.hpp>

namespace search {
namespace datastore {

class MyStore : public DataStore<int, EntryRefT<3, 2> > {
private:
    typedef DataStore<int, EntryRefT<3, 2> > ParentType;
    using ParentType::_buffers;
    using ParentType::_states;
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

    void trimElemHoldList(generation_t usedGen) {
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
    switchActiveBuffer(void)
    {
        ParentType::switchActiveBuffer(0, 0u);
    }
    std::vector<void *> & buffers() { return _buffers; }
    std::vector<BufferState> &statesVec() { return _states; }
    size_t activeBufferId() const { return _activeBufferIds[0]; }
};

typedef MyStore::RefType MyRef;

class Test : public vespalib::TestApp {
private:
    bool assertMemStats(const DataStoreBase::MemStats & exp,
                        const DataStoreBase::MemStats & act);
    void requireThatEntryRefIsWorking();
    void requireThatAlignedEntryRefIsWorking();
    void requireThatEntriesCanBeAddedAndRetrieved();
    void requireThatAddEntryTriggersChangeOfBuffer();
    void requireThatWeCanHoldAndTrimBuffers();
    void requireThatWeCanHoldAndTrimElements();
    void requireThatWeCanUseFreeLists();
    void requireThatMemoryStatsAreCalculated();
    void requireThatMemoryUsageIsCalculated();

    void
    requireThatWecanDisableElemHoldList(void);
public:
    int Main();
};

bool
Test::assertMemStats(const DataStoreBase::MemStats & exp,
                     const DataStoreBase::MemStats & act)
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

void
Test::requireThatEntryRefIsWorking()
{
    typedef EntryRefT<22> MyRefType;
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

void
Test::requireThatAlignedEntryRefIsWorking()
{
    typedef AlignedEntryRefT<22, 2> MyRefType; // 4 byte alignement
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

void
Test::requireThatEntriesCanBeAddedAndRetrieved()
{
    typedef DataStore<int> IntStore;
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

void
Test::requireThatAddEntryTriggersChangeOfBuffer()
{
    typedef DataStore<uint64_t, EntryRefT<10, 10> > Store;
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

void
Test::requireThatWeCanHoldAndTrimBuffers()
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

    EXPECT_TRUE(s.statesVec()[0].size() != 0);
    EXPECT_TRUE(s.statesVec()[1].size() != 0);
    EXPECT_TRUE(s.statesVec()[2].size() != 0);
    EXPECT_TRUE(s.statesVec()[3].size() != 0);
    s.trimHoldLists(11);
    EXPECT_TRUE(s.statesVec()[0].size() == 0);
    EXPECT_TRUE(s.statesVec()[1].size() != 0);
    EXPECT_TRUE(s.statesVec()[2].size() != 0);
    EXPECT_TRUE(s.statesVec()[3].size() != 0);

    s.switchActiveBuffer();
    EXPECT_EQUAL(0u, s.activeBufferId());
    EXPECT_EQUAL(0u, MyRef(s.addEntry(5)).bufferId());
    s.trimHoldLists(41);
    EXPECT_TRUE(s.statesVec()[0].size() != 0);
    EXPECT_TRUE(s.statesVec()[1].size() == 0);
    EXPECT_TRUE(s.statesVec()[2].size() == 0);
    EXPECT_TRUE(s.statesVec()[3].size() == 0);
}

void
Test::requireThatWeCanHoldAndTrimElements()
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

void
Test::requireThatWeCanUseFreeLists()
{
    MyStore s;
    s.enableFreeLists();
    MyRef r1 = s.addEntry2(1);
    s.holdElem(r1, 1);
    s.transferHoldLists(10);
    MyRef r2 = s.addEntry2(2);
    s.holdElem(r2, 1);
    s.transferHoldLists(20);
    s.trimElemHoldList(11);
    MyRef r3 = s.addEntry2(3); // reuse r1
    EXPECT_EQUAL(r1.offset(), r3.offset());
    EXPECT_EQUAL(r1.bufferId(), r3.bufferId());
    MyRef r4 = s.addEntry2(4);
    EXPECT_EQUAL(r2.offset() + 1, r4.offset());
    s.trimElemHoldList(21);
    MyRef r5 = s.addEntry2(5); // reuse r2
    EXPECT_EQUAL(r2.offset(), r5.offset());
    EXPECT_EQUAL(r2.bufferId(), r5.bufferId());
    MyRef r6 = s.addEntry2(6);
    EXPECT_EQUAL(r4.offset() + 1, r6.offset());
    EXPECT_EQUAL(3, s.getEntry(r1));
    EXPECT_EQUAL(5, s.getEntry(r2));
    EXPECT_EQUAL(3, s.getEntry(r3));
    EXPECT_EQUAL(4, s.getEntry(r4));
    EXPECT_EQUAL(5, s.getEntry(r5));
    EXPECT_EQUAL(6, s.getEntry(r6));
}

void
Test::requireThatMemoryStatsAreCalculated()
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

void
Test::requireThatMemoryUsageIsCalculated()
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


void
Test::requireThatWecanDisableElemHoldList(void)
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

int
Test::Main()
{
    TEST_INIT("datastore_test");

    requireThatEntryRefIsWorking();
    requireThatAlignedEntryRefIsWorking();
    requireThatEntriesCanBeAddedAndRetrieved();
    requireThatAddEntryTriggersChangeOfBuffer();
    requireThatWeCanHoldAndTrimBuffers();
    requireThatWeCanHoldAndTrimElements();
    requireThatWeCanUseFreeLists();
    requireThatMemoryStatsAreCalculated();
    requireThatMemoryUsageIsCalculated();
    requireThatWecanDisableElemHoldList();

    TEST_DONE();
}

}
}

TEST_APPHOOK(search::datastore::Test);

