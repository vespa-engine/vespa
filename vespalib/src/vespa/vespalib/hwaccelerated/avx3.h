// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "avx2.h"

namespace vespalib::hwaccelerated {

/**
 * Accelerator for AVX3, which corresponds to ~Skylake with AVX512{F, VL, DQ, BW, CD}.
 */
class Avx3Accelerator : public Avx2Accelerator
{
public:
    ~Avx3Accelerator() override = default;

    float dotProduct(const float * a, const float * b, size_t sz) const noexcept override;
    double dotProduct(const double * a, const double * b, size_t sz) const noexcept override;
    size_t populationCount(const uint64_t *a, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const int8_t * a, const int8_t * b, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const float * a, const float * b, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const double * a, const double * b, size_t sz) const noexcept override;
    void convert_bfloat16_to_float(const uint16_t * src, float * dest, size_t sz) const noexcept override;
    int64_t dotProduct(const int8_t * a, const int8_t * b, size_t sz) const noexcept override;
    void and128(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const noexcept override;
    void or128(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const noexcept override;
    const char* target_name() const noexcept override { return "AVX3"; }
};

}
