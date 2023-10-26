// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "binary_hamming_distance.h"
#include <cstdint>

namespace vespalib {

size_t binary_hamming_distance(const void *lhs, const void *rhs, size_t sz) {
    uintptr_t addr_a = (uintptr_t) lhs;
    uintptr_t addr_b = (uintptr_t) rhs;
    size_t sum = 0;
    size_t i = 0;
    static_assert(sizeof(uint64_t) == 8);
    bool aligned = ((addr_a & 0x7) == 0) && ((addr_b & 0x7) == 0);
    if (__builtin_expect(aligned, true)) {
        const uint64_t *words_a = static_cast<const uint64_t *>(lhs);
        const uint64_t *words_b = static_cast<const uint64_t *>(rhs);
        for (; i * 8 + 7 < sz; ++i) {
            uint64_t xor_bits = words_a[i] ^ words_b[i];
            sum += __builtin_popcountl(xor_bits);
        }
    }
    if (__builtin_expect((i * 8 < sz), false)) {
        const uint8_t *bytes_a = static_cast<const uint8_t *>(lhs);
        const uint8_t *bytes_b = static_cast<const uint8_t *>(rhs);
        for (i *= 8; i < sz; ++i) {
            uint64_t xor_bits = bytes_a[i] ^ bytes_b[i];
            sum += __builtin_popcountl(xor_bits);
        }
    }
    return sum;
};

}
