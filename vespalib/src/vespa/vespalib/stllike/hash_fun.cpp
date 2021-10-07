// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hash_fun.h"
#include <xxhash.h>

namespace vespalib {

size_t
hashValue(const char *str) noexcept
{
    return hashValue(str, strlen(str));
}

/**
 * @brief Calculate hash value.
 *
 * The hash function XXH3_64bits from xxhash library.
 * @param buf input buffer
 * @param sz input buffer size
 * @return hash value of input
 **/
size_t
hashValue(const void * buf, size_t sz) noexcept
{
    return XXH3_64bits(buf, sz);
}

}
