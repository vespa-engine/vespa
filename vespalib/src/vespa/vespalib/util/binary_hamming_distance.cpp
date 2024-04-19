// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "binary_hamming_distance.h"
#include <cstdint>

namespace vespalib {

namespace {
    constexpr uint8_t WORD_SZ = sizeof (uint64_t);
    constexpr uint8_t UNROLL_CNT = 3;
    static_assert(sizeof(uint64_t) == 8);
}
size_t
binary_hamming_distance(const void *lhs, const void *rhs, size_t sz) noexcept {
    auto addr_a = (uintptr_t) lhs;
    auto addr_b = (uintptr_t) rhs;
    size_t sum = 0;
    size_t i = 0;
    bool aligned = ((addr_a & 0x7) == 0) && ((addr_b & 0x7) == 0);
    if (__builtin_expect(aligned, true)) {
        const auto *words_a = static_cast<const uint64_t *>(lhs);
        const auto *words_b = static_cast<const uint64_t *>(rhs);
        for (; (i+UNROLL_CNT) * WORD_SZ <= sz; i += UNROLL_CNT) {
            for (uint8_t j=0; j < UNROLL_CNT; j++) {
                sum += __builtin_popcountl(words_a[i+j] ^ words_b[i+j]);
            }
        }
        for (; (i + 1) * WORD_SZ <= sz; ++i) {
            sum += __builtin_popcountl(words_a[i] ^ words_b[i]);
        }
    }
    if (__builtin_expect((i * WORD_SZ < sz), false)) {
        const auto *bytes_a = static_cast<const uint8_t *>(lhs);
        const auto *bytes_b = static_cast<const uint8_t *>(rhs);
        for (i *= WORD_SZ; i < sz; ++i) {
            uint64_t xor_bits = bytes_a[i] ^ bytes_b[i];
            sum += __builtin_popcountl(xor_bits);
        }
    }
    return sum;
};

}
