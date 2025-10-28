// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "x64_generic.h"

namespace vespalib::hwaccelerated {

/**
 * Avx-512 implementation.
 */
class Avx2Accelerator : public X64GenericAccelerator
{
public:
    size_t populationCount(const uint64_t *a, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const int8_t * a, const int8_t * b, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const float * a, const float * b, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const double * a, const double * b, size_t sz) const noexcept override;
    void convert_bfloat16_to_float(const uint16_t * src, float * dest, size_t sz) const noexcept override;
    int64_t dotProduct(const int8_t * a, const int8_t * b, size_t sz) const noexcept override;
    void and128(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const noexcept override;
    void or128(size_t offset, const std::vector<std::pair<const void *, bool>> &src, void *dest) const noexcept override;
    TargetInfo target_info() const noexcept override;
    const dispatch::FnTable& fn_table() const override;
};

}
