// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <mutex>

namespace vespalib {

template <typename T>
class VarHolder
{
    T                   _v;
    mutable std::mutex  _lock;
public:
    VarHolder() : _v(), _lock() {}
    explicit VarHolder(const T &v) : _v(v), _lock() {}
    VarHolder(const VarHolder &) = delete;
    VarHolder & operator = (const VarHolder &) = delete;
    ~VarHolder() {}

    void set(const T &v) {
        T old;
        {
            std::lock_guard guard(_lock);
            old = _v;
            _v = v;
        }
    }

    void clear() { set(T()); }

    T get() const {
        std::lock_guard guard(_lock);
        return _v;
    }
};

}
