// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config.h>
#include <bit>
#include <cstdint>
#include <cstring>
#include <vector>

namespace vespalib::hwaccelerated::helper {
namespace {

inline size_t
populationCount(const uint64_t *a, size_t sz) {
    size_t count(0);
    size_t i(0);
    for (; (i + 3) < sz; i += 4) {
        count += std::popcount(a[i + 0]) +
                 std::popcount(a[i + 1]) +
                 std::popcount(a[i + 2]) +
                 std::popcount(a[i + 3]);
    }
    for (; i < sz; i++) {
        count += std::popcount(a[i]);
    }
    return count;
}

namespace {
constexpr uint8_t WORD_SZ = sizeof(uint64_t);
constexpr uint8_t UNROLL_CNT = 4;
}

inline size_t
autovec_binary_hamming_distance(const void *lhs, const void *rhs, size_t sz) noexcept {
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
                sum += std::popcount(words_a[i+j] ^ words_b[i+j]);
            }
        }
        for (; (i + 1) * WORD_SZ <= sz; ++i) {
            sum += std::popcount(words_a[i] ^ words_b[i]);
        }
    }
    if (__builtin_expect((i * WORD_SZ < sz), false)) {
        const auto *bytes_a = static_cast<const uint8_t *>(lhs);
        const auto *bytes_b = static_cast<const uint8_t *>(rhs);
        for (i *= WORD_SZ; i < sz; ++i) {
            uint64_t xor_bits = bytes_a[i] ^ bytes_b[i];
            sum += std::popcount(xor_bits);
        }
    }
    return sum;
}

#ifdef VESPA_USE_THREAD_SANITIZER
/*
 * Source bitvectors might be modified due to feeding during search.
 */
template<typename T, unsigned ChunkSize>
T get(const void * base, bool invert)__attribute__((no_sanitize("thread")));
#endif

template<typename T, unsigned ChunkSize>
T get(const void * base, bool invert) {
    static_assert(sizeof(T) == ChunkSize, "sizeof(T) == ChunkSize");
    T v;
    memcpy(&v, base, sizeof(T));
    return __builtin_expect(invert, false) ? ~v : v;
}

template <typename T, unsigned ChunkSize>
const T * cast(const void * ptr, size_t offsetBytes) {
    static_assert(sizeof(T) == ChunkSize, "sizeof(T) == ChunkSize");
    return static_cast<const T *>(static_cast<const void *>(static_cast<const char *>(ptr) + offsetBytes));
}

template<unsigned ChunkSize, unsigned Chunks>
void
andChunks(size_t offset, const std::vector<std::pair<const void *, bool>> & src, void * dest) {
    typedef uint64_t Chunk __attribute__ ((vector_size (ChunkSize)));
    static_assert(sizeof(Chunk) == ChunkSize, "sizeof(Chunk) == ChunkSize");
    static_assert(ChunkSize * Chunks == 128, "ChunkSize*Chunks == 128");
    Chunk * chunk = static_cast<Chunk *>(dest);
    const Chunk * tmp = cast<Chunk, ChunkSize>(src[0].first, offset);
    for (size_t n=0; n < Chunks; n++) {
        chunk[n] = get<Chunk, ChunkSize>(tmp+n, src[0].second);
    }
    for (size_t i(1); i < src.size(); i++) {
        tmp = cast<Chunk, ChunkSize>(src[i].first, offset);
        for (size_t n=0; n < Chunks; n++) {
            chunk[n] &= get<Chunk, ChunkSize>(tmp+n, src[i].second);
        }
    }
}

template<unsigned ChunkSize, unsigned Chunks>
void
orChunks(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) {
    typedef uint64_t Chunk __attribute__ ((vector_size (ChunkSize)));
    static_assert(sizeof(Chunk) == ChunkSize, "sizeof(Chunk) == ChunkSize");
    static_assert(ChunkSize * Chunks == 128, "ChunkSize*Chunks == 128");
    Chunk * chunk = static_cast<Chunk *>(dest);
    const Chunk * tmp = cast<Chunk, ChunkSize>(src[0].first, offset);
    for (size_t n = 0; n < Chunks; n++) {
        chunk[n] = get<Chunk, ChunkSize>(tmp + n, src[0].second);
    }
    for (size_t i(1); i < src.size(); i++) {
        tmp = cast<Chunk, ChunkSize>(src[i].first, offset);
        for (size_t n = 0; n < Chunks; n++) {
            chunk[n] |= get<Chunk, ChunkSize>(tmp + n, src[i].second);
        }
    }
}

template<typename TemporaryT=int32_t>
double squaredEuclideanDistanceT(const int8_t *a, const int8_t *b, size_t sz) __attribute__((noinline));

template<typename TemporaryT>
double squaredEuclideanDistanceT(const int8_t *a, const int8_t *b, size_t sz) {
    //Note that this is 3 times faster with int32_t than with int64_t and 16x faster than float
    TemporaryT sum = 0;
    for (size_t i(0); i < sz; i++) {
        int16_t d = int16_t(a[i]) - int16_t(b[i]);
        sum += d * d;
    }
    return sum;
}

inline double
squaredEuclideanDistance(const int8_t *a, const int8_t *b, size_t sz) {
    constexpr size_t LOOP_COUNT = 0x100;
    double sum(0);
    size_t i = 0;
    for (; i + LOOP_COUNT <= sz; i += LOOP_COUNT) {
        sum += squaredEuclideanDistanceT<int32_t>(a + i, b + i, LOOP_COUNT);
    }
    if (sz > i) [[unlikely]] {
        sum += squaredEuclideanDistanceT<int32_t>(a + i, b + i, sz - i);
    }
    return sum;
}

inline void
convert_bfloat16_to_float(const uint16_t *src, float *dest, size_t sz) noexcept {
    uint32_t *asu32 = reinterpret_cast<uint32_t *>(dest);
    for (size_t i(0); i < sz; i++) {
        asu32[i] = src[i] << 16;
    }
}

template<typename ACCUM = uint32_t>
ACCUM
multiplyAddT(const int8_t *a, const int8_t *b, size_t sz) noexcept __attribute__((noinline));

template<typename ACCUM>
ACCUM
multiplyAddT(const int8_t *a, const int8_t *b, size_t sz) noexcept {
    ACCUM sum = 0;
    for (size_t i(0); i < sz; i++) {
        sum += int16_t(a[i]) * int16_t(b[i]);
    }
    return sum;
}

inline int64_t
multiplyAdd(const int8_t *a, const int8_t *b, size_t sz) noexcept {
    constexpr size_t LOOP_COUNT = 0x100;
    int64_t sum(0);
    size_t i = 0;
    for (; i + LOOP_COUNT <= sz; i += LOOP_COUNT) {
        sum += multiplyAddT<int32_t>(a + i, b + i, LOOP_COUNT);
    }
    if (sz > i) [[unlikely]] {
        sum += multiplyAddT<int32_t>(a + i, b + i, sz - i);
    }
    return sum;
}

} // anon ns
} // vespalib::hwaccelerated::helper
