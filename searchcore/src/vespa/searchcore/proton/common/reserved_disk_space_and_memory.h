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
    size_t   _reserved_memory;

public:
    ReservedDiskSpaceAndMemory(uint64_t reserved_disk_space_in, size_t reserved_memory_in) noexcept
        : _reserved_disk_space(reserved_disk_space_in), _reserved_memory(reserved_memory_in) {}
    [[nodiscard]] uint64_t reserved_disk_space() const noexcept { return _reserved_disk_space; }
    [[nodiscard]] size_t reserved_memory() const noexcept { return _reserved_memory; }
};

} // namespace proton
