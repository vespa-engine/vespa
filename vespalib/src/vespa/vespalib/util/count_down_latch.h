// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "time.h"
#include <mutex>
#include <condition_variable>

namespace vespalib {

/**
 * A countdown latch helps one or more threads wait for the completion
 * of a number of operations performed by other threads. Specifically,
 * any thread invoking the await method will block until the countDown
 * method has been invoked an appropriate number of times. The
 * countdown latch is created with a count. Each invocation of
 * countDown will reduce the current count. When the count reaches 0,
 * the threads blocked in await will be unblocked. When the count is
 * 0, additional invocations of await will not block and additional
 * invocations of countDown will have no effect.
 **/
class CountDownLatch
{
private:
    mutable std::mutex      _lock;
    std::condition_variable _cond;
    uint32_t                _count;

    CountDownLatch(const CountDownLatch &rhs) = delete;
    CountDownLatch(CountDownLatch &&rhs) = delete;
    CountDownLatch &operator=(const CountDownLatch &rhs) = delete;
    CountDownLatch &operator=(CountDownLatch &&rhs) = delete;

public:
    /**
     * Create a countdown latch with the given initial count.
     *
     * @param cnt initial count
     **/
    CountDownLatch(uint32_t cnt) noexcept : _lock(), _cond(), _count(cnt) {}

    /**
     * Count down this latch. When the count reaches 0, all threads
     * blocked in the await method will be unblocked.
     **/
    void countDown() {
        std::lock_guard guard(_lock);
        if (_count != 0) {
            --_count;
            if (_count == 0) {
                _cond.notify_all();
            }
        }
    }

    /**
     * Wait for this latch to count down to 0. This method will block
     * until the countDown method has been invoked enough times to
     * reduce the count to 0.
     **/
    void await() {
        std::unique_lock guard(_lock);
        _cond.wait(guard, [this]() { return (_count == 0); });
    }

    /**
     * Wait for this latch to count down to 0. This method will block
     * until the countDown method has been invoked enough times to
     * reduce the count to 0 or the given amount of time has elapsed.
     *
     * @param maxwait the maximum number of milliseconds to wait
     * @return true if the counter reached 0, false if we timed out
     **/
    bool await(vespalib::duration maxwait) {
        std::unique_lock guard(_lock);
        return _cond.wait_for(guard, maxwait, [this]() { return (_count == 0); });
    }

    /**
     * Obtain the current count for this latch. This method is mostly
     * useful for debugging and testing.
     *
     * @return current count
     **/
    [[nodiscard]] uint32_t getCount() const noexcept {
        std::lock_guard guard(_lock);
        return _count;
    }

    /**
     * Empty. Needs to be virtual to reduce compiler warnings.
     **/
    virtual ~CountDownLatch();
};

} // namespace vespalib

