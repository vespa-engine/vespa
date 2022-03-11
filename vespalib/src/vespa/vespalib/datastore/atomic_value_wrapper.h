// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <atomic>

namespace vespalib::datastore {

/**
 * Copyable atomic wrapper for a primitive value that offers value store and load
 * functionality with explicit memory ordering constraints. Intended to be used for
 * non-EntryRef values where atomicity and transitive visibility is a requirement.
 *
 * Copying always happens with relaxed ordering, as it expects that the copier has
 * already loaded the source AtomicValueWrapper with an ordering that is appropriate
 * for observing any transitive memory dependencies.
 *
 * This wrapper is intentionally not implicitly convertible to/from values of the
 * underlying primitive type.
 *
 * Note: use AtomicEntryRef instead if you're wrapping an EntryRef directly.
 */
template <typename T>
class AtomicValueWrapper {
    static_assert(std::atomic<T>::is_always_lock_free);

    std::atomic<T> _value;
public:
    constexpr AtomicValueWrapper() noexcept : _value() {}
    constexpr explicit AtomicValueWrapper(T value) noexcept : _value(value) {}
    AtomicValueWrapper(const AtomicValueWrapper& rhs) noexcept
        : _value(rhs._value.load(std::memory_order_relaxed))
    {}
    AtomicValueWrapper(AtomicValueWrapper&& rhs) noexcept
        : _value(rhs._value.load(std::memory_order_relaxed))
    {}
    AtomicValueWrapper& operator=(const AtomicValueWrapper& rhs) noexcept {
        _value.store(rhs._value.load(std::memory_order_relaxed),
                     std::memory_order_relaxed);
        return *this;
    }
    void store_release(T value) noexcept {
        _value.store(value, std::memory_order_release);
    }
    void store_relaxed(T value) noexcept {
        _value.store(value, std::memory_order_relaxed);
    }
    [[nodiscard]] T load_acquire() const noexcept {
        return _value.load(std::memory_order_acquire);
    }
    [[nodiscard]] T load_relaxed() const noexcept {
        return _value.load(std::memory_order_relaxed);
    }

    [[nodiscard]] bool operator==(const AtomicValueWrapper& rhs) const noexcept {
        return (load_relaxed() == rhs.load_relaxed());
    }
    [[nodiscard]] bool operator!=(const AtomicValueWrapper& rhs) const noexcept {
        return !(*this == rhs);
    }
};

}
