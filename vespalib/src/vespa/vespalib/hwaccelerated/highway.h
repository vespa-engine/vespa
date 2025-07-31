// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iaccelerated.h"
#include <span>

namespace vespalib::hwaccelerated {

class Highway {
public:
    // Returns all accelerator targets that are supported by the current architecture and runtime.
    // The targets are ordered in decreasing order of preference, i.e. element 0 is considered
    // the most preferred target to use for the lifetime of this process.
    // Always returns at least 1 element.
    [[nodiscard]] static std::span<const IAccelerated*> supported_targets();
    // Returns the accelerator instance corresponding to the "best" Highway target that is
    // supported by the current architecture and runtime. This is always equal to the first
    // element returned by `supported_targets()`.
    [[nodiscard]] static const IAccelerated& best_target() noexcept;
};

}
