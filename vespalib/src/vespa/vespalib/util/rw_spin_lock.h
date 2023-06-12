// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <mutex>
#include <shared_mutex>
#include <atomic>
#include <thread>
#include <cassert>
#include <utility>

namespace vespalib {

/**
 * A reader-writer spin lock implementation.
 *
 * reader:  shared access for any number of readers
 * writer:  exclusive access for a single writer
 *
 * valid lock combinations:
 *   {}
 *   {N readers}
 *   {1 writer}
 *
 * Trying to obtain a write lock will lead to not granting new read
 * locks.
 *
 * This lock is intended for use-cases that involves mostly reading,
 * with a little bit of writing.
 *
 * This class implements the Lockable and SharedLockable named
 * requirements from the standard library, making it directly usable
 * with std::shared_lock (reader) and std::unique_lock (writer)
 *
 * There is also some special glue added for lock upgrading and
 * downgrading.
 *
 * NOTE: this implementation is experimental, mostly intended for
 *       benchmarking and trying to identify use-cases that work with
 *       rw locks. Upgrade locks that do not block readers might be
 *       implementet in the future.
 **/
class RWSpinLock {
private:
    // [31: num readers][1: pending writer]
    // a reader gets the lock by:
    //   increasing the number of readers while the pending writer bit is not set.
    // a writer gets the lock by:
    //   changing the pending writer bit from 0 to 1 and then
    //   waiting for the number of readers to become 0
    // an upgrade is successful when:
    //   a reader is able to obtain the pending writer bit
    std::atomic<uint32_t> _state;

    // Convenience function used to check if the pending writer bit is
    // set in the given value.
    bool has_pending_writer(uint32_t value) noexcept {
        return (value & 1);
    }

    // Wait for all readers to release their locks.
    void wait_for_zero_readers(uint32_t &value) {
        while (value != 1) {
            std::this_thread::yield();
            value = _state.load(std::memory_order_acquire);
        }
    }

public:
    RWSpinLock() noexcept : _state(0) {
        static_assert(std::atomic<uint32_t>::is_always_lock_free);
    }

    // implementation of Lockable named requirement - vvv

    void lock() noexcept {
        uint32_t expected = 0;
        uint32_t desired = 1;
        while (!_state.compare_exchange_weak(expected, desired,
                                             std::memory_order_acquire,
                                             std::memory_order_relaxed))
        {
            while (has_pending_writer(expected)) {
                std::this_thread::yield();
                expected = _state.load(std::memory_order_relaxed);
            }
            desired = expected + 1;
        }
        wait_for_zero_readers(desired);
    }

    [[nodiscard]] bool try_lock() noexcept {
        uint32_t expected = 0;
        return _state.compare_exchange_strong(expected, 1,
                                              std::memory_order_acquire,
                                              std::memory_order_relaxed);
    }

    void unlock() noexcept {
        _state.store(0, std::memory_order_release);
    }

    // implementation of Lockable named requirement - ^^^

    // implementation of SharedLockable named requirement - vvv

    void lock_shared() noexcept {
        uint32_t expected = 0;
        uint32_t desired = 2;
        while (!_state.compare_exchange_weak(expected, desired,
                                             std::memory_order_acquire,
                                             std::memory_order_relaxed))
        {
            while (has_pending_writer(expected)) {
                std::this_thread::yield();
                expected = _state.load(std::memory_order_relaxed);
            }
            desired = expected + 2;
        }
    }

    [[nodiscard]] bool try_lock_shared() noexcept {
        uint32_t expected = 0;
        uint32_t desired = 2;
        while (!_state.compare_exchange_weak(expected, desired,
                                             std::memory_order_acquire,
                                             std::memory_order_relaxed))
        {
            if (has_pending_writer(expected)) {
                return false;
            }
            desired = expected + 2;
        }
        return true;
    }

    void unlock_shared() noexcept {
        _state.fetch_sub(2, std::memory_order_release);
    }

    // implementation of SharedLockable named requirement - ^^^

    // try to upgrade a read (shared) lock to a write (unique) lock
    bool try_convert_read_to_write() noexcept {
        uint32_t expected = 2;
        uint32_t desired = 1;
        while (!_state.compare_exchange_weak(expected, desired,
                                             std::memory_order_acquire,
                                             std::memory_order_relaxed))
        {
            if (has_pending_writer(expected)) {
                return false;
            }
            desired = expected - 1;
        }
        wait_for_zero_readers(desired);
        return true;
    }

    // convert a write (unique) lock to a read (shared) lock
    void convert_write_to_read() noexcept {
        _state.store(2, std::memory_order_release);        
    }
};

template<typename T>
concept rw_upgrade_downgrade_lock = requires(T a, T b) {
    { a.try_convert_read_to_write() } -> std::same_as<bool>;
    { b.convert_write_to_read() } -> std::same_as<void>;
};

template <rw_upgrade_downgrade_lock T>
[[nodiscard]] std::unique_lock<T> try_upgrade(std::shared_lock<T> &&guard) noexcept {
    assert(guard.owns_lock());
    if (guard.mutex()->try_convert_read_to_write()) {
        return {*guard.release(), std::adopt_lock};
    } else {
        return {};
    }
}

template <rw_upgrade_downgrade_lock T>
[[nodiscard]] std::shared_lock<T> downgrade(std::unique_lock<T> &&guard) noexcept {
    assert(guard.owns_lock());
    guard.mutex()->convert_write_to_read();
    return {*guard.release(), std::adopt_lock};
}

}
