// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iaccelrated.h"

namespace vespalib::hwaccelrated {

/**
 * Generic cpu agnostic implementation.
 */
class GenericAccelrator : public IAccelrated
{
public:
    float dotProduct(const float * a, const float * b, size_t sz) const override;
    double dotProduct(const double * a, const double * b, size_t sz) const override;
    int64_t dotProduct(const int8_t * a, const int8_t * b, size_t sz) const override;
    int64_t dotProduct(const int16_t * a, const int16_t * b, size_t sz) const override;
    int64_t dotProduct(const int32_t * a, const int32_t * b, size_t sz) const override;
    long long dotProduct(const int64_t * a, const int64_t * b, size_t sz) const override;
    void orBit(void * a, const void * b, size_t bytes) const override;
    void andBit(void * a, const void * b, size_t bytes) const override;
    void andNotBit(void * a, const void * b, size_t bytes) const override;
    void notBit(void * a, size_t bytes) const override;
    size_t populationCount(const uint64_t *a, size_t sz) const override;
    double squaredEuclideanDistance(const int8_t * a, const int8_t * b, size_t sz) const override;
    double squaredEuclideanDistance(const float * a, const float * b, size_t sz) const override;
    double squaredEuclideanDistance(const double * a, const double * b, size_t sz) const override;
    void and64(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const override;
    void or64(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const override;
};

}
