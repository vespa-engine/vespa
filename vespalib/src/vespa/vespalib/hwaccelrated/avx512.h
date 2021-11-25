// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "avx2.h"

namespace vespalib::hwaccelrated {

/**
 * Avx-512 implementation.
 */
class Avx512Accelrator : public Avx2Accelrator
{
public:
    float dotProduct(const float * a, const float * b, size_t sz) const override;
    double dotProduct(const double * a, const double * b, size_t sz) const override;
    size_t populationCount(const uint64_t *a, size_t sz) const override;
    double squaredEuclideanDistance(const int8_t * a, const int8_t * b, size_t sz) const override;
    double squaredEuclideanDistance(const float * a, const float * b, size_t sz) const override;
    double squaredEuclideanDistance(const double * a, const double * b, size_t sz) const override;
    void and64(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const override;
    void or64(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const override;
};

}
