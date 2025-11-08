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

    TargetInfo target_info() const noexcept override;
    const dispatch::FnTable& fn_table() const override;
};

}
