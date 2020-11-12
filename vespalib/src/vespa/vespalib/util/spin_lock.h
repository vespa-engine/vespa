// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <atomic>

namespace vespalib {

/**
 * A spin-lock implementation that favors uncontended performance.
 * Some measures are taken to reduce the impact of threads waiting to
 * get the lock since this will not affect the fast-path of obtaining
 * the lock immediately.
 *
 * This implementation satisfies the BasicLockable requirements,
 * making it work with things like std::lock_guard.
 **/
class SpinLock {
private:
    std::atomic<bool> _lock;
public:
    SpinLock() noexcept : _lock(false) {
        static_assert(std::atomic<bool>::is_always_lock_free);
    }
    void lock() noexcept {
        while (__builtin_expect(_lock.exchange(true, std::memory_order_acquire), false)) {
            while (_lock.load(std::memory_order_relaxed)) {
                __builtin_ia32_pause();                
            }
        }
    }
    void unlock() noexcept { _lock.store(false, std::memory_order_release); }
};

}
