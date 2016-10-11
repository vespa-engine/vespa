// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/hwaccelrated/avx2.h>

namespace vespalib {

namespace hwaccelrated {

namespace {

bool validAlignment32(const void * p) {
    return (reinterpret_cast<uint64_t>(p) & 0x1ful) == 0;
}

template <typename T>
class TypeSpecifics { };

template <>
struct TypeSpecifics<float> {
    static constexpr const size_t V_SZ = 32;
    typedef float V __attribute__ ((vector_size (V_SZ)));
    static constexpr const size_t VectorsPerChunk = 4;
    static constexpr const V zero = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
    static float sum(V v) {
        return v[0] + v[1] + v[2] + v[3] + v[4] + v[5] + v[6] + v[7];
    }
};

template <>
struct TypeSpecifics<double> {
    static constexpr const size_t V_SZ = 32;
    typedef double V __attribute__ ((vector_size (V_SZ)));
    static constexpr const size_t VectorsPerChunk = 4;
    static constexpr const V zero = {0.0, 0.0, 0.0, 0.0};
    static float sum(V v) {
        return v[0] + v[1] + v[2] + v[3];
    }
};

}

template <typename T, unsigned AlignA, unsigned AlignB>
T
Avx2Accelrator::computeDotProduct(const T * af, const T * bf, size_t sz)
{
    using TT = TypeSpecifics<T>;
    constexpr const size_t ChunkSize = TT::V_SZ*4/sizeof(T);
    constexpr const size_t VectorsPerChunk = TT::VectorsPerChunk;
    typename TT::V partial[VectorsPerChunk] = { TT::zero, TT::zero, TT::zero, TT::zero};
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

template <typename T>
T
Avx2Accelrator::dotProductSelectAlignment(const T * af, const T * bf, size_t sz)
{
    if (validAlignment32(af)) {
        if (validAlignment32(bf)) {
            return computeDotProduct<T, 32, 32>(af, bf, sz);
        } else {
            return computeDotProduct<T, 32, 1>(af, bf, sz);
        }
    } else {
        if (validAlignment32(bf)) {
            return computeDotProduct<T, 1, 32>(af, bf, sz);
        } else {
            return computeDotProduct<T, 1, 1>(af, bf, sz);
        }
    }
}

float
Avx2Accelrator::dotProduct(const float * af, const float * bf, size_t sz) const
{
    return dotProductSelectAlignment(af, bf, sz);
}

double
Avx2Accelrator::dotProduct(const double * af, const double * bf, size_t sz) const
{
    return dotProductSelectAlignment(af, bf, sz);
}

}
}
