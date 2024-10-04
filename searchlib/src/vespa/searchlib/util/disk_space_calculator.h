// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/size_literals.h>
#include <cstdint>

namespace search {

/**
 * Calculator for disk space used on disk for a file with given size
 **/
class DiskSpaceCalculator
{
    static constexpr uint64_t block_size = 4_Ki;
public:
    uint64_t operator()(size_t size) {
        // round up size to file system block size (assumed to be 4 KiB)
        return (size + block_size - 1) & -block_size;
    }
};

} // namespace search
