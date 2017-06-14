// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/storageframework/defaultimplementation/memory/memorystate.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/util/exceptions.h>

namespace storage {
namespace framework {
namespace defaultimplementation {

struct MemoryStateTest : public CppUnit::TestFixture
{

    void testBasics();

    CPPUNIT_TEST_SUITE(MemoryStateTest);
    CPPUNIT_TEST(testBasics); // Fails sometimes, test needs rewrite.
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MemoryStateTest);

class SimpleMemoryManager : public framework::MemoryManagerInterface
{
private:
    std::map<std::string, framework::MemoryAllocationType> _types;

public:
    void setMaximumMemoryUsage(uint64_t max) override { (void) max; }

    const framework::MemoryAllocationType&
    registerAllocationType(const framework::MemoryAllocationType& type) override {
        _types[type.getName()] = type;
        return _types[type.getName()];
    }

    const framework::MemoryAllocationType&
    getAllocationType(const std::string& name) const override {
        std::map<std::string, framework::MemoryAllocationType>::const_iterator iter =
            _types.find(name);

        if (iter == _types.end()) {
            throw vespalib::IllegalArgumentException("Allocation type not found: " + name);
        }

        return iter->second;
    }

    std::vector<const MemoryAllocationType*> getAllocationTypes() const override {
        std::vector<const MemoryAllocationType*> types;
        for(std::map<std::string, framework::MemoryAllocationType>
                ::const_iterator it = _types.begin(); it != _types.end(); ++it)
        {
            types.push_back(&it->second);
        }
        return types;
    }

    framework::MemoryToken::UP allocate(const framework::MemoryAllocationType&,
                                        uint64_t,
                                        uint64_t,
                                        uint8_t,
                                        framework::ReduceMemoryUsageInterface*) override
    {
        return framework::MemoryToken::UP();
    }

    uint64_t getMemorySizeFreeForPriority(uint8_t priority) const override {
        (void) priority;
        return 0;
    }
};

void
MemoryStateTest::testBasics()
{
    SimpleMemoryManager manager;

    const MemoryAllocationType& putAlloc(manager.registerAllocationType(
            MemoryAllocationType("MESSAGE_PUT", MemoryAllocationType::EXTERNAL_LOAD)));
    const MemoryAllocationType& getAlloc(manager.registerAllocationType(
            MemoryAllocationType("MESSAGE_GET", MemoryAllocationType::EXTERNAL_LOAD)));
    const MemoryAllocationType& blockAlloc(manager.registerAllocationType(
            MemoryAllocationType("MESSAGE_DOCBLOCK")));
    const MemoryAllocationType& databaseAlloc(manager.registerAllocationType(
            MemoryAllocationType("DATABASE")));
    const MemoryAllocationType& cacheAlloc(manager.registerAllocationType(
            MemoryAllocationType("SLOTFILE_CACHE", MemoryAllocationType::CACHE)));

    uint32_t maxMemory = 1024;

    RealClock clock;
    MemoryState state1(clock, maxMemory);
    MemoryState state2(clock, maxMemory);

    state1.setMinJumpToUpdateMax(50);

    state1.addToEntry(putAlloc, 100, 10,
                      MemoryState::GOT_MAX, false);
    state1.addToEntry(putAlloc, 100, 60,
                      MemoryState::GOT_MAX, false);
    state1.addToEntry(blockAlloc,
                      200, 20,
                      MemoryState::GOT_MIN, false);
    state1.addToEntry(getAlloc, 0, 15,
                      MemoryState::DENIED, false, 0);
    state1.addToEntry(databaseAlloc, 150, 0,
                      MemoryState::DENIED, true, 1);
    state1.addToEntry(cacheAlloc, 45, 0,
                      MemoryState::GOT_MAX, true, 1);

    state2.addToEntry(putAlloc, 50, 10,
                      MemoryState::GOT_MIN, false);
    state2.addToEntry(putAlloc, 20, 40,
                      MemoryState::GOT_MIN, false);

    state1.removeFromEntry(databaseAlloc, 25, 0, 0);
    state1.removeFromEntry(putAlloc, 100, 60);

    MemoryState::SnapShot state3;
    state3 = state1.getMaxSnapshot();
    state3 += state2.getMaxSnapshot();

    std::string expected;
    expected =
        "\n"
        "MemoryState(Max memory: 1024) {\n"
        "  Current: SnapShot(Used 470, w/o cache 425) {\n"
        "    Type(Pri): Used(Size/Allocs) Stats(Allocs, Wanted, Min, Denied, Forced)\n"
        "    DATABASE(0):             Used(125 B / 1)     Stats(1, 0, 0, 1, 1)\n"
        "    MESSAGE_DOCBLOCK(20):    Used(200 B / 1)     Stats(1, 0, 1, 0, 0)\n"
        "    MESSAGE_GET(15):         Used(0 B / 0)       Stats(1, 0, 0, 1, 0)\n"
        "    MESSAGE_PUT(10):         Used(100 B / 1)     Stats(1, 1, 0, 0, 0)\n"
        "    MESSAGE_PUT(60):         Used(0 B / 0)       Stats(1, 1, 0, 0, 0)\n"
        "    SLOTFILE_CACHE(0):       Used(45 B / 1)      Stats(1, 1, 0, 0, 1)\n"
        "  }\n"
        "  Max: SnapShot(Used 550, w/o cache 550) {\n"
        "    Type(Pri): Used(Size/Allocs) Stats(Allocs, Wanted, Min, Denied, Forced)\n"
        "    DATABASE(0):             Used(150 B / 1)     Stats(1, 0, 0, 1, 1)\n"
        "    MESSAGE_DOCBLOCK(20):    Used(200 B / 1)     Stats(1, 0, 1, 0, 0)\n"
        "    MESSAGE_GET(15):         Used(0 B / 0)       Stats(1, 0, 0, 1, 0)\n"
        "    MESSAGE_PUT(10):         Used(100 B / 1)     Stats(1, 1, 0, 0, 0)\n"
        "    MESSAGE_PUT(60):         Used(100 B / 1)     Stats(1, 1, 0, 0, 0)\n"
        "  }\n"
        "}";

    CPPUNIT_ASSERT_EQUAL(expected, "\n" + state1.toString(true));
    expected = "\n"
"MemoryState(Max memory: 1024) {\n"
"  Current: SnapShot(Used 70, w/o cache 70) {\n"
"    Type(Pri): Used(Size/Allocs) Stats(Allocs, Wanted, Min, Denied, Forced)\n"
"    MESSAGE_PUT(10):         Used(50 B / 1)      Stats(1, 0, 1, 0, 0)\n"
"    MESSAGE_PUT(40):         Used(20 B / 1)      Stats(1, 0, 1, 0, 0)\n"
"  }\n"
"}";
    CPPUNIT_ASSERT_EQUAL(expected, "\n" + state2.toString(true));
    expected = "\n"
"SnapShot(Used 550, w/o cache 550) {\n"
"  Type(Pri): Used(Size/Allocs) Stats(Allocs, Wanted, Min, Denied, Forced)\n"
"  DATABASE(0):             Used(150 B / 1)     Stats(1, 0, 0, 1, 1)\n"
"  MESSAGE_DOCBLOCK(20):    Used(200 B / 1)     Stats(1, 0, 1, 0, 0)\n"
"  MESSAGE_GET(15):         Used(0 B / 0)       Stats(1, 0, 0, 1, 0)\n"
"  MESSAGE_PUT(10):         Used(100 B / 1)     Stats(1, 1, 0, 0, 0)\n"
"  MESSAGE_PUT(60):         Used(100 B / 1)     Stats(1, 1, 0, 0, 0)\n"
"}";
    CPPUNIT_ASSERT_EQUAL(expected, "\n" + state3.toString(true));
}

} // defaultimplementation
} // framework
} // storage
