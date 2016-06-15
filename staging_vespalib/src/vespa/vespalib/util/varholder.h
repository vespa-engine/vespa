// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/noncopyable.hpp>
#include <vespa/vespalib/util/sync.h>

namespace vespalib
{


template <typename T>
class VarHolder : public noncopyable
{

    T			_v;
    vespalib::Lock      _lock;

public:
    VarHolder(void)
        : _v(),
          _lock()
    {
    }

    explicit
    VarHolder(const T &v)
        : _v(v),
          _lock()
    {
    }

    ~VarHolder(void)
    {
    }

    void
    set(const T &v)
    {
        T old;
        {
            vespalib::LockGuard guard(_lock);
            old = _v;
            _v = v;
        }
    }

    void
    clear(void)
    {
        set(T());
    }

    T
    get(void) const
    {
        vespalib::LockGuard guard(_lock);
        return _v;
    }
};

}  // namespace vespalib

