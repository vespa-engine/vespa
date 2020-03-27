// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hash_fun.h"

// Enable inliining when we have xxhash 0.7.3 on all platforms
// #define XXH_INLINE_ALL
#include <xxhash.h>

namespace vespalib {

size_t
hashValue(const char *str)
{
    return hashValue(str, strlen(str));
}

/**
 * @brief Calculate hash value.
 *
 * The hash function XXH64 from xxhash library.
 * @param buf input buffer
 * @param sz input buffer size
 * @return hash value of input
 **/
size_t
hashValue(const void * buf, size_t sz)
{
    return XXH64(buf, sz, 0);
}

}
