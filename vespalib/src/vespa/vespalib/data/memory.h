// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <ostream>

namespace vespalib {

/**
 * Simple wrapper referencing a read-only region of memory.
 **/
struct Memory
{
    const char *data;
    size_t      size;

    Memory() : data(nullptr), size(0) {}
    Memory(const char *d, size_t s) : data(d), size(s) {}
    Memory(const char *str) : data(str), size(strlen(str)) {}
    Memory(const std::string &str)
        : data(str.data()), size(str.size()) {}
    Memory(const vespalib::string &str)
        : data(str.data()), size(str.size()) {}
    Memory(const vespalib::stringref &str_ref)
        : data(str_ref.data()), size(str_ref.size()) {}
    vespalib::string make_string() const;
    vespalib::stringref make_stringref() const { return stringref(data, size); }
    bool operator == (const Memory &rhs) const {
        return ((size == rhs.size) &&
                ((data == rhs.data) ||
                 (memcmp(data, rhs.data, size) == 0)));
    }
};

std::ostream &operator<<(std::ostream &os, const Memory &memory);

} // namespace vespalib
