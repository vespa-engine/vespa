// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enum_lock.h"

#include <cassert>

namespace search::attribute {

EnumLock::EnumLock() noexcept : _mutex(), _cond(), _readers(0), _waiting_writers(0), _writer(false) {
}

EnumLock::~EnumLock() {
    assert(_readers == 0 && !_writer);
}

void EnumLock::lock() {
    std::unique_lock guard(_mutex);
    ++_waiting_writers;
    _cond.wait(guard, [this] { return !_writer && _readers == 0; });
    --_waiting_writers;
    _writer = true;
}

bool EnumLock::try_lock() {
    std::lock_guard guard(_mutex);
    if (_writer || _readers != 0) {
        return false;
    }
    _writer = true;
    return true;
}

void EnumLock::unlock() {
    // Notify while holding the mutex: once it is released, the lock may be destroyed.
    std::lock_guard guard(_mutex);
    assert(_writer);
    _writer = false;
    _cond.notify_all();
}

void EnumLock::lock_shared() {
    std::unique_lock guard(_mutex);
    // Only an active writer blocks a reader; a waiting one does not.
    _cond.wait(guard, [this] { return !_writer; });
    ++_readers;
}

bool EnumLock::try_lock_shared() {
    std::lock_guard guard(_mutex);
    if (_writer) {
        return false;
    }
    ++_readers;
    return true;
}

void EnumLock::unlock_shared() {
    std::lock_guard guard(_mutex);
    assert(_readers > 0);
    if (--_readers == 0) {
        _cond.notify_all(); // a writer may be waiting for the readers to drain
    }
}

uint32_t EnumLock::readers() const {
    std::lock_guard guard(_mutex);
    return _readers;
}

uint32_t EnumLock::waiting_writers() const {
    std::lock_guard guard(_mutex);
    return _waiting_writers;
}

} // namespace search::attribute
