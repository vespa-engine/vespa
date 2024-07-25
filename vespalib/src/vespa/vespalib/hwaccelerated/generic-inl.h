// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#ifndef VESPA_HWACCEL_TARGET_TYPE
#error "VESPA_HWACCEL_TARGET_TYPE not set"
#endif

#include "iaccelerated.h"

namespace vespalib::hwaccelerated {

/**
 * A generic implementation of IAccelerated (in the sense that it has no CPU-specific
 * tweaks or tricks up its sleeves) that can be compiled for distinct architecture targets
 * to get a baseline auto-vectorized set of kernels for those targets.
 */
class VESPA_HWACCEL_TARGET_TYPE : public IAccelerated
{
public:
    float dotProduct(const float* a, const float* b, size_t sz) const noexcept override;
    float dotProduct(const BFloat16* a, const BFloat16* b, size_t sz) const noexcept override;
    double dotProduct(const double* a, const double* b, size_t sz) const noexcept override;
    int64_t dotProduct(const int8_t* a, const int8_t* b, size_t sz) const noexcept override;
    int64_t dotProduct(const int16_t* a, const int16_t* b, size_t sz) const noexcept override;
    int64_t dotProduct(const int32_t* a, const int32_t* b, size_t sz) const noexcept override;
    long long dotProduct(const int64_t* a, const int64_t* b, size_t sz) const noexcept override;
    void orBit(void* a, const void* b, size_t bytes) const noexcept override;
    void andBit(void* a, const void* b, size_t bytes) const noexcept override;
    void andNotBit(void* a, const void* b, size_t bytes) const noexcept override;
    void notBit(void* a, size_t bytes) const noexcept override;
    size_t populationCount(const uint64_t* a, size_t sz) const noexcept override;
    void convert_bfloat16_to_float(const uint16_t* src, float* dest, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const int8_t* a, const int8_t* b, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const float* a, const float* b, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const double* a, const double* b, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const BFloat16* a, const BFloat16* b, size_t sz) const noexcept override;
    void and128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) const noexcept override;
    void or128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) const noexcept override;
#ifdef VESPA_HWACCEL_TARGET_NAME
    const char* target_name() const noexcept override { return VESPA_HWACCEL_TARGET_NAME; }
#endif
};

} // vespalib::hwaccelerated

// .cpp files should set this additional macro to also generate the target class _definitions_
#ifdef VESPA_HWACCEL_INCLUDE_DEFINITIONS
#include "generic-inl.hpp"
#endif
