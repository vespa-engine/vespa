// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "index_disk_dir_state.h"
#include <cassert>

namespace searchcorespi::index {

bool
IndexDiskDirState::activate(uint64_t size_on_disk) noexcept
{
    ++_active_count;
    if (!_size_on_disk.has_value()) {
        _size_on_disk.emplace(size_on_disk);
        return true;
    }
    return false;
}

bool
IndexDiskDirState::deactivate() noexcept
{
    assert(_active_count > 0u);
    --_active_count;
    return _active_count == 0u;
}

}
