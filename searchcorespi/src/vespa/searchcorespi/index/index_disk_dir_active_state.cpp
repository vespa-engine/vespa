// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "index_disk_dir_active_state.h"
#include <cassert>

namespace searchcorespi::index {

void
IndexDiskDirActiveState::deactivate() noexcept
{
    assert(_active_count > 0u);
    --_active_count;
}

}
