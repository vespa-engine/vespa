// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>

namespace proton {

/**
 * Interface class providing a snapshot of reserved disk space.
 */
class IReservedDiskSpaceProvider {
public:
    virtual ~IReservedDiskSpaceProvider() = default;
    virtual uint64_t get_reserved_disk_space() const = 0;
};

}
