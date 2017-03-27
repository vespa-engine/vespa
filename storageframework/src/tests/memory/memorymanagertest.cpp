// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/storageframework/defaultimplementation/memory/memorymanager.h>
#include <vespa/storageframework/defaultimplementation/memory/simplememorylogic.h>
#include <vespa/storageframework/defaultimplementation/memory/prioritymemorylogic.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/vespalib/util/random.h>

namespace storage {
namespace framework {
namespace defaultimplementation {

struct MemoryManagerTest : public CppUnit::TestFixture
{
    void testBasics();
    void testCacheAllocation();
    void testStress();

    CPPUNIT_TEST_SUITE(MemoryManagerTest);
    CPPUNIT_TEST(testBasics);
    CPPUNIT_TEST(testCacheAllocation);
    CPPUNIT_TEST(testStress);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MemoryManagerTest);

void
MemoryManagerTest::testBasics()
{
    uint64_t maxMemory = 1000;
    RealClock clock;
    SimpleMemoryLogic* logic = new SimpleMemoryLogic(clock, maxMemory);
    AllocationLogic::UP allLogic(std::move(logic));
    MemoryManager manager(std::move(allLogic));

    const MemoryAllocationType& putAlloc(manager.registerAllocationType(
            MemoryAllocationType("put", MemoryAllocationType::EXTERNAL_LOAD)));
    const MemoryAllocationType& getAlloc(manager.registerAllocationType(
            MemoryAllocationType("get", MemoryAllocationType::EXTERNAL_LOAD)));
    const MemoryAllocationType& bufAlloc(manager.registerAllocationType(
            MemoryAllocationType("buffer")));
    const MemoryAllocationType& cacheAlloc(manager.registerAllocationType(
            MemoryAllocationType("cache", MemoryAllocationType::CACHE)));
    const MemoryState& state(logic->getState());
    const MemoryState::SnapShot& current(state.getCurrentSnapshot());
        // Basics
    {
        //   * Getting a token, and release it back with correct behavior
        framework::MemoryToken::UP put = manager.allocate(putAlloc,
                                               0, 100, 80);
        CPPUNIT_ASSERT(put.get() != 0);
        CPPUNIT_ASSERT_EQUAL(uint64_t(100), put->getSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(100), current.getUsedSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(900), state.getFreeSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(1000), state.getTotalSize());

        //   * Do the same while not being empty. Different type.
        framework::MemoryToken::UP get = manager.allocate(getAlloc,
                                               30, 200, 50);
        CPPUNIT_ASSERT(get.get() != 0);
        CPPUNIT_ASSERT_EQUAL(uint64_t(200), get->getSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(300), current.getUsedSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(700), state.getFreeSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(1000), state.getTotalSize());

        //   * Do the same while not being empty. Same type.
        framework::MemoryToken::UP get2 = manager.allocate(
                getAlloc,
                70,
                150,
                60);

        CPPUNIT_ASSERT(get2.get() != 0);
        CPPUNIT_ASSERT_EQUAL(uint64_t(150), get2->getSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(450), current.getUsedSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(550), state.getFreeSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(1000), state.getTotalSize());
    }
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), current.getUsedSize());
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), current.getUserCount());

        // Non-external load
        //   * Getting minimum when going beyond 80% full
    {
        framework::MemoryToken::UP filler = manager.allocate(putAlloc,
                                                  795, 795, 90);
        framework::MemoryToken::UP resize = manager.allocate(
                bufAlloc, 10, 90, 80);
        CPPUNIT_ASSERT(resize.get() != 0);
        CPPUNIT_ASSERT_EQUAL(uint64_t(10), resize->getSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(805), current.getUsedSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(195), state.getFreeSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(1000), state.getTotalSize());
    }
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), current.getUsedSize());
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), current.getUserCount());

        // Non-external load
        //   * Getting up to threshold if hitting it
    {
        framework::MemoryToken::UP filler = manager.allocate(putAlloc,
                                                  750, 750, 90);
        framework::MemoryToken::UP resize = manager.allocate(
                bufAlloc, 10, 90, 80);
        CPPUNIT_ASSERT(resize.get() != 0);
        CPPUNIT_ASSERT_EQUAL(uint64_t(50), resize->getSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(800), current.getUsedSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(200), state.getFreeSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(1000), state.getTotalSize());
    }
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), current.getUsedSize());
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), current.getUserCount());

        // External load
    {
        //   * Stopped when going beyond 80% full
        framework::MemoryToken::UP filler = manager.allocate(putAlloc,
                                                  795, 795, 90);
        framework::MemoryToken::UP put = manager.allocate(putAlloc,
                                               10, 100, 80);
        CPPUNIT_ASSERT(put.get() == 0);
        CPPUNIT_ASSERT_EQUAL(uint64_t(795), current.getUsedSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(205), state.getFreeSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(1000), state.getTotalSize());
    }
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), current.getUsedSize());
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), current.getUserCount());

        // External load
    {
        //   * Getting up to threshold if hitting it
        framework::MemoryToken::UP filler = manager.allocate(putAlloc,
                                                  750, 750, 90);
        framework::MemoryToken::UP put = manager.allocate(putAlloc,
                                               10, 100, 80);
        CPPUNIT_ASSERT(put.get() != 0);
        CPPUNIT_ASSERT_EQUAL(uint64_t(50), put->getSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(800), current.getUsedSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(200), state.getFreeSize());
        CPPUNIT_ASSERT_EQUAL(uint64_t(1000), state.getTotalSize());
    }
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), current.getUsedSize());
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), current.getUserCount());

        // Test caching..
    {
        // Cache paradigm:
        // Allocate a token taking up no space at all.
        // Give it to your ReduceMemoryUsageInterface implementation.
        // Run resize on your token in that implementation to get memory and
        // return memory. That way locking should be easy when needed.
        struct ReduceI : public framework::ReduceMemoryUsageInterface {
            framework::MemoryToken::UP _token;

            virtual uint64_t reduceMemoryConsumption(const MemoryToken& token,
                                                     uint64_t reduceBy)
            {
                assert(&token == _token.get());
                (void) &token;
                assert(_token->getSize() >= reduceBy);
                return reduceBy;
            }
        };
        ReduceI reducer;
        framework::MemoryToken::UP cache = manager.allocate(cacheAlloc,
                                                 0, 0, 0, &reducer);
        CPPUNIT_ASSERT(cache.get() != 0);
        CPPUNIT_ASSERT_EQUAL(uint64_t(0), cache->getSize());
        reducer._token = std::move(cache);
        for (uint32_t i=1; i<=50; ++i) {
            bool success = reducer._token->resize(i * 10, i * 10);
            CPPUNIT_ASSERT_EQUAL(true, success);
        }
        CPPUNIT_ASSERT_EQUAL(uint64_t(500), reducer._token->getSize());

        //   * Ordered to free space
        framework::MemoryToken::UP put = manager.allocate(putAlloc,
                                                          600, 600, 80);
        CPPUNIT_ASSERT_EQUAL_MSG(manager.toString(),
                                 uint64_t(400), reducer._token->getSize());
        CPPUNIT_ASSERT_EQUAL_MSG(manager.toString(),
                                 uint64_t(600), put->getSize());
    }
    CPPUNIT_ASSERT_EQUAL_MSG(state.toString(true),
                             uint64_t(0), current.getUsedSize());
    CPPUNIT_ASSERT_EQUAL_MSG(state.toString(true),
                             uint64_t(0), current.getUserCount());

        // Test merge and tracking of allocation counts with merge, by doing
        // operations with tokens and see that user count and used size
        // correctly go back to zero.
    {
        framework::MemoryToken::UP tok1(
                manager.allocate(putAlloc, 5, 5, 40));
        framework::MemoryToken::UP tok2(
                manager.allocate(putAlloc, 10, 10, 40));
        framework::MemoryToken::UP tok3(
                manager.allocate(putAlloc, 20, 20, 40));
        framework::MemoryToken::UP tok4(
                manager.allocate(putAlloc, 40, 40, 40));
        framework::MemoryToken::UP tok5(
                manager.allocate(putAlloc, 80, 80, 40));
        framework::MemoryToken::UP tok6(
                manager.allocate(putAlloc, 1, 1, 40));
        framework::MemoryToken::UP tok7(
                manager.allocate(putAlloc, 3, 3, 40));
    }
}

void
MemoryManagerTest::testCacheAllocation()
{
    uint64_t maxMemory = 3000;

    RealClock clock;
    SimpleMemoryLogic::UP logic(new PriorityMemoryLogic(clock, maxMemory));
    logic->setCacheThreshold(1.0);

    AllocationLogic::UP allLogic(std::move(logic));
    MemoryManager manager(std::move(allLogic));

    const MemoryAllocationType& putAlloc(manager.registerAllocationType(
            MemoryAllocationType("put", MemoryAllocationType::EXTERNAL_LOAD)));
    const MemoryAllocationType& cacheAlloc(manager.registerAllocationType(
            MemoryAllocationType("cache", MemoryAllocationType::CACHE)));

    framework::MemoryToken::UP token =
        manager.allocate(putAlloc,
                         50,
                         50,
                         127);

    CPPUNIT_ASSERT_EQUAL(50, (int)token->getSize());

    framework::MemoryToken::UP token2 =
        manager.allocate(cacheAlloc,
                         1000,
                         2000,
                         127);

    CPPUNIT_ASSERT_EQUAL(2000, (int)token2->getSize());

    token2->resize(2000, 3000);

    CPPUNIT_ASSERT_EQUAL(2950, (int)token2->getSize());
}

namespace {
struct MemoryManagerLoadGiver : public document::Runnable,
                                public ReduceMemoryUsageInterface
{
    MemoryManager& _manager;
    const framework::MemoryAllocationType& _type;
    uint8_t _priority;
    uint32_t _minMem;
    uint32_t _maxMem;
    uint32_t _failed;
    uint32_t _ok;
    uint32_t _reduced;
    using MemoryTokenUP = std::unique_ptr<MemoryToken>;
    std::vector<MemoryTokenUP> _tokens;
    vespalib::Lock _cacheLock;

    MemoryManagerLoadGiver(
            MemoryManager& manager,
            const framework::MemoryAllocationType& type,
            uint8_t priority,
            uint32_t minMem,
            uint32_t maxMem,
            uint32_t tokensToKeep)
        : _manager(manager),
          _type(type),
          _priority(priority),
          _minMem(minMem),
          _maxMem(maxMem),
          _failed(0),
          _ok(0),
          _reduced(0),
          _tokens(tokensToKeep)
    {
    }

    uint64_t reduceMemoryConsumption(const MemoryToken&, uint64_t reduceBy) {
        ++_reduced;
        return reduceBy;
    }

    void run() {
        ReduceMemoryUsageInterface* reducer = 0;
        if (_type.isCache()) reducer = this;
        vespalib::RandomGen randomizer;
        while (running()) {
            vespalib::Lock lock(_cacheLock);
            framework::MemoryToken::UP token = _manager.allocate(
                    _type, _minMem, _maxMem, _priority, reducer);
            if (token.get() == 0) {
                ++_failed;
            } else {
                ++_ok;
            }
            uint32_t index = randomizer.nextUint32(0, _tokens.size() - 1);
            _tokens[index] = MemoryTokenUP(token.release());
        }
    }
};
}

void
MemoryManagerTest::testStress()
{
    uint64_t stressTimeMS = 1 * 1000;
    uint64_t maxMemory = 1 * 1024 * 1024;
    RealClock clock;
    AllocationLogic::UP logic(new PriorityMemoryLogic(clock, maxMemory));
    MemoryManager manager(std::move(logic));

    FastOS_ThreadPool pool(128 * 1024);
    std::vector<MemoryManagerLoadGiver*> loadGivers;
    for (uint32_t type = 0; type < 5; ++type) {
        const MemoryAllocationType* allocType = 0;
        uint32_t min = 1000, max = 5000;
        if (type == 0) {
            allocType = &manager.registerAllocationType(MemoryAllocationType(
                    "default"));
        } else if (type == 1) {
            allocType = &manager.registerAllocationType(MemoryAllocationType(
                "external", MemoryAllocationType::EXTERNAL_LOAD));
        } else if (type == 2) {
            allocType = &manager.registerAllocationType(MemoryAllocationType(
                "forced", MemoryAllocationType::FORCE_ALLOCATE));
        } else if (type == 3) {
            allocType = &manager.registerAllocationType(MemoryAllocationType(
                "forcedExternal", MemoryAllocationType::FORCE_ALLOCATE
                                | MemoryAllocationType::EXTERNAL_LOAD));
        } else if (type == 4) {
            allocType = &manager.registerAllocationType(MemoryAllocationType(
                "cache", MemoryAllocationType::CACHE));
            max = 30000;
        }
        for (int priority = 0; priority < 256; priority += 8) {
            loadGivers.push_back(new MemoryManagerLoadGiver(
                        manager, *allocType, priority, min, max, 10));
            loadGivers.back()->start(pool);
        }
        FastOS_Thread::Sleep(stressTimeMS);
    }
    FastOS_Thread::Sleep(5 * stressTimeMS);
    uint64_t okTotal = 0, failedTotal = 0, reducedTotal = 0;
    for (uint32_t i = 0; i < loadGivers.size(); i++) {
        /*
        fprintf(stderr, "%d %s-%u: Failed %d, ok %d, reduced %d\n",
                i, loadGivers[i]->_type.getName().c_str(),
                uint32_t(loadGivers[i]->_priority),
                loadGivers[i]->_failed, loadGivers[i]->_ok,
                loadGivers[i]->_reduced); // */
        okTotal += loadGivers[i]->_ok;
        failedTotal += loadGivers[i]->_failed;
        reducedTotal += loadGivers[i]->_reduced;
    }
    for (uint32_t i = 0; i < loadGivers.size(); i++) loadGivers[i]->stop();
    for (uint32_t i = 0; i < loadGivers.size(); i++) loadGivers[i]->join();
    pool.Close();

    /*
    bool verbose = false;
    std::cerr << "\n\nMemory allocations at end of load:\n";
    manager.print(std::cerr, verbose, ""); // */

    for (uint32_t i = 0; i < loadGivers.size(); i++) {
        loadGivers[i]->_tokens.clear();
    }
    for (uint32_t i = 0; i < loadGivers.size(); i++) {
        delete loadGivers[i];
    }
    loadGivers.clear();

    //std::cerr << "\n\nMemory allocations at end of testl:\n";
    //manager.print(std::cerr, verbose, "");

    std::cerr << "\n  Managed " << std::fixed
              << (okTotal / (stressTimeMS / 1000))
              << " ok, " << (failedTotal / (stressTimeMS / 1000))
              << " failed and " << (reducedTotal / (stressTimeMS / 1000))
              << " reduced allocations/s.\n                             ";

    MemoryState state(clock, 1);
    manager.getState(state);
    const MemoryState::SnapShot& current(state.getCurrentSnapshot());
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), current.getUserCount());
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), current.getUsedSize());
    CPPUNIT_ASSERT_EQUAL(uint64_t(0), current.getUsedSizeIgnoringCache());
}

} // defaultimplementation
} // framework
} // storage
