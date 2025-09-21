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
    // the Highway library. Note that this will also include targets that `create_best_target()`
    // may exclude due to our own filtering decisions, so it's useful for tests and benchmarks.
    // Always returns at least 1 element.
    [[nodiscard]] static std::vector<std::unique_ptr<IAccelerated>> create_supported_targets();
    // Returns the accelerator instance corresponding to the "best" Highway target that is
    // supported by the current architecture and runtime. This target may be chosen from a subset
    // of what `create_supported_targets()` returns, as we may apply platform-specific target
    // exclusions.
    [[nodiscard]] static std::unique_ptr<IAccelerated> create_best_target();
};

}
