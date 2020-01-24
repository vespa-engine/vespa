// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sse2.h"
#include "private_helpers.hpp"

namespace vespalib::hwaccelrated {

namespace {

bool validAlignment16(const void * p) {
    return (reinterpret_cast<uint64_t>(p) & 0xful) == 0;
}

bool validAlignment16(const void * a, const void * b) {
    return validAlignment16(a) && validAlignment16(b);
}

}

float
Sse2Accelrator::dotProduct(const float * af, const float * bf, size_t sz) const
{
    if ( ! validAlignment16(af, bf)) {
        return GenericAccelrator::dotProduct(af, bf, sz);
    }
    typedef float v4sf __attribute__ ((vector_size (16)));
    const size_t ChunkSize(16);
    const size_t VectorsPerChunk(ChunkSize/4);
    v4sf partial[VectorsPerChunk] = { {0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0}, {0.0, 0.0, 0.0, 0.0} };
    const v4sf * a = reinterpret_cast<const v4sf *>(af);
    const v4sf * b = reinterpret_cast<const v4sf *>(bf);

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
    sum += partial[0][0] + partial[0][1] + partial[0][2] + partial[0][3];
    return sum; 
}

double
Sse2Accelrator::dotProduct(const double * af, const double * bf, size_t sz) const
{
    if ( ! validAlignment16(af, bf)) {
        return GenericAccelrator::dotProduct(af, bf, sz);
    }
    typedef double v2sd __attribute__ ((vector_size (16)));
    const size_t ChunkSize(8);
    const size_t VectorsPerChunk(ChunkSize/2);
    v2sd partial[VectorsPerChunk] = { {0.0, 0.0}, {0.0, 0.0}, {0.0, 0.0}, {0.0, 0.0} };
    const v2sd * a = reinterpret_cast<const v2sd *>(af);
    const v2sd * b = reinterpret_cast<const v2sd *>(bf);

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
    sum += partial[0][0] + partial[0][1];
    return sum; 
}

size_t
Sse2Accelrator::populationCount(const uint64_t *a, size_t sz) const {
    return helper::populationCount(a, sz);
}

}
