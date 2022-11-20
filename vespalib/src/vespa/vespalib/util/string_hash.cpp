// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_hash.h"

namespace vespalib {

double hash2d(const char *str, size_t len) {
    uint32_t hash = 0;
    for (size_t i = 0; i < len; ++i) {
        hash = (hash << 5) - hash + str[i];
    }
    return hash;
}

double hash2d(vespalib::stringref str) {
    return hash2d(str.data(), str.size());
}

} // namespace vespalib
