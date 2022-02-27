// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/optimized.h>
#include <cstring>

namespace vespalib::hwaccelrated::helper {
namespace {

inline size_t
populationCount(const uint64_t *a, size_t sz) {
    size_t count(0);
    size_t i(0);
    for (; (i + 3) < sz; i += 4) {
        count += Optimized::popCount(a[i + 0]) +
                 Optimized::popCount(a[i + 1]) +
                 Optimized::popCount(a[i + 2]) +
                 Optimized::popCount(a[i + 3]);
    }
    for (; i < sz; i++) {
        count += Optimized::popCount(a[i]);
    }
    return count;
}

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
    static_assert(ChunkSize*Chunks == 64, "ChunkSize*Chunks == 64");
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
orChunks(size_t offset, const std::vector<std::pair<const void *, bool>> & src, void * dest) {
    typedef uint64_t Chunk __attribute__ ((vector_size (ChunkSize)));
    static_assert(sizeof(Chunk) == ChunkSize, "sizeof(Chunk) == ChunkSize");
    static_assert(ChunkSize*Chunks == 64, "ChunkSize*Chunks == 64");
    Chunk * chunk = static_cast<Chunk *>(dest);
    const Chunk * tmp = cast<Chunk, ChunkSize>(src[0].first, offset);
    for (size_t n=0; n < Chunks; n++) {
        chunk[n] = get<Chunk, ChunkSize>(tmp+n, src[0].second);
    }
    for (size_t i(1); i < src.size(); i++) {
        tmp = cast<Chunk, ChunkSize>(src[i].first, offset);
        for (size_t n=0; n < Chunks; n++) {
            chunk[n] |= get<Chunk, ChunkSize>(tmp+n, src[i].second);
        }
    }
}

template<typename TemporaryT=int32_t>
double squaredEuclideanDistanceT(const int8_t * a, const int8_t * b, size_t sz) __attribute__((noinline));
template<typename TemporaryT>
double squaredEuclideanDistanceT(const int8_t * a, const int8_t * b, size_t sz)
{
    //Note that this is 3 times faster with int32_t than with int64_t and 16x faster than float
    TemporaryT sum = 0;
    for (size_t i(0); i < sz; i++) {
        int16_t d = int16_t(a[i]) - int16_t(b[i]);
        sum += d * d;
    }
    return sum;
}

inline double
squaredEuclideanDistance(const int8_t * a, const int8_t * b, size_t sz) {
    constexpr size_t LOOP_COUNT = 0x10000;
    double sum(0);
    size_t i=0;
    for (; i + LOOP_COUNT <= sz; i += LOOP_COUNT) {
        sum += squaredEuclideanDistanceT<int32_t>(a + i, b + i, LOOP_COUNT);
    }
    sum += squaredEuclideanDistanceT<int32_t>(a + i, b + i, sz - i);
    return sum;
}

}
}
