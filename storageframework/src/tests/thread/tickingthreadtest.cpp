// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/storageframework/defaultimplementation/component/testcomponentregister.h>
#include <vespa/storageframework/generic/thread/tickingthread.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/time.h>
#include <thread>

namespace storage::framework::defaultimplementation {

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

    ThreadWaitInfo doCriticalTick(ThreadIndex index) override {
        assert(index < _context.size());
        Context& c(_context[index]);
        if (_doCritOverlapTest) {
            uint32_t oldTick = _critOverlapCounter;
            std::this_thread::sleep_for(1ms);
            _critOverlap |= (_critOverlapCounter != oldTick);
            ++_critOverlapCounter;
        }
        ++c._critTickCount;
        return ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
    }
    ThreadWaitInfo doNonCriticalTick(ThreadIndex index) override {
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

TEST(TickingThreadTest, test_ticks_before_wait_basic)
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
        std::this_thread::sleep_for(1ms);
        totalSleepMs++;
    }
    EXPECT_GT(totalSleepMs, 10);
    app._threadPool->stop();
}

TEST(TickingThreadTest, test_ticks_before_wait_live_update)
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
        std::this_thread::sleep_for(1ms);
    }

    EXPECT_GT(maxAttempts, 0);
    EXPECT_GE(app.getTotalNonCritTicks(), ticksBeforeWaitMs);
    app._threadPool->stop();
}

TEST(TickingThreadTest, test_destroy_without_starting)
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    int threadCount = 5;
    MyApp app(threadCount, true);
}

TEST(TickingThreadTest, test_verbose_stopping)
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    int threadCount = 5;
    MyApp app(threadCount, true);
    app.start(testReg.getThreadPoolImpl());
    while (app.getMinCritTick() < 5) {
        std::this_thread::sleep_for(1ms);
    }
    app._threadPool->stop();
}

TEST(TickingThreadTest, test_stop_on_deletion)
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    int threadCount = 5;
    MyApp app(threadCount, true);
    app.start(testReg.getThreadPoolImpl());
    while (app.getMinCritTick() < 5) {
        std::this_thread::sleep_for(1ms);
    }
}

TEST(TickingThreadTest, test_lock_all_ticks)
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    int threadCount = 5;
    MyApp app1(threadCount);
    MyApp app2(threadCount);
    app1.start(testReg.getThreadPoolImpl());
    app2.start(testReg.getThreadPoolImpl());
    while (std::min(app1.getMinCritTick(), app2.getMinCritTick()) < 5) {
        std::this_thread::sleep_for(1ms);
    }
    uint64_t ticks1, ticks2;
    {
        TickingLockGuard guard(app1._threadPool->freezeAllTicks());
        ticks1 = app1.getTotalTicks();
        ticks2 = app2.getTotalTicks();
        
        while (app2.getMinCritTick() < 2 * ticks2 / threadCount) {
            std::this_thread::sleep_for(1ms);
        }
        EXPECT_EQ(ticks1, app1.getTotalTicks());
    }
    while (app1.getMinCritTick() < 2 * ticks1 / threadCount) {
        std::this_thread::sleep_for(1ms);
    }
}

TEST(TickingThreadTest, test_lock_critical_ticks)
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    int threadCount = 5;
    uint64_t iterationsBeforeOverlap = 0;
    {
        MyApp app(threadCount, true);
        app.start(testReg.getThreadPoolImpl());
        while (!app.hasCritOverlap()) {
            std::this_thread::sleep_for(1ms);
            ++app._critOverlapCounter;
            ++iterationsBeforeOverlap;
        }
    }
    {
        MyApp app(threadCount, true);
        app.start(testReg.getThreadPoolImpl());
        for (uint64_t i=0; i<iterationsBeforeOverlap * 10; ++i) {
            std::this_thread::sleep_for(1ms);
            TickingLockGuard guard(app._threadPool->freezeCriticalTicks());
            for (int j=0; j<threadCount; ++j) {
                ++app._context[j]._critTickCount;
            }
            EXPECT_TRUE(!app.hasCritOverlap());
        }
    }
}

TEST(TickingThreadTest, test_fails_on_start_without_threads)
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    int threadCount = 0;
    MyApp app(threadCount, true);
    try{
        app.start(testReg.getThreadPoolImpl());
        FAIL() << "Expected starting without threads to fail";
    } catch (vespalib::Exception& e) {
        EXPECT_EQ(vespalib::string("Makes no sense to start threadpool without threads"),
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

    ThreadWaitInfo doCriticalTick(ThreadIndex) override {
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
    ThreadWaitInfo doNonCriticalTick(ThreadIndex) override {
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

TEST(TickingThreadTest, test_broadcast)
{
    TestComponentRegister testReg(
            ComponentRegisterImpl::UP(new ComponentRegisterImpl));
    BroadcastApp app;
    app.start(testReg.getThreadPoolImpl());
    app.doTask("foo");
    std::this_thread::sleep_for(1ms);
    app.doTask("bar");
    std::this_thread::sleep_for(1ms);
    app.doTask("baz");
    std::this_thread::sleep_for(1ms);
    app.doTask("hmm");
    std::this_thread::sleep_for(1ms);
}

}
