// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "avx2.h"

namespace vespalib::hwaccelerated {

/**
 * Accelerator for AVX3, which corresponds to ~Skylake with AVX512{F, VL, DQ, BW, CD}.
 */
class Avx3Accelerator : public Avx2Accelerator {
public:
    ~Avx3Accelerator() override = default;

    TargetInfo               target_info() const noexcept override;
    const dispatch::FnTable& fn_table() const override;
};

} // namespace vespalib::hwaccelerated
