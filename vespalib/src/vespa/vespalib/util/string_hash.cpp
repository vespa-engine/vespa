// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_hash.h"
#include <vespa/vespalib/stllike/hash_fun.h>

namespace vespalib {

double hash2d(const char *str, size_t len) {
    size_t h = hashValue(str, len);
    if ((h & 0x7ff0000000000000ul) == 0x7ff0000000000000ul) {
        // Avoid nan and inf
        h = h & 0xffeffffffffffffful;
    }
    double d = 0;
    static_assert(sizeof(d) == sizeof(h));
    memcpy(&d, &h, sizeof(d));
    return d;
}

double hash2d(vespalib::stringref str) {
    return hash2d(str.data(), str.size());
}

} // namespace vespalib
