// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_hash.h"

namespace vespalib {

uint32_t hash_code(const char *str, size_t len) {
    uint32_t hash = 0;
    for (size_t i = 0; i < len; ++i) {
        hash = (hash << 5) - hash + str[i];
    }
    return hash;
}

uint32_t hash_code(vespalib::stringref str) {
    return hash_code(str.data(), str.size());
}

} // namespace vespalib
