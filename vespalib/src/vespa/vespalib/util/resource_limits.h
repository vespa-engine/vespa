// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace vespalib {

/*
 * Class for getting resource limits.
 */
class ResourceLimits {
    uint64_t _memory;
    uint32_t _cpu;

    ResourceLimits(uint64_t memory, uint32_t cpu);
public:
    static ResourceLimits create();
    uint64_t memory() const noexcept { return _memory; }
    uint32_t cpu() const noexcept { return _cpu; }
};

}
