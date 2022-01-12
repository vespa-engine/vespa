// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace searchcorespi::index {

/*
 * Class describing active state for a disk index directory.
 */
class IndexDiskDirActiveState {
    uint32_t _active_count;
public:
    IndexDiskDirActiveState()
        : _active_count(0)
    {
    }

    void activate() noexcept { ++_active_count; }
    void deactivate() noexcept;
    bool is_active() const noexcept { return _active_count != 0; }
};

}
