// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

namespace proton {

/**
 * Class containing transient disk and memory usage (in bytes).
 */
class TransientResourceUsage {
private:
    size_t _disk;
    size_t _memory;

public:
    TransientResourceUsage() noexcept
        : _disk(0),
          _memory(0)
    {}
    TransientResourceUsage(size_t disk_in,
                           size_t memory_in) noexcept
        : _disk(disk_in),
          _memory(memory_in)
    {}
    size_t disk() const noexcept { return _disk; }
    size_t memory() const noexcept { return _memory; }
    void merge(const TransientResourceUsage& rhs) {
        _disk += rhs.disk();
        _memory += rhs.memory();
    }
};

/**
 * Interface class providing a snapshot of transient resource usage.
 *
 * E.g. the memory used by the memory index and extra disk needed for running disk index fusion.
 * This provides the total transient resource usage for the components this provider encapsulates.
 */
class ITransientResourceUsageProvider {
public:
    virtual ~ITransientResourceUsageProvider() = default;
    virtual TransientResourceUsage get_transient_resource_usage() const = 0;
};

}
