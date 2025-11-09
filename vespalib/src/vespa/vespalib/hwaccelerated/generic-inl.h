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
class VESPA_HWACCEL_TARGET_TYPE : public IAccelerated {
public:
    ~VESPA_HWACCEL_TARGET_TYPE() override = default;

    TargetInfo target_info() const noexcept override;
    const dispatch::FnTable& fn_table() const override;
};

} // vespalib::hwaccelerated

// .cpp files should set this additional macro to also generate the target class _definitions_
#ifdef VESPA_HWACCEL_INCLUDE_DEFINITIONS
#include "generic-inl.hpp"
#endif
