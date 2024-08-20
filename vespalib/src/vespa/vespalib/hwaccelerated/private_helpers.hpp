// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/config.h>
#include <cstring>

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

template <typename T, size_t UNROLL, typename PartialT = T, typename ConvertElementT = T>
inline double squared_euclidean_distance_unrolled(const T* a, const T* b, size_t sz) noexcept {
    // Note that this is 3 times faster with int32_t than with int64_t and 16x faster than float
    PartialT partial[UNROLL];
    for (size_t i = 0; i < UNROLL; ++i) {
        partial[i] = 0;
    }
    size_t i = 0;
    for (; i + UNROLL <= sz; i += UNROLL) {
        for (size_t j = 0; j < UNROLL; ++j) {
            ConvertElementT d = ConvertElementT(a[i+j]) - ConvertElementT(b[i+j]);
            partial[j] += d * d;
        }
    }
    for (; i < sz; ++i) {
        ConvertElementT d = ConvertElementT(a[i]) - ConvertElementT(b[i]);
        partial[i % UNROLL] += d * d;
    }
    double sum = 0;
    for (size_t j = 0; j < UNROLL; ++j) {
        sum += partial[j];
    }
    return sum;
}

template <typename T, size_t UNROLL, typename PartialT = T, typename ConvertElementT = T>
double squared_euclidean_distance_unrolled_noinline(const T* a, const T* b, size_t sz) noexcept __attribute__((noinline));

template <typename T, size_t UNROLL, typename PartialT, typename ConvertElementT>
double squared_euclidean_distance_unrolled_noinline(const T* a, const T* b, size_t sz) noexcept {
    return squared_euclidean_distance_unrolled<T, UNROLL, PartialT, ConvertElementT>(a, b, sz);
}

inline double
squaredEuclideanDistance(const int8_t *a, const int8_t *b, size_t sz) noexcept {
    constexpr size_t LOOP_COUNT = 0x100;
    double sum(0);
    size_t i = 0;
    for (; i + LOOP_COUNT <= sz; i += LOOP_COUNT) {
        sum += squared_euclidean_distance_unrolled_noinline<int8_t, 2, int32_t, int16_t>(a + i, b + i, LOOP_COUNT);
    }
    if (sz > i) [[unlikely]] {
        sum += squared_euclidean_distance_unrolled_noinline<int8_t, 2, int32_t, int16_t>(a + i, b + i, sz - i);
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

}
}
