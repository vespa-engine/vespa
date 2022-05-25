// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

    Memory() noexcept : data(nullptr), size(0) {}
    Memory(const char *d, size_t s) noexcept : data(d), size(s) {}
    Memory(const char *str) noexcept : data(str), size(strlen(str)) {}
    Memory(const std::string &str) noexcept
        : data(str.data()), size(str.size()) {}
    Memory(const vespalib::string &str) noexcept
        : data(str.data()), size(str.size()) {}
    Memory(vespalib::stringref str_ref) noexcept
        : data(str_ref.data()), size(str_ref.size()) {}
    vespalib::string make_string() const;
    vespalib::stringref make_stringref() const { return stringref(data, size); }
    bool operator == (const Memory &rhs) const noexcept {
        if (size != rhs.size) {
            return false;
        }
        if ((size == 0) || (data == rhs.data)) {
            return true;
        }
        return (memcmp(data, rhs.data, size) == 0);
    }
};

std::ostream &operator<<(std::ostream &os, const Memory &memory);

} // namespace vespalib
