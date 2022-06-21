// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <cstddef>

namespace vespalib::compress {

class Integer {
public:
    /**
     * Will compress a positive integer to either 1,2 or 4 bytes
     * @param n Number to compress
     * @param destination Where to put the compressed number
     * @return number of bytes used.
     */
    static size_t compressPositive(uint64_t n, void *destination);
    /**
     * Will compress an integer to either 1,2 or 4 bytes
     * @param n Number to compress
     * @param destination Where to put the compressed number
     * @return number of bytes used.
     */
    static size_t compress(int64_t n, void *destination);
    /**
     * @param unsigned number to compute compressed size of in bytes.
     * @return Will return the number of bytes this positive number will require
     **/
    static size_t compressedPositiveLength(uint64_t n) {
        if (n < (0x1 << 6)) {
            return 1;
        } else if (n < (0x1 << 14)) {
            return 2;
        } else if ( n < (0x1 << 30)) {
            return 4;
        } else {
            throw_too_big(n);
        }
    }
    /**
     * @param number to compute compressed size of in bytes.
     * @return Will return the number of bytes this number will require
     **/
    static size_t compressedLength(int64_t n) {
        if (n < 0) {
            n = -n;
        }
        if (n < (0x1 << 5)) {
            return 1;
        } else if (n < (0x1 << 13)) {
            return 2;
        } else if ( n < (0x1 << 29)) {
            return 4;
        } else {
            throw_too_big(n);
        }
    }
    /**
     * Will decompress an integer.
     * @param pointer to buffer. pointer is automatically advanced.
     * @return decompressed number
     */
    static size_t decompress(int64_t & n, const void * srcv) {
        const uint8_t * src = static_cast<const uint8_t *>(srcv);
        const uint8_t c = src[0];
        size_t numbytes;
        if (__builtin_expect(c & 0x40, false)) {
           if (c & 0x20) {
               n = ((c & 0x1f) << 24) + (src[1] << 16) + (src[2] << 8) + src[3];
               numbytes = 4;
            } else {
               n = ((c & 0x1f) << 8) + src[1];
               numbytes = 2;
            }
        } else {
           n = c & 0x1f;
           numbytes = 1;
        }
        if (c & 0x80) {
            n = -n;
        }
        return numbytes;
    }
    /**
     * Will decompress a positive integer.
     * @param pointer to buffer. pointer is automatically advanced.
     * @return decompressed number
     */
    static size_t decompressPositive(uint64_t & n, const void * srcv) {
        const uint8_t * src = static_cast<const uint8_t *>(srcv);
        const uint8_t c = src[0];
        size_t numbytes;
        if (c & 0x80) {
           if (c & 0x40) {
               n = ((c & 0x3f) << 24) + (src[1] << 16) + (src[2] << 8) + src[3];
               numbytes = 4;
           } else {
               n = ((c & 0x3f) << 8) + src[1];
               numbytes = 2;
           }
        } else {
           n = c & 0x3f;
           numbytes = 1;
        }
        return numbytes;
    }
private:
    [[ noreturn ]] static void throw_too_big(int64_t n);
    [[ noreturn ]] static void throw_too_big(uint64_t n);
};

}
