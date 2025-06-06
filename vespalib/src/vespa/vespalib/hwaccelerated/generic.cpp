// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "generic.h"
#include "private_helpers.hpp"
#include <cblas.h>

namespace vespalib::hwaccelerated {

namespace {

template <typename ACCUM, typename T, size_t UNROLL>
ACCUM
multiplyAdd(const T * a, const T * b, size_t sz) noexcept
{
    ACCUM partial[UNROLL];
    for (size_t i(0); i < UNROLL; i++) {
        partial[i] = 0;
    }
    size_t i(0);
    for (; i + UNROLL <= sz; i+= UNROLL) {
        for (size_t j(0); j < UNROLL; j++) {
            partial[j] += a[i+j] * b[i+j];
        }
    }
    for (;i < sz; i++) {
        partial[i%UNROLL] += a[i] * b[i];
    }
    ACCUM sum(0);
    for (size_t j(0); j < UNROLL; j++) {
        sum += partial[j];
    }
    return sum;
}

template <typename T, size_t UNROLL>
double
squaredEuclideanDistanceT(const T * a, const T * b, size_t sz) noexcept
{
    T partial[UNROLL];
    for (size_t i(0); i < UNROLL; i++) {
        partial[i] = 0;
    }
    size_t i(0);
    for (; i + UNROLL <= sz; i += UNROLL) {
        for (size_t j(0); j < UNROLL; j++) {
            T d = a[i+j] - b[i+j];
            partial[j] += d * d;
        }
    }
    for (;i < sz; i++) {
        T d = a[i] - b[i];
        partial[i%UNROLL] += d * d;
    }
    double sum(0);
    for (size_t j(0); j < UNROLL; j++) {
        sum += partial[j];
    }
    return sum;
}

template<size_t UNROLL, typename Operation>
void
bitOperation(Operation operation, void * aOrg, const void * bOrg, size_t bytes) noexcept {

    const size_t sz(bytes/sizeof(uint64_t));
    {
        auto a(static_cast<uint64_t *>(aOrg));
        auto b(static_cast<const uint64_t *>(bOrg));
        size_t i(0);
        for (; i + UNROLL <= sz; i += UNROLL) {
            for (size_t j(0); j < UNROLL; j++) {
                a[i + j] = operation(a[i + j], b[i + j]);
            }
        }
        for (; i < sz; i++) {
            a[i] = operation(a[i], b[i]);
        }
    }

    auto a(static_cast<uint8_t *>(aOrg));
    auto b(static_cast<const uint8_t *>(bOrg));
    for (size_t i(sz*sizeof(uint64_t)); i < bytes; i++) {
        a[i] = operation(a[i], b[i]);
    }
}

}

float
GenericAccelerator::dotProduct(const float * a, const float * b, size_t sz) const noexcept
{
    return cblas_sdot(sz, a, 1, b, 1);
}

double
GenericAccelerator::dotProduct(const double * a, const double * b, size_t sz) const noexcept
{
    return cblas_ddot(sz, a, 1, b, 1);
}

int64_t
GenericAccelerator::dotProduct(const int8_t * a, const int8_t * b, size_t sz) const noexcept
{
    return helper::multiplyAdd(a, b, sz);
}

int64_t
GenericAccelerator::dotProduct(const int16_t * a, const int16_t * b, size_t sz) const noexcept
{
    return multiplyAdd<int64_t, int16_t, 8>(a, b, sz);
}
int64_t
GenericAccelerator::dotProduct(const int32_t * a, const int32_t * b, size_t sz) const noexcept
{
    return multiplyAdd<int64_t, int32_t, 8>(a, b, sz);
}

long long
GenericAccelerator::dotProduct(const int64_t * a, const int64_t * b, size_t sz) const noexcept
{
    return multiplyAdd<long long, int64_t, 8>(a, b, sz);
}

void
GenericAccelerator::orBit(void * aOrg, const void * bOrg, size_t bytes) const noexcept
{
    bitOperation<8>([](uint64_t a, uint64_t b) { return a | b; }, aOrg, bOrg, bytes);
}

void
GenericAccelerator::andBit(void * aOrg, const void * bOrg, size_t bytes) const noexcept
{
    bitOperation<8>([](uint64_t a, uint64_t b) { return a & b; }, aOrg, bOrg, bytes);
}
void
GenericAccelerator::andNotBit(void * aOrg, const void * bOrg, size_t bytes) const noexcept
{
    bitOperation<8>([](uint64_t a, uint64_t b) { return a & ~b; }, aOrg, bOrg, bytes);
}

void
GenericAccelerator::notBit(void * aOrg, size_t bytes) const noexcept
{
    auto a(static_cast<uint64_t *>(aOrg));
    const size_t sz(bytes/sizeof(uint64_t));
    for (size_t i(0); i < sz; i++) {
        a[i] = ~a[i];
    }
    auto ac(static_cast<uint8_t *>(aOrg));
    for (size_t i(sz*sizeof(uint64_t)); i < bytes; i++) {
        ac[i] = ~ac[i];
    }
}

void
GenericAccelerator::convert_bfloat16_to_float(const uint16_t * src, float * dest, size_t sz) const noexcept {
    helper::convert_bfloat16_to_float(src, dest, sz);
}

size_t
GenericAccelerator::populationCount(const uint64_t *a, size_t sz) const noexcept {
    return helper::populationCount(a, sz);
}

double
GenericAccelerator::squaredEuclideanDistance(const int8_t * a, const int8_t * b, size_t sz) const noexcept {
    return helper::squaredEuclideanDistance(a, b, sz);
}

double
GenericAccelerator::squaredEuclideanDistance(const float * a, const float * b, size_t sz) const noexcept {
    return squaredEuclideanDistanceT<float, 16>(a, b, sz);
}

double
GenericAccelerator::squaredEuclideanDistance(const double * a, const double * b, size_t sz) const noexcept {
    return squaredEuclideanDistanceT<double, 16>(a, b, sz);
}

void
GenericAccelerator::and128(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const noexcept {
    helper::andChunks<16, 8>(offset, src, dest);
}

void
GenericAccelerator::or128(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const noexcept {
    helper::orChunks<16, 8>(offset, src, dest);
}

}
