// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/storageframework/defaultimplementation/component/testcomponentregister.h>
#include <vespa/storageframework/generic/thread/tickingthread.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage {
namespace framework {
namespace defaultimplementation {

struct TickingThreadTest : public CppUnit::TestFixture
{
    void setUp() {}
    void tearDown() {}

    void testTicksBeforeWaitBasic();
    void testTicksBeforeWaitLiveUpdate();
    void testDestroyWithoutStarting();
    void testVerboseStopping();
    void testStopOnDeletion();
    void testLockAllTicks();
    void testLockCriticalTicks();
    void testFailsOnStartWithoutThreads();
    void testBroadcast();

    CPPUNIT_TEST_SUITE(TickingThreadTest);
    CPPUNIT_TEST(testTicksBeforeWaitBasic);
    CPPUNIT_TEST(testTicksBeforeWaitLiveUpdate);
    CPPUNIT_TEST(testDestroyWithoutStarting);
    CPPUNIT_TEST(testVerboseStopping);
    CPPUNIT_TEST(testStopOnDeletion);
    CPPUNIT_TEST(testLockAllTicks);
    CPPUNIT_TEST(testLockCriticalTicks);
    CPPUNIT_TEST(testFailsOnStartWithoutThreads);
    CPPUNIT_TEST(testBroadcast);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(TickingThreadTest);

namespace {

struct Context {
    uint64_t _critTickCount;
    uint64_t _nonCritTickCount;

    Context() : _critTickCount(0), _nonCritTickCount(0) {}
};

struct MyApp : public TickingThread {
    uint32_t _critOverlapCounter;
    bool _doCritOverlapTest;
    bool _critOverlap;
    std::vector<Context> _context;
    TickingThreadPool::UP _threadPool;

    MyApp(int threadCount, bool doCritOverlapTest = false);
    ~MyApp();

    void start(ThreadPool& p) { _threadPool->start(p); }

    virtual ThreadWaitInfo doCriticalTick(ThreadIndex index) {
        assert(index < _context.size());
        Context& c(_context[index]);
        if (_doCritOverlapTest) {
            uint32_t oldTick = _critOverlapCounter;
            FastOS_Thread::Sleep(1);
            _critOverlap |= (_critOverlapCounter != oldTick);
            ++_critOverlapCounter;
        }
        ++c._critTickCount;
        return ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
    }
    virtual ThreadWaitInfo doNonCriticalTick(ThreadIndex index) {
        assert(index < _context.size());
        Context& c(_context[index]);
        ++c._nonCritTickCount;
        return ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
    }

    uint64_t getMinCritTick() {
        uint64_t min = std::numeric_limits<uint64_t>().max();
        for (uint32_t i=0; i<_context.size(); ++i) {
            min = std::min(min, _context[i]._critTickCount);
        }
        return min;
    }
    uint64_t getMinNonCritTick() {
        uint64_t min = std::numeric_limits<uint64_t>().max();
        for (uint32_t i=0; i<_context.size(); ++i) {
            min = std::min(min, _context[i]._critTickCount);
        }
        return min;
    }
    uint64_t getTotalCritTicks() {
        uint64_t total = 0;
        for (uint32_t i=0; i<_context.size(); ++i) {
            total += _context[i]._critTickCount;
        }
        return total;
    }
    uint64_t getTotalNonCritTicks() {
        uint64_t total = 0;
        for (uint32_t i=0; i<_context.size(); ++i) {
            total += _context[i]._nonCritTickCount;
        }
        return total;
    }
    uint64_t getTotalTicks()
        { return getTotalCritTicks() + getTotalNonCritTicks(); }
    bool hasCritOverlap() { return _critOverlap; }
};

MyApp::MyApp(int threadCount, bool doCritOverlapTest)
    : _critOverlapCounter(0),
      _doCritOverlapTest(doCritOverlapTest),
      _critOverlap(false),
      _threadPool(TickingThreadPool::createDefault("testApp"))
{
    for (int i=0; i<threadCount; ++i) {
        _threadPool->addThread(*this);
        _context.push_back(Context());
    }
}

MyApp::~MyApp() { }

}

void
TickingThreadTest::testTicksBeforeWaitBasic()
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    int threadCount = 1;
    MyApp app(threadCount);
    app.start(testReg.getThreadPoolImpl());

    // Default behaviour is 5ms sleep before each tick. Let's do 20 ticks,
    // and verify time is in right ballpark.
    int totalSleepMs = 0;
    while (app.getTotalNonCritTicks() < 20) {
        FastOS_Thread::Sleep(1);
        totalSleepMs++;
    }
    CPPUNIT_ASSERT(totalSleepMs > 10);
    app._threadPool->stop();
}

void
TickingThreadTest::testTicksBeforeWaitLiveUpdate()
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    int threadCount = 1;
    MyApp app(threadCount);
    // Configure thread pool to send bulks of 5000 ticks each second.
    long unsigned int ticksBeforeWaitMs = 5000;
    MilliSecTime waitTimeMs(1000);
    MilliSecTime maxProcessingTime(234234);
    app.start(testReg.getThreadPoolImpl());
    app._threadPool->updateParametersAllThreads(
        waitTimeMs, maxProcessingTime, ticksBeforeWaitMs);

    // Check that 5000 ticks are received instantly (usually <2 ms)
    // (if live update is broken it will take more than an hour).
    int maxAttempts = 120000;  // a bit more than 120 secs
    while (app.getTotalNonCritTicks() < ticksBeforeWaitMs && maxAttempts-->0) {
        FastOS_Thread::Sleep(1);
    }

    CPPUNIT_ASSERT(maxAttempts>0);
    CPPUNIT_ASSERT(app.getTotalNonCritTicks() >= ticksBeforeWaitMs);
    app._threadPool->stop();
}

void
TickingThreadTest::testDestroyWithoutStarting()
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    int threadCount = 5;
    MyApp app(threadCount, true);
}

void
TickingThreadTest::testVerboseStopping()
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    int threadCount = 5;
    MyApp app(threadCount, true);
    app.start(testReg.getThreadPoolImpl());
    while (app.getMinCritTick() < 5) {
        FastOS_Thread::Sleep(1);
    }
    app._threadPool->stop();
}

void
TickingThreadTest::testStopOnDeletion()
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    int threadCount = 5;
    MyApp app(threadCount, true);
    app.start(testReg.getThreadPoolImpl());
    while (app.getMinCritTick() < 5) {
        FastOS_Thread::Sleep(1);
    }
}

void
TickingThreadTest::testLockAllTicks()
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    int threadCount = 5;
    MyApp app1(threadCount);
    MyApp app2(threadCount);
    app1.start(testReg.getThreadPoolImpl());
    app2.start(testReg.getThreadPoolImpl());
    while (std::min(app1.getMinCritTick(), app2.getMinCritTick()) < 5) {
        FastOS_Thread::Sleep(1);
    }
    uint64_t ticks1, ticks2;
    {
        TickingLockGuard guard(app1._threadPool->freezeAllTicks());
        ticks1 = app1.getTotalTicks();
        ticks2 = app2.getTotalTicks();
        
        while (app2.getMinCritTick() < 2 * ticks2 / threadCount) {
            FastOS_Thread::Sleep(1);
        }
        CPPUNIT_ASSERT_EQUAL(ticks1, app1.getTotalTicks());
    }
    while (app1.getMinCritTick() < 2 * ticks1 / threadCount) {
        FastOS_Thread::Sleep(1);
    }
}

void
TickingThreadTest::testLockCriticalTicks()
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    int threadCount = 5;
    uint64_t iterationsBeforeOverlap = 0;
    {
        MyApp app(threadCount, true);
        app.start(testReg.getThreadPoolImpl());
        while (!app.hasCritOverlap()) {
            FastOS_Thread::Sleep(1);
            ++app._critOverlapCounter;
            ++iterationsBeforeOverlap;
        }
    }
    {
        MyApp app(threadCount, true);
        app.start(testReg.getThreadPoolImpl());
        for (uint64_t i=0; i<iterationsBeforeOverlap * 10; ++i) {
            FastOS_Thread::Sleep(1);
            TickingLockGuard guard(app._threadPool->freezeCriticalTicks());
            for (int j=0; j<threadCount; ++j) {
                ++app._context[j]._critTickCount;
            }
            CPPUNIT_ASSERT(!app.hasCritOverlap());
        }
    }
}

void
TickingThreadTest::testFailsOnStartWithoutThreads()
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    int threadCount = 0;
    MyApp app(threadCount, true);
    try{
        app.start(testReg.getThreadPoolImpl());
        CPPUNIT_FAIL("Expected starting without threads to fail");
    } catch (vespalib::Exception& e) {
        CPPUNIT_ASSERT_EQUAL(vespalib::string(
                "Makes no sense to start threadpool without threads"),
                             e.getMessage());
    }
}

namespace {

RealClock clock;

void printTaskInfo(const std::string& task, const char* action) {
    vespalib::string msg = vespalib::make_string(
            "%" PRIu64 ": %s %s\n",
            clock.getTimeInMicros().getTime(),
            task.c_str(),
            action);
    // std::cerr << msg;
}

struct BroadcastApp : public TickingThread {
    std::vector<std::string> _queue;
    std::vector<std::string> _active;
    std::vector<std::string> _processed;
    TickingThreadPool::UP _threadPool;

    // Set a huge wait time by default to ensure we have to notify
    BroadcastApp();
    ~BroadcastApp();

    void start(ThreadPool& p) { _threadPool->start(p); }

    virtual ThreadWaitInfo doCriticalTick(ThreadIndex) {
        if (!_queue.empty()) {
            for (uint32_t i=0; i<_queue.size(); ++i) {
                printTaskInfo(_queue[i], "activating");
                _active.push_back(_queue[i]);
            }
            _queue.clear();
            return ThreadWaitInfo::MORE_WORK_ENQUEUED;
        }
        return ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
    }
    virtual ThreadWaitInfo doNonCriticalTick(ThreadIndex) {
        if (!_active.empty()) {
            for (uint32_t i=0; i<_active.size(); ++i) {
                printTaskInfo(_queue[i], "processing");
                _processed.push_back(_active[i]);
            }
            _active.clear();
        }
        return ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
    }

    void doTask(const std::string& task) {
        printTaskInfo(task, "enqueue");
        TickingLockGuard guard(_threadPool->freezeCriticalTicks());
        _queue.push_back(task);
        guard.broadcast();
    }
};

BroadcastApp::BroadcastApp()
    : _threadPool(TickingThreadPool::createDefault("testApp", MilliSecTime(300000)))
{
    _threadPool->addThread(*this);
}
BroadcastApp::~BroadcastApp() {}

}


void
TickingThreadTest::testBroadcast()
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    BroadcastApp app;
    app.start(testReg.getThreadPoolImpl());
    app.doTask("foo");
    FastOS_Thread::Sleep(1);
    app.doTask("bar");
    FastOS_Thread::Sleep(1);
    app.doTask("baz");
    FastOS_Thread::Sleep(1);
    app.doTask("hmm");
    FastOS_Thread::Sleep(1);
}

} // defaultimplementation
} // framework
} // storage
