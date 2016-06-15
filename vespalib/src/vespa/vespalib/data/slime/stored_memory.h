// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <stddef.h>
#include <vespa/vespalib/data/slime/memory.h>

namespace vespalib {
namespace slime {

/**
 * Simple class used to store a region of memory.
 **/
class StoredMemory
{
private:
    friend struct Memory;
    char  *_data;
    size_t _size;

    StoredMemory(const StoredMemory &);
    StoredMemory &operator=(const StoredMemory &);
public:
    explicit StoredMemory(const Memory &mem) :
        _data(0),
        _size(mem.size)
    {
        if (__builtin_expect(_size > 0, true)) {
            _data = static_cast<char*>(malloc(_size));
            memcpy(_data, mem.data, _size);
        }
    }

    ~StoredMemory() {
        free(_data);
    }
};

} // namespace vespalib::slime
} // namespace vespalib

