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
