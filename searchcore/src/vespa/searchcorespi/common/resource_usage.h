// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "transient_resource_usage.h"

namespace searchcorespi::common {

/**
 * Class containing resource usage (currently only transient resource usage).
 */
class ResourceUsage {
    TransientResourceUsage _transient;
    uint64_t               _disk;
public:
    ResourceUsage() noexcept
        : _transient(),
          _disk(0)
    {}
    explicit ResourceUsage(const TransientResourceUsage& transient_in, uint64_t disk_in) noexcept
        : _transient(transient_in),
          _disk(disk_in)
    {}

    const TransientResourceUsage& transient() const noexcept { return _transient; }
    uint64_t transient_disk() const noexcept { return _transient.disk(); }
    size_t transient_memory() const noexcept { return _transient.memory(); }
    uint64_t disk() const noexcept { return _disk; }
    void merge(const ResourceUsage& rhs) noexcept {
        _transient.merge(rhs._transient);
        _disk += rhs.disk();
    }
};

}
