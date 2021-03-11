// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entryref.h"
#include <atomic>

namespace vespalib::datastore {

/**
 * A wrapper for std::atomic of type EntryRef that supports copy and move constructors and assignment operator,
 * and uses Release-Acquire ordering for store and load.
 *
 * Use this class when entry refs are stored in data stores or rcu vectors,
 * where copy and move constructors and assignment operator are needed when resizing underlying buffers.
 * In this case synchronization between the writer thread and reader threads
 * is handled as part of the buffer switch.
 */
class AtomicEntryRef {
private:
    std::atomic<uint32_t> _ref;

public:
    AtomicEntryRef() noexcept : _ref() {}
    explicit AtomicEntryRef(EntryRef ref) noexcept : _ref(ref.ref()) {}
    AtomicEntryRef(const AtomicEntryRef& rhs) noexcept : _ref(rhs._ref.load(std::memory_order_relaxed)) {}
    AtomicEntryRef(AtomicEntryRef&& rhs) noexcept : _ref(rhs._ref.load(std::memory_order_relaxed)) {}
    AtomicEntryRef& operator=(const AtomicEntryRef& rhs) noexcept {
        uint32_t ref = rhs._ref.load(std::memory_order_relaxed);
        _ref.store(ref, std::memory_order_relaxed);
        return *this;
    }

    void store_release(EntryRef ref) noexcept {
        _ref.store(ref.ref(), std::memory_order_release);
    }
    EntryRef load_acquire() const noexcept {
        return EntryRef(_ref.load(std::memory_order_acquire));
    }
    EntryRef load_relaxed() const noexcept {
        return EntryRef(_ref.load(std::memory_order_relaxed));
    }
};

}
