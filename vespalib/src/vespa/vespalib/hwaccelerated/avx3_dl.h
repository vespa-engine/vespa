// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "avx2.h"

namespace vespalib::hwaccelerated {

/**
 * Accelerator for the superset of AVX-512 that is intended to be equal to the
 * Google Highway AVX3_DL ("deep learning", one presumes) vectorization target.
 * This basically corresponds to Icelake Server, which is what is used as the
 * compilation `-march` target.
 *
 * Using this particular accelerator requires checking for the following CPU
 * features (partially inferred from Highway's `hwy/targets.cc`):
 *
 *   - AVX512F          AVX-512 baseline (Foundation) feature set
 *   - AVX512VL         Vector Length Extensions
 *   - AVX512DQ         Doubleword and Quadword Instructions
 *   - AVX512BW         Byte and Word Instructions
 *   - AVX512CD         Conflict Detection
 *   - AVX512VNNI       Vector Neural Network Instructions
 *   - VPCLMULQDQ       Carry-less Multiplication
 *   - AVX512VBMI       Vector Byte Manipulation Instructions
 *   - AVX512VBMI2      Vector Byte Manipulation Instructions 2 - Byte Manipulation In New York
 *   - VAES             Vector AES; no support for probing via builtins, have to assume it holds...
 *   - AVX512VPOPCNTDQ  Vector popcount
 *   - AVX512BITALG     Bit Algorithms
 *   - GFNI             Galois Field New Instructions
 *
 * ... as well as transitive AVX2/SSE4 feature sets, but we make the simplifying
 * assumption that those already are present if AVX512F is supported.
 */
class Avx3DlAccelerator : public Avx2Accelerator {
public:
    ~Avx3DlAccelerator() override = default;

    float dotProduct(const float* a, const float* b, size_t sz) const noexcept override;
    double dotProduct(const double* a, const double* b, size_t sz) const noexcept override;
    size_t populationCount(const uint64_t* a, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const int8_t* a, const int8_t* b, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const float* a, const float* b, size_t sz) const noexcept override;
    double squaredEuclideanDistance(const double* a, const double* b, size_t sz) const noexcept override;
    size_t binary_hamming_distance(const void* lhs, const void* rhs, size_t sz) const noexcept override;
    void convert_bfloat16_to_float(const uint16_t* src, float* dest, size_t sz) const noexcept override;
    int64_t dotProduct(const int8_t* a, const int8_t* b, size_t sz) const noexcept override;
    void and128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) const noexcept override;
    void or128(size_t offset, const std::vector<std::pair<const void*, bool>>& src, void* dest) const noexcept override;
    const char* target_name() const noexcept override { return "AVX3_DL"; }
};

}
