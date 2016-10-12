// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/fastos/dynamiclibrary.h>

namespace vespalib {

namespace hwaccelrated {

namespace avx {

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

template <typename T, size_t VLEN>
class TypeSpecifics { };

template <>
struct TypeSpecifics<float, 32u> {
    static constexpr const size_t V_SZ = 32u;
    typedef float V __attribute__ ((vector_size (V_SZ)));
    static float sum(const V & v) { return sumT<float, V>(v); }
};

template <>
struct TypeSpecifics<double, 32u> {
    static constexpr const size_t V_SZ = 32u;
    typedef double V __attribute__ ((vector_size (V_SZ)));
    static double sum(const V & v) { return sumT<double, V>(v); }
};

template <>
struct TypeSpecifics<float, 64u> {
    static constexpr const size_t V_SZ = 64u;
    typedef float V __attribute__ ((vector_size (V_SZ)));
    static float sum(const V & v) { return sumT<float, V>(v); }
};

template <>
struct TypeSpecifics<double, 64u> {
    static constexpr const size_t V_SZ = 64u;
    typedef double V __attribute__ ((vector_size (V_SZ)));
    static double sum(const V & v) { return sumT<double, V>(v); }
};

template <typename T, size_t VLEN, unsigned AlignA, unsigned AlignB, size_t VectorsPerChunk>
static T computeDotProduct(const T * af, const T * bf, size_t sz) __attribute__((noinline));

template <typename T, size_t VLEN, unsigned AlignA, unsigned AlignB, size_t VectorsPerChunk>
T computeDotProduct(const T * af, const T * bf, size_t sz)
{
    using TT = TypeSpecifics<T, VLEN>;
    constexpr const size_t ChunkSize = TT::V_SZ*VectorsPerChunk/sizeof(T);
    typename TT::V partial[VectorsPerChunk];
    memset(partial, 0, sizeof(partial));
    typedef T A __attribute__ ((vector_size (TT::V_SZ), aligned(AlignA)));
    typedef T B __attribute__ ((vector_size (TT::V_SZ), aligned(AlignB)));
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
    for (size_t i(1); i < VectorsPerChunk; i++) {
        partial[0] += partial[i];
    }
    return sum + TT::sum(partial[0]);
}

}

template <typename T, size_t VLEN, size_t VectorsPerChunk=4>
VESPA_DLL_LOCAL static T dotProductSelectAlignment(const T * af, const T * bf, size_t sz);

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

}
}
}
