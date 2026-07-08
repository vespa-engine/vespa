// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "transient_memory_tracker.h"

#include <cassert>
#include <type_traits>

namespace vespalib {

static_assert(std::is_move_constructible_v<TransientMemoryTracker>);
static_assert(std::is_move_assignable_v<TransientMemoryTracker>);

std::mutex TransientMemoryTracker::_mutex;
size_t     TransientMemoryTracker::_total_transient_memory(0);

TransientMemoryTracker::TransientMemoryTracker() noexcept : _transient_memory(0) {
}

TransientMemoryTracker::TransientMemoryTracker(TransientMemoryTracker&& rhs) noexcept
    : _transient_memory(rhs._transient_memory) {
    rhs._transient_memory = 0;
}

TransientMemoryTracker::~TransientMemoryTracker() {
    set_transient_memory(0);
}

TransientMemoryTracker& TransientMemoryTracker::operator=(TransientMemoryTracker&& rhs) noexcept {
    std::swap(_transient_memory, rhs._transient_memory);
    rhs.set_transient_memory(0);
    return *this;
}

void TransientMemoryTracker::set_transient_memory(size_t value) noexcept {
    if (value == _transient_memory) {
        return;
    }
    set_transient_memory(acquire_lock(), value);
};

void TransientMemoryTracker::set_transient_memory(size_t value, size_t slack) noexcept {
    if (value <= _transient_memory + slack && value + slack >= _transient_memory) {
        return;
    }
    set_transient_memory(acquire_lock(), value);
};

void TransientMemoryTracker::set_transient_memory(Lock lock, size_t value) noexcept {
    assert(lock.mutex() == &_mutex);
    assert(lock.owns_lock());
    if (value == _transient_memory) {
        return;
    }
    if (value > _transient_memory) {
        _total_transient_memory += value - _transient_memory;
    } else if (value < _transient_memory) {
        _total_transient_memory -= _transient_memory - value;
    }
    _transient_memory = value;
}

void TransientMemoryTracker::swap(TransientMemoryTracker& rhs) noexcept {
    std::swap(_transient_memory, rhs._transient_memory);
}

size_t TransientMemoryTracker::get_total_transient_memory(Lock lock) noexcept {
    assert(lock.mutex() == &_mutex);
    assert(lock.owns_lock());
    return _total_transient_memory;
}

} // namespace vespalib
