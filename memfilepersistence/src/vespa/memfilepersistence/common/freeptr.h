// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::FreePtr
 * @ingroup slotfile
 *
 * @brief Simple pointer wrapper that free() its content when deleted.
 *
 * Utility used to hold memory allocated with malloc directly.
 */

#pragma once

#include <iostream>
#include <sstream>

namespace storage {

template<typename T>
class FreePtr {
    T* _ptr;

public:
    FreePtr(T* ptr = 0) : _ptr(ptr) {}
    ~FreePtr() { free(); }

    FreePtr(FreePtr& ptr) : _ptr(ptr._ptr) { ptr._ptr = 0; }
    FreePtr& operator=(FreePtr& ptr) { swap(ptr); ptr.free(); return *this; }

    void reset(T* ptr = 0) { free(); _ptr = ptr; }
    void swap(FreePtr<T>& other)
        { T* tmp = _ptr; _ptr = other._ptr; other._ptr = tmp; }
    T* get() { return _ptr; }
    const T* get() const { return _ptr; }
    T* operator->() { return _ptr; }
    const T* operator->() const { return _ptr; }
    T& operator*() { assert(_ptr != 0); return *_ptr; }
    const T& operator*() const { assert(_ptr != 0); return *_ptr; }
    void free() { if (_ptr != 0) { ::free(_ptr); _ptr = 0; } }
};

} // storage

