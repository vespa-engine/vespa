// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hash_fun.h"
#include <xxhash.h>

namespace vespalib {

size_t
hashValue(const char *str) noexcept {
    return xxhash::xxh3_64(str, strlen(str));
}

namespace xxhash {

uint64_t
xxh3_64(uint64_t value) noexcept {
    return XXH3_64bits(&value, sizeof(value));
}

uint64_t
xxh3_64(const void * buf, size_t sz) noexcept {
    return XXH3_64bits(buf, sz);
}

}
}
