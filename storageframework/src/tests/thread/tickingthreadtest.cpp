// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageframework/defaultimplementation/clock/realclock.h>
#include <vespa/storageframework/defaultimplementation/component/testcomponentregister.h>
#include <vespa/storageframework/generic/thread/tickingthread.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/atomic.h>
#include <thread>

using namespace vespalib::atomic;

namespace storage::framework::defaultimplementation {

namespace {

struct Context {
    std::atomic<uint64_t> _critTickCount;
    std::atomic<uint64_t> _nonCritTickCount;

    constexpr Context() noexcept : _critTickCount(0), _nonCritTickCount(0) {}
    Context(const Context& rhs) noexcept
        : _critTickCount(load_relaxed(rhs._critTickCount)),
          _nonCritTickCount(load_relaxed(rhs._nonCritTickCount))
    {}
};

struct MyApp : public TickingThread {
    std::atomic<uint32_t> _critOverlapCounter;
    std::atomic<bool>     _critOverlap;
    bool                  _doCritOverlapTest;
    std::vector<Context>  _context;
    TickingThreadPool::UP _threadPool;

    MyApp(int threadCount, bool doCritOverlapTest = false);
    ~MyApp() override;

    void start(ThreadPool& p) { _threadPool->start(p); }

    ThreadWaitInfo doCriticalTick(ThreadIndex index) override {
        assert(index < _context.size());
        Context& c(_context[index]);
        if (_doCritOverlapTest) {
            uint32_t oldTick = load_relaxed(_critOverlapCounter);
            std::this_thread::sleep_for(1ms);
            store_relaxed(_critOverlap, load_relaxed(_critOverlap) || (load_relaxed(_critOverlapCounter) != oldTick));
            _critOverlapCounter.fetch_add(1, std::memory_order_relaxed);
        }
        c._critTickCount.fetch_add(1, std::memory_order_relaxed);
        return ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
    }
    ThreadWaitInfo doNonCriticalTick(ThreadIndex index) override {
        assert(index < _context.size());
        Context& c(_context[index]);
        c._nonCritTickCount.fetch_add(1, std::memory_order_relaxed);
        return ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
    }
    uint64_t getMinCritTick() {
        uint64_t min = std::numeric_limits<uint64_t>().max();
        for (uint32_t i=0; i<_context.size(); ++i) {
            min = std::min(min, load_relaxed(_context[i]._critTickCount));
        }
        return min;
    }
    uint64_t getMinNonCritTick() {
        uint64_t min = std::numeric_limits<uint64_t>().max();
        for (uint32_t i=0; i<_context.size(); ++i) {
            min = std::min(min, load_relaxed(_context[i]._critTickCount));
        }
        return min;
    }
    uint64_t getTotalCritTicks() const noexcept {
        uint64_t total = 0;
        for (uint32_t i=0; i<_context.size(); ++i) {
            total += load_relaxed(_context[i]._critTickCount);
        }
        return total;
    }
    uint64_t getTotalNonCritTicks() const noexcept {
        uint64_t total = 0;
        for (uint32_t i=0; i<_context.size(); ++i) {
            total += load_relaxed(_context[i]._nonCritTickCount);
        }
        return total;
    }
    uint64_t getTotalTicks() const noexcept {
        return getTotalCritTicks() + getTotalNonCritTicks();
    }
    bool hasCritOverlap() const noexcept { return load_relaxed(_critOverlap); }
};

MyApp::MyApp(int threadCount, bool doCritOverlapTest)
    : _critOverlapCounter(0),
      _critOverlap(false),
      _doCritOverlapTest(doCritOverlapTest),
      _threadPool(TickingThreadPool::createDefault("testApp", 100ms))
{
    for (int i=0; i<threadCount; ++i) {
        _threadPool->addThread(*this);
        _context.emplace_back();
    }
}

MyApp::~MyApp() = default;

}

TEST(TickingThreadTest, test_ticks_before_wait_basic)
{
    TestComponentRegister testReg(std::make_unique<ComponentRegisterImpl>());
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

TEST(TickingThreadTest, test_destroy_without_starting)
{
    TestComponentRegister testReg(std::make_unique<ComponentRegisterImpl>());
    int threadCount = 5;
    MyApp app(threadCount, true);
}

TEST(TickingThreadTest, test_verbose_stopping)
{
    TestComponentRegister testReg(std::make_unique<ComponentRegisterImpl>());
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
    TestComponentRegister testReg(std::make_unique<ComponentRegisterImpl>());
    int threadCount = 5;
    MyApp app(threadCount, true);
    app.start(testReg.getThreadPoolImpl());
    while (app.getMinCritTick() < 5) {
        std::this_thread::sleep_for(1ms);
    }
}

TEST(TickingThreadTest, test_lock_all_ticks)
{
    TestComponentRegister testReg(std::make_unique<ComponentRegisterImpl>());
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
    TestComponentRegister testReg(std::make_unique<ComponentRegisterImpl>());
    int threadCount = 5;
    uint64_t iterationsBeforeOverlap = 0;
    {
        MyApp app(threadCount, true);
        app.start(testReg.getThreadPoolImpl());
        while (!app.hasCritOverlap()) {
            std::this_thread::sleep_for(1ms);
            app._critOverlapCounter.fetch_add(1, std::memory_order_relaxed);
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
                printTaskInfo(_active[i], "processing");
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
    : _threadPool(TickingThreadPool::createDefault("testApp", 300s))
{
    _threadPool->addThread(*this);
}
BroadcastApp::~BroadcastApp() = default;

}

TEST(TickingThreadTest, test_broadcast)
{
    TestComponentRegister testReg(std::make_unique<ComponentRegisterImpl>());
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
