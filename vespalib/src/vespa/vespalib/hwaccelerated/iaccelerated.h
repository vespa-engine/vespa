// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "target_info.h"
#include <vespa/vespalib/util/bfloat16.h>
#include <memory>
#include <cstdint>
#include <vector>

namespace vespalib::hwaccelerated {

namespace dispatch { struct FnTable; }

class TargetInfo;

/**
 * This contains an interface to all primitives that has different cpu supported accelerations.
 * The actual implementation you get by calling the static getAccelerator method.
 */
class IAccelerated {
public:
    virtual ~IAccelerated() = default;
    using UP = std::unique_ptr<IAccelerated>;
    virtual float dotProduct(const float* a, const float* b, size_t sz) const noexcept = 0;
    virtual float dotProduct(const BFloat16* a, const BFloat16* b, size_t sz) const noexcept = 0;
    virtual double dotProduct(const double* a, const double* b, size_t sz) const noexcept = 0;
    virtual int64_t dotProduct(const int8_t* a, const int8_t* b, size_t sz) const noexcept = 0;
    virtual int64_t dotProduct(const int16_t* a, const int16_t* b, size_t sz) const noexcept = 0;
    virtual int64_t dotProduct(const int32_t* a, const int32_t* b, size_t sz) const noexcept = 0;
    virtual long long dotProduct(const int64_t* a, const int64_t* b, size_t sz) const noexcept = 0;
    virtual void orBit(void* a, const void* b, size_t bytes) const noexcept = 0;
    virtual void andBit(void* a, const void* b, size_t bytes) const noexcept = 0;
    virtual void andNotBit(void* a, const void* b, size_t bytes) const noexcept = 0;
    virtual void notBit(void* a, size_t bytes) const noexcept = 0;
    virtual size_t populationCount(const uint64_t* a, size_t sz) const noexcept = 0;
    virtual size_t binary_hamming_distance(const void* lhs, const void* rhs, size_t sz) const noexcept = 0;
    virtual void convert_bfloat16_to_float(const uint16_t* src, float* dest, size_t sz) const noexcept = 0;
    virtual double squaredEuclideanDistance(const int8_t* a, const int8_t* b, size_t sz) const noexcept = 0;
    virtual double squaredEuclideanDistance(const float* a, const float* b, size_t sz) const noexcept = 0;
    virtual double squaredEuclideanDistance(const double* a, const double* b, size_t sz) const noexcept = 0;
    virtual double squaredEuclideanDistance(const BFloat16* a, const BFloat16* b, size_t sz) const noexcept = 0;
    // AND 128 bytes from multiple, optionally inverted sources
    virtual void and128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) const noexcept = 0;
    // OR 128 bytes from multiple, optionally inverted sources
    virtual void or128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) const noexcept = 0;

    [[nodiscard]] virtual TargetInfo target_info() const noexcept = 0;

    // The function table entries must be valid for the lifetime of the process,
    // entirely independent of the lifetime of `this`.
    [[nodiscard]] virtual const dispatch::FnTable& fn_table() const = 0;

    // Returns all auto-vectorized accelerator targets that are supported by the current
    // architecture and runtime. The targets are ordered in decreasing order of preference,
    // i.e. element 0 is considered the most preferred target to use.
    // Always returns at least 1 element.
    static std::vector<std::unique_ptr<IAccelerated>> create_supported_auto_vectorized_targets();
};

}
