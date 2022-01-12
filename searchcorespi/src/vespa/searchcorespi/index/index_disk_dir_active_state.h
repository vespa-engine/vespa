// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

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
    void deactivate() noexcept { --_active_count; }
    bool is_active() const noexcept { return _active_count != 0; }
};

}
