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
    explicit VarHolder(T v) : _v(std::move(v)), _lock() {}
    VarHolder(const VarHolder &) = delete;
    VarHolder & operator = (const VarHolder &) = delete;
    ~VarHolder();

    void set(T v) {
        T old;
        {
            std::lock_guard guard(_lock);
            old = std::move(_v);
            _v = std::move(v);
        }
    }

    void clear() { set(T()); }

    T get() const {
        std::lock_guard guard(_lock);
        return _v;
    }
};

template <typename T>
VarHolder<T>::~VarHolder() = default;

}
