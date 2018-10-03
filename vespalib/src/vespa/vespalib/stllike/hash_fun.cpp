// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hash_fun.h"

namespace vespalib {

size_t
hashValue(const char *str)
{
    size_t res = 0;
    unsigned const char *pt = (unsigned const char *) str;
    while (*pt != 0) {
        res = (res << 7) + (res >> 25) + *pt++;
    }
    return res;
}

/**
 * @brief Calculate hash value.
 *
 * This is the hash function used by the HashMap class.
 * The hash function is inherited from Fastserver4 / FastLib / pandora.
 * @param buf input buffer
 * @param sz input buffer size
 * @return hash value of input
 **/
size_t
hashValue(const void * buf, size_t sz)
{
    size_t res = 0;
    unsigned const char *pt = (unsigned const char *) buf;
    for (size_t i(0); i < sz; i++) {
        res = (res << 7) + (res >> 25) + pt[i];
    }
    return res;
}

}
