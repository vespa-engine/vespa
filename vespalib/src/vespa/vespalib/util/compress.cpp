// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compress.h"
#include "stringfmt.h"
#include "exceptions.h"

namespace vespalib::compress {

size_t Integer::compressPositive(uint64_t n, void *destination)
{
    uint8_t * d = static_cast<uint8_t *>(destination);
    if (n < (0x1 << 6)) {
        d[0] = n;
        return 1;
    } else if (n < (0x1 << 14)) {
        d[0] = (n >> 8) | 0x80;
        d[1] = n & 0xff;
        return 2;
    } else if ( n < (0x1 << 30)) {
        n = n | 0xc0000000;
        d[0] = (n >> 24) & 0xff;
        d[1] = (n >> 16) & 0xff;
        d[2] = (n >> 8) & 0xff;
        d[3] = n & 0xff;
        return 4;
    } else {
        throw_too_big(n);
    }
}


void
Integer::throw_too_big(int64_t n) {
    throw IllegalArgumentException(make_string("Number '%" PRId64 "' too big, must extend encoding", n));
}

void
Integer::throw_too_big(uint64_t n) {
    throw IllegalArgumentException(make_string("Number '%" PRIu64 "' too big, must extend encoding", n));
}

size_t Integer::compress(int64_t n, void *destination)
{
    uint8_t * d = static_cast<uint8_t *>(destination);
    int negative = n < 0 ? 0x80 : 0x0;
    if (negative != 0) {
        n = -n;
    }
    if (n < (0x1 << 5)) {
        d[0] = n | negative;
        return 1;
    } else if (n < (0x1 << 13)) {
        d[0] = (n >> 8) | 0x40 | negative;
        d[1] = n & 0xff;
        return 2;
    } else if ( n < (0x1 << 29)) {
        d[0] = ((n >> 24) | 0x60 | negative) & 0xff;
        d[1] = (n >> 16) & 0xff;
        d[2] = (n >> 8) & 0xff;
        d[3] = n & 0xff;
        return 4;
    } else {
        throw_too_big(negative ? -n : n);
    }
}

}
