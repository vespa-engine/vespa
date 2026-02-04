// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "transient_resource_usage.h"

namespace proton {

/**
 * Class containing resource usage (currently only transient resource usage).
 */
class ResourceUsage {
    TransientResourceUsage _transient;
public:
    ResourceUsage() noexcept
        : _transient()
    {}
    explicit ResourceUsage(const TransientResourceUsage& transient_in) noexcept
        : _transient(transient_in)
    {}

    size_t transient_disk() const noexcept { return _transient.disk(); }
    size_t transient_memory() const noexcept { return _transient.memory(); }
    void merge(const ResourceUsage& rhs) noexcept {
        _transient.merge(rhs._transient);
    }
};

}
