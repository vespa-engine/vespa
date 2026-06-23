// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <cstdlib>

namespace proton {

/**
 * Reserved disk space and memory.
 */
class ReservedDiskSpaceAndMemory {
    uint64_t _reserved_disk_space;
    size_t   _reserved_memory_for_flush;
    size_t   _reserved_memory_for_memory_indexes;

public:
    ReservedDiskSpaceAndMemory(uint64_t reserved_disk_space_in, size_t reserved_memory_for_flush_in,
                               size_t reserved_memory_for_memory_indexes_in) noexcept
        : _reserved_disk_space(reserved_disk_space_in),
          _reserved_memory_for_flush(reserved_memory_for_flush_in),
          _reserved_memory_for_memory_indexes(reserved_memory_for_memory_indexes_in) {}
    [[nodiscard]] uint64_t reserved_disk_space() const noexcept { return _reserved_disk_space; }
    [[nodiscard]] size_t reserved_memory_for_flush() const noexcept { return _reserved_memory_for_flush; }
    [[nodiscard]] size_t reserved_memory_for_memory_indexes() const noexcept {
        return _reserved_memory_for_memory_indexes;
    }
    [[nodiscard]] size_t reserved_memory() const noexcept {
        return _reserved_memory_for_flush + _reserved_memory_for_memory_indexes;
    }
};

} // namespace proton
