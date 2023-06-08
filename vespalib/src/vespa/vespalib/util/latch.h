// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <mutex>
#include <condition_variable>

namespace vespalib {

/**
 * A latch acts like a blocking queue where the maximum capacity is a
 * single element. It enables directional exchange of data where reads
 * and writes are alternating.
 **/
template <typename T>
class Latch {
private:
    std::mutex              _lock;
    std::condition_variable _cond;
    char                    _space[sizeof(T)];
    bool                    _has_value;

    void *as_void() { return &_space[0]; }
    T *as_value() { return (T*)as_void(); }
public:
    Latch() : _lock(), _cond(), _space(), _has_value(false) {}
    ~Latch() {
        if (_has_value) {
            as_value()->~T();
        }
    }
    bool has_value() {
        std::lock_guard guard(_lock);
        return _has_value;
    }
    T read() {
        std::unique_lock guard(_lock);
        while (!_has_value) {
            _cond.wait(guard);
        }
        T value = std::move(*as_value());
        as_value()->~T();
        _has_value = false;
        _cond.notify_all();
        return value;
    }
    void write(T value) {
        std::unique_lock guard(_lock);
        while (_has_value) {
            _cond.wait(guard);
        }
        new (as_void()) T(std::move(value));
        _has_value = true;
        _cond.notify_all();
    }
};

} // namespace vespalib
