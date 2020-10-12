// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/sync.h>

namespace vespalib {
class TryLock
{
private:
    friend class LockGuard;
    friend class MonitorGuard;

    std::unique_lock<std::mutex> _guard;
    std::condition_variable     *_cond;

public:
    TryLock(const Monitor &mon)
        : _guard(*mon._mutex, std::try_to_lock),
          _cond(_guard ? mon._cond.get() : nullptr)
    {}
    ~TryLock() = default;

    TryLock(const TryLock &) = delete;
    TryLock &operator=(const TryLock &) = delete;

    /**
     * @brief Check whether this object holds a lock
     *
     * @return true if this object holds a lock
     **/
    bool hasLock() const { return static_cast<bool>(_guard); }
    void unlock() {
        if (_guard) {
            _guard.unlock();
            _cond = nullptr;
        }
    }
};

}
using namespace vespalib;

#define CHECK_LOCKED(m) { TryLock tl(m); EXPECT_TRUE(!tl.hasLock()); }
#define CHECK_UNLOCKED(m) { TryLock tl(m); EXPECT_TRUE(tl.hasLock()); }

class Test : public TestApp
{
private:
    Monitor _monitor;

    LockGuard    lockMonitor()   { return LockGuard(_monitor); }
    MonitorGuard obtainMonitor() { return MonitorGuard(_monitor); }
public:
    ~Test() override;
    void testCountDownLatch();
    int Main() override;
};

Test::~Test() = default;

void
Test::testCountDownLatch() {
    {
        CountDownLatch latch(5);
        EXPECT_EQUAL(latch.getCount(), 5u);
        latch.countDown();
        EXPECT_EQUAL(latch.getCount(), 4u);
        latch.countDown();
        EXPECT_EQUAL(latch.getCount(), 3u);
        latch.countDown();
        EXPECT_EQUAL(latch.getCount(), 2u);
        latch.countDown();
        EXPECT_EQUAL(latch.getCount(), 1u);
        latch.countDown();
        EXPECT_EQUAL(latch.getCount(), 0u);
        latch.countDown();
        EXPECT_EQUAL(latch.getCount(), 0u);
        latch.await(); // should not block
        latch.await(); // should not block
    }
    {
        Gate gate;
        EXPECT_EQUAL(gate.getCount(), 1u);
        gate.countDown();
        EXPECT_EQUAL(gate.getCount(), 0u);
        gate.countDown();
        EXPECT_EQUAL(gate.getCount(), 0u);
        gate.await(); // should not block
        gate.await(); // should not block
    }
    {
        Gate gate;
        EXPECT_EQUAL(gate.getCount(), 1u);
        EXPECT_EQUAL(gate.await(0), false);
        EXPECT_EQUAL(gate.await(10), false);
        gate.countDown();
        EXPECT_EQUAL(gate.getCount(), 0u);
        EXPECT_EQUAL(gate.await(0), true);
        EXPECT_EQUAL(gate.await(10), true);
    }
}

int
Test::Main()
{
    TEST_INIT("sync_test");
    // you can use a LockGuard to lock a Monitor
    {
        Monitor monitor;
        {
            CHECK_UNLOCKED(monitor);
            LockGuard guard(monitor);
            CHECK_LOCKED(monitor);
        }
        CHECK_UNLOCKED(monitor);
        {
            LockGuard guard(monitor);
            CHECK_LOCKED(monitor);
            guard.unlock();
            CHECK_UNLOCKED(monitor);
        }
    }
    {
        Monitor monitor;
        {
            CHECK_UNLOCKED(monitor);
            MonitorGuard guard(monitor);
            guard.signal();
            guard.broadcast();
            guard.wait(10);
            CHECK_LOCKED(monitor);
        }
        CHECK_UNLOCKED(monitor);
        {
            MonitorGuard guard(monitor);
            CHECK_LOCKED(monitor);
            guard.unlock();
            CHECK_UNLOCKED(monitor);
        }
    }

    // you can lock const objects

    {
        const Monitor lock;
        CHECK_UNLOCKED(lock);
        LockGuard guard(lock);
        CHECK_LOCKED(lock);
    }
    {
        const Monitor monitor;
        CHECK_UNLOCKED(monitor);
        MonitorGuard guard(monitor);
        CHECK_LOCKED(monitor);
    }
    // LockGuard/MonitorGuard have destructive move
    {
        Monitor lock;
        CHECK_UNLOCKED(lock);
        LockGuard a(lock);
        CHECK_LOCKED(lock);
        {
            CHECK_LOCKED(lock);
            LockGuard b(std::move(a));
            CHECK_LOCKED(lock);
        }
        CHECK_UNLOCKED(lock);
    }
    {
        Monitor mon;
        CHECK_UNLOCKED(mon);
        MonitorGuard a(mon);
        CHECK_LOCKED(mon);
        {
            CHECK_LOCKED(mon);
            MonitorGuard b(std::move(a));
            CHECK_LOCKED(mon);
        }
        CHECK_UNLOCKED(mon);
    }
    // Destructive copy also works for return value handover
    {
        CHECK_UNLOCKED(_monitor);
        {
            CHECK_UNLOCKED(_monitor);
            CHECK_UNLOCKED(_monitor);
            LockGuard b = lockMonitor(); // copy, not assign
            CHECK_LOCKED(_monitor);
        }
        CHECK_UNLOCKED(_monitor);
    }
    {
        CHECK_UNLOCKED(_monitor);
        {
            CHECK_UNLOCKED(_monitor);
            MonitorGuard guard(obtainMonitor());
            CHECK_LOCKED(_monitor);
        }
        CHECK_UNLOCKED(_monitor);
    }
    // Test that guards can be matched to locks/monitors
    {
        Monitor lock1;
        Monitor lock2;
        LockGuard lockGuard1(lock1);
        LockGuard lockGuard2(lock2);
        EXPECT_TRUE(lockGuard1.locks(lock1));
        EXPECT_FALSE(lockGuard1.locks(lock2));
        EXPECT_TRUE(lockGuard2.locks(lock2));
        EXPECT_FALSE(lockGuard2.locks(lock1));
        lockGuard1.unlock();
        EXPECT_FALSE(lockGuard1.locks(lock1));
    }
    {
        Monitor lock1;
        Monitor lock2;
        MonitorGuard lockGuard1(lock1);
        MonitorGuard lockGuard2(lock2);
        EXPECT_TRUE(lockGuard1.monitors(lock1));
        EXPECT_FALSE(lockGuard1.monitors(lock2));
        EXPECT_TRUE(lockGuard2.monitors(lock2));
        EXPECT_FALSE(lockGuard2.monitors(lock1));
        lockGuard1.unlock();
        EXPECT_FALSE(lockGuard1.monitors(lock1));
    }
    testCountDownLatch();
    TEST_DONE();
}

TEST_APPHOOK(Test)
