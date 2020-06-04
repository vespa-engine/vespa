// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

template<typename T>
T get(const void * base, bool invert) {
    T v;
    memcpy(&v, base, sizeof(T));
    return __builtin_expect(invert, false) ? ~v : v;
}

template<unsigned ChunkSize, unsigned Chunks>
void
andChunks(size_t offset, const std::vector<std::pair<const uint64_t *, bool>> & src, uint64_t * dest) {
    typedef uint64_t Chunk __attribute__ ((vector_size (ChunkSize)));
    Chunk * chunk = reinterpret_cast<Chunk *>(dest);
    const Chunk * tmp = reinterpret_cast<const Chunk *>(src[0].first+offset);
    for (size_t n=0; n < Chunks; n++) {
        chunk[n] = get<Chunk>(tmp+n, src[0].second);
    }
    for (size_t i(1); i < src.size(); i++) {
        tmp = reinterpret_cast<const Chunk *>(src[i].first+offset);
        for (size_t n=0; n < Chunks; n++) {
            chunk[n] &= get<Chunk>(tmp+n, src[i].second);
        }
    }
}

template<unsigned ChunkSize, unsigned Chunks>
void
orChunks(size_t offset, const std::vector<std::pair<const uint64_t *, bool>> & src, uint64_t * dest) {
    typedef uint64_t Chunk __attribute__ ((vector_size (ChunkSize)));
    Chunk * chunk = reinterpret_cast<Chunk *>(dest);
    const Chunk * tmp = reinterpret_cast<const Chunk *>(src[0].first+offset);
    for (size_t n=0; n < Chunks; n++) {
        chunk[n] = get<Chunk>(tmp+n, src[0].second);
    }
    for (size_t i(1); i < src.size(); i++) {
        tmp = reinterpret_cast<const Chunk *>(src[i].first+offset);
        for (size_t n=0; n < Chunks; n++) {
            chunk[n] |= get<Chunk>(tmp+n, src[i].second);
        }
    }
}

}
}
