// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace proton {

/**
 * Disk usage sample.
 */
class DiskUsage {
    uint64_t _used_bytes;
    uint64_t _capacity_bytes;

public:
    DiskUsage(uint64_t used_bytes, uint64_t capacity_bytes) noexcept
        : _used_bytes(used_bytes), _capacity_bytes(capacity_bytes) {}
    [[nodiscard]] uint64_t used_bytes() const noexcept { return _used_bytes; }
    [[nodiscard]] uint64_t capacity_bytes() const noexcept { return _capacity_bytes; }
};

} // namespace proton
