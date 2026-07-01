// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "reserved_disk_space_and_memory.h"

namespace proton {

/**
 * Interface class providing a snapshot of reserved disk space and memory.
 */
class IReservedDiskSpaceAndMemoryProvider {
public:
    virtual ~IReservedDiskSpaceAndMemoryProvider() = default;
    virtual ReservedDiskSpaceAndMemory get_reserved_disk_space_and_memory() const = 0;
};

} // namespace proton
