// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iaccelerated.h"
#include <memory>

namespace vespalib::hwaccelerated {

class Highway {
public:
    // Returns all accelerator targets that are supported by the current architecture and runtime.
    // The targets are ordered in decreasing order of preference, i.e. element 0 is considered
    // the most preferred target to use for the lifetime of this process from the perspective of
    // the Highway library.
    // May return zero elements iff Vespa has been custom compiled for an architecture level that
    // is below our expected baseline (AVX2 on x64; AArch64 should always have NEON). Otherwise,
    // always returns at least 1 element.
    [[nodiscard]] static std::vector<std::unique_ptr<IAccelerated>> create_supported_targets();
};

}
