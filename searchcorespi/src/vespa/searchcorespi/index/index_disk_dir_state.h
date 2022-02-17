// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <optional>

namespace searchcorespi::index {

/*
 * Class describing state for a disk index directory.
 */
class IndexDiskDirState {
    uint32_t _active_count;
    std::optional<uint64_t> _size_on_disk;
public:
    IndexDiskDirState()
        : _active_count(0),
          _size_on_disk()
    {
    }

    void activate() noexcept { ++_active_count; }
    void deactivate() noexcept;
    bool is_active() const noexcept { return _active_count != 0; }
    const std::optional<uint64_t>& get_size_on_disk() const noexcept { return _size_on_disk; }
    void set_size_on_disk(uint64_t size_on_disk) noexcept { _size_on_disk = size_on_disk; }
};

}
