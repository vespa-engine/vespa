// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "x64_generic.h"

namespace vespalib::hwaccelerated {

/**
 * Avx-512 implementation.
 */
class Avx2Accelerator : public X64GenericAccelerator {
public:
    ~Avx2Accelerator() override = default;

    TargetInfo               target_info() const noexcept override;
    const dispatch::FnTable& fn_table() const override;
};

} // namespace vespalib::hwaccelerated
