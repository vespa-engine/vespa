// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/sync.h>

namespace vespalib {

template <typename T>
class VarHolder
{
    T     _v;
    Lock  _lock;
public:
    VarHolder() : _v(), _lock() {}
    explicit VarHolder(const T &v) : _v(v), _lock() {}
    VarHolder(const VarHolder &) = delete;
    VarHolder & operator = (const VarHolder &) = delete;
    ~VarHolder() {}

    void set(const T &v) {
        T old;
        {
            vespalib::LockGuard guard(_lock);
            old = _v;
            _v = v;
        }
    }

    void clear() { set(T()); }

    T get() const {
        vespalib::LockGuard guard(_lock);
        return _v;
    }
};

}
