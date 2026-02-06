// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>

namespace searchcorespi::common {

/**
 * Class containing transient disk and memory usage (in bytes).
 */
class TransientResourceUsage {
private:
    uint64_t _disk;
    size_t   _memory;

public:
    TransientResourceUsage() noexcept
        : _disk(0),
          _memory(0)
    {}
    TransientResourceUsage(uint64_t disk_in, size_t memory_in) noexcept
        : _disk(disk_in),
          _memory(memory_in)
    {}
    uint64_t disk() const noexcept { return _disk; }
    size_t memory() const noexcept { return _memory; }
    void merge(const TransientResourceUsage& rhs) noexcept{
        _disk += rhs.disk();
        _memory += rhs.memory();
    }
};

}
