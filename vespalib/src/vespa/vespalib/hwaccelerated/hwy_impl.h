// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "platform_generic.h"

namespace vespalib::hwaccelerated {

class HwyAccelerator final : public PlatformGenericAccelerator {
public:
    ~HwyAccelerator() override = default;

    float dotProduct(const float* a, const float* b, size_t sz) const noexcept override;
    float dotProduct(const BFloat16* a, const BFloat16* b, size_t sz) const noexcept override;
    double dotProduct(const double* a, const double* b, size_t sz) const noexcept override;
    int64_t dotProduct(const int8_t* a, const int8_t* b, size_t sz) const noexcept override;
    size_t populationCount(const uint64_t* a, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const int8_t* a, const int8_t* b, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const float* a, const float* b, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const double* a, const double* b, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const BFloat16* a, const BFloat16* b, size_t sz) const noexcept override;
    const char* target_name() const noexcept override;
};

}
