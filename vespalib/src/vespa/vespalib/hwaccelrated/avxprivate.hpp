// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "private_helpers.hpp"

namespace vespalib::hwaccelrated::avx {

namespace {

inline bool validAlignment(const void * p, const size_t align) {
    return (reinterpret_cast<uint64_t>(p) & (align-1)) == 0;
}

template <typename T, typename V>
T sumT(const V & v) {
    T sum(0);
    for (size_t i(0); i < (sizeof(V)/sizeof(T)); i++) {
        sum += v[i];
    }
    return sum;
}

template <typename T, size_t C>
T sumR(const T * v) {
    if (C == 1) {
        return v[0];
    } else if (C == 2) {
        return v[0] + v[1];
    } else {
        return sumR<T, C/2>(v) + sumR<T, C/2>(v+C/2);
    }
}

template <typename T, size_t VLEN, unsigned AlignA, unsigned AlignB, size_t VectorsPerChunk>
static T computeDotProduct(const T * af, const T * bf, size_t sz) __attribute__((noinline));

template <typename T, size_t VLEN, unsigned AlignA, unsigned AlignB, size_t VectorsPerChunk>
T computeDotProduct(const T * af, const T * bf, size_t sz)
{
    constexpr const size_t ChunkSize = VLEN*VectorsPerChunk/sizeof(T);
    typedef T V __attribute__ ((vector_size (VLEN)));
    typedef T A __attribute__ ((vector_size (VLEN), aligned(AlignA)));
    typedef T B __attribute__ ((vector_size (VLEN), aligned(AlignB)));
    V partial[VectorsPerChunk];
    memset(partial, 0, sizeof(partial));
    const A * a = reinterpret_cast<const A *>(af);
    const B * b = reinterpret_cast<const B *>(bf);

    const size_t numChunks(sz/ChunkSize);
    for (size_t i(0); i < numChunks; i++) {
        for (size_t j(0); j < VectorsPerChunk; j++) {
            partial[j] += a[VectorsPerChunk*i+j] * b[VectorsPerChunk*i+j];
        }
    }
    T sum(0);
    for (size_t i(numChunks*ChunkSize); i < sz; i++) {
        sum += af[i] * bf[i];
    }
    partial[0] = sumR<V, VectorsPerChunk>(partial);

    return sum + sumT<T, V>(partial[0]);
}

}

template <typename T, size_t VLEN, size_t VectorsPerChunk=4>
VESPA_DLL_LOCAL T dotProductSelectAlignment(const T * af, const T * bf, size_t sz);

template <typename T, size_t VLEN, size_t VectorsPerChunk>
T dotProductSelectAlignment(const T * af, const T * bf, size_t sz)
{
    if (validAlignment(af, VLEN)) {
        if (validAlignment(bf, VLEN)) {
            return computeDotProduct<T, VLEN, VLEN, VLEN, VectorsPerChunk>(af, bf, sz);
        } else {
            return computeDotProduct<T, VLEN, VLEN, 1, VectorsPerChunk>(af, bf, sz);
        }
    } else {
        if (validAlignment(bf, VLEN)) {
            return computeDotProduct<T, VLEN, 1, VLEN, VectorsPerChunk>(af, bf, sz);
        } else {
            return computeDotProduct<T, VLEN, 1, 1, VectorsPerChunk>(af, bf, sz);
        }
    }
}

template <typename T, unsigned VLEN, unsigned AlignA, unsigned AlignB>
double
euclideanDistanceT(const T * af, const T * bf, size_t sz)
{
    constexpr unsigned VectorsPerChunk = 4;
    constexpr unsigned ChunkSize = VLEN*VectorsPerChunk/sizeof(T);
    typedef T V __attribute__ ((vector_size (VLEN)));
    typedef T A __attribute__ ((vector_size (VLEN), aligned(AlignA)));
    typedef T B __attribute__ ((vector_size (VLEN), aligned(AlignB)));
    V partial[VectorsPerChunk];
    memset(partial, 0, sizeof(partial));
    const A * a = reinterpret_cast<const A *>(af);
    const B * b = reinterpret_cast<const B *>(bf);

    const size_t numChunks(sz/ChunkSize);
    for (size_t i(0); i < numChunks; i++) {
        for (size_t j(0); j < VectorsPerChunk; j++) {
            partial[j] += (a[VectorsPerChunk*i+j] - b[VectorsPerChunk*i+j]) * (a[VectorsPerChunk*i+j] - b[VectorsPerChunk*i+j]);
        }
    }
    double sum(0);
    for (size_t i(numChunks*ChunkSize); i < sz; i++) {
        sum += (af[i] - bf[i]) * (af[i] - bf[i]);
    }
    partial[0] = sumR<V, VectorsPerChunk>(partial);

    return sum + sumT<T, V>(partial[0]);
}

template <typename T, unsigned VLEN>
double euclideanDistanceSelectAlignment(const T * af, const T * bf, size_t sz)
{
    constexpr unsigned ALIGN = 32;
    if (validAlignment(af, ALIGN)) {
        if (validAlignment(bf, ALIGN)) {
            return euclideanDistanceT<T, VLEN, ALIGN, ALIGN>(af, bf, sz);
        } else {
            return euclideanDistanceT<T, VLEN, ALIGN, 1>(af, bf, sz);
        }
    } else {
        if (validAlignment(bf, ALIGN)) {
            return euclideanDistanceT<T, VLEN, 1, ALIGN>(af, bf, sz);
        } else {
            return euclideanDistanceT<T, VLEN, 1, 1>(af, bf, sz);
        }
    }
}

}
