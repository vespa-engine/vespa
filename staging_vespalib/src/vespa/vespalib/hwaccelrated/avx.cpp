// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#include <vespa/vespalib/hwaccelrated/avx.h>

namespace vespalib {

namespace hwaccelrated {

namespace {

bool validAlignment32(const void * p) {
    return (reinterpret_cast<uint64_t>(p) & 0x1ful) == 0;
}

}

template <unsigned AlignA, unsigned AlignB>
float
AvxAccelrator::computeDotProduct(const float * af, const float * bf, size_t sz)
{
    typedef float v8saf __attribute__ ((vector_size (32)));
    const size_t ChunkSize(32);
    const size_t VectorsPerChunk(ChunkSize/8);
    v8saf partial[VectorsPerChunk] = { {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
                                      {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0} };
    typedef float A __attribute__ ((vector_size (32), aligned(AlignA)));
    typedef float B __attribute__ ((vector_size (32), aligned(AlignB)));
    const A * a = reinterpret_cast<const A *>(af);
    const B * b = reinterpret_cast<const B *>(bf);

    const size_t numChunks(sz/ChunkSize);
    for (size_t i(0); i < numChunks; i++) {
        for (size_t j(0); j < VectorsPerChunk; j++) {
            partial[j] += a[VectorsPerChunk*i+j] * b[VectorsPerChunk*i+j];
        }
    }
    float sum(0);
    for (size_t i(numChunks*ChunkSize); i < sz; i++) {
        sum += af[i] * bf[i];
    }
    for (size_t i(1); i < VectorsPerChunk; i++) {
        partial[0] += partial[i];
    }
    sum += partial[0][0] + partial[0][1] + partial[0][2] + partial[0][3] +
           partial[0][4] + partial[0][5] + partial[0][6] + partial[0][7];
    return sum; 
}

template <unsigned AlignA, unsigned AlignB>
double
AvxAccelrator::computeDotProduct(const double * af, const double * bf, size_t sz)
{
    typedef double v4sd __attribute__ ((vector_size (32)));
    const size_t ChunkSize(16);
    const size_t VectorsPerChunk(ChunkSize/4);
    v4sd partial[VectorsPerChunk] = { {0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0} };
    typedef double A __attribute__ ((vector_size (32), aligned(AlignA)));
    typedef double B __attribute__ ((vector_size (32), aligned(AlignB)));
    const A * a = reinterpret_cast<const A *>(af);
    const B * b = reinterpret_cast<const B *>(bf);

    const size_t numChunks(sz/ChunkSize);
    for (size_t i(0); i < numChunks; i++) {
        for (size_t j(0); j < VectorsPerChunk; j++) {
            partial[j] += a[VectorsPerChunk*i+j] * b[VectorsPerChunk*i+j];
        }
    }
    double sum(0);
    for (size_t i(numChunks*ChunkSize); i < sz; i++) {
        sum += af[i] * bf[i];
    }
    for (size_t i(1); i < VectorsPerChunk; i++) {
        partial[0] += partial[i];
    }
    sum += partial[0][0] + partial[0][1] + partial[0][2] + partial[0][3];
    return sum; 
}

template <typename T>
T
AvxAccelrator::dotProductSelectAlignment(const T * af, const T * bf, size_t sz)
{
    if (validAlignment32(af)) {
        if (validAlignment32(bf)) {
            return computeDotProduct<32, 32>(af, bf, sz);
        } else {
            return computeDotProduct<32, 1>(af, bf, sz);
        }
    } else {
        if (validAlignment32(bf)) {
            return computeDotProduct<1, 32>(af, bf, sz);
        } else {
            return computeDotProduct<1, 1>(af, bf, sz);
        }
    }
}

float
AvxAccelrator::dotProduct(const float * af, const float * bf, size_t sz) const
{
    return dotProductSelectAlignment(af, bf, sz);
}

double
AvxAccelrator::dotProduct(const double * af, const double * bf, size_t sz) const
{
    return dotProductSelectAlignment(af, bf, sz);
}

}
}
