// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_fun.h>

namespace vespalib {
namespace slime {

class StoredMemory;

/**
 * Simple wrapper used to reference a region of memory.
 **/
struct Memory
{
    const char *data;
    size_t      size;

    Memory() : data(0), size(0) {}
    Memory(const char *d, size_t s) : data(d), size(s) {}
    Memory(const char *str) : data(str), size(strlen(str)) {}
    Memory(const std::string &str)
        : data(str.data()), size(str.size()) {}
    Memory(const vespalib::string &str)
        : data(str.data()), size(str.size()) {}
    Memory(const stringref &str)
        : data(str.data()), size(str.size()) {}
    explicit Memory(const StoredMemory &sm);
    vespalib::string make_string() const;
    bool operator == (const Memory & rhs) const {
        return (size == rhs.size) &&
               ( (data == rhs.data) ||
                 (memcmp(data, rhs.data, size) == 0) );
    }
    size_t hash() const {
        return vespalib::hashValue(data, size);
    }
};

} // namespace vespalib::slime
} // namespace vespalib

