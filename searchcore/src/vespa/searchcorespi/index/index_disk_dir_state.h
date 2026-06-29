// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <chrono>
#include <cstdint>
#include <optional>

namespace searchcorespi::index {

/*
 * Class describing state for a disk index directory.
 */
class IndexDiskDirState {
    uint32_t                                           _active_count;
    std::optional<uint64_t>                            _size_on_disk;
    std::optional<std::chrono::steady_clock::duration> _flush_duration;
    bool                                               _stale;

public:
    IndexDiskDirState() : _active_count(0), _size_on_disk(), _flush_duration(), _stale(false) {}

    bool activate(uint64_t size_on_disk, std::chrono::steady_clock::duration flush_duration_) noexcept;
    bool deactivate() noexcept;
    bool is_active() const noexcept { return _active_count != 0; }
    const std::optional<uint64_t>& get_size_on_disk() const noexcept { return _size_on_disk; }
    void set_size_on_disk(uint64_t size_on_disk) noexcept { _size_on_disk = size_on_disk; }
    [[nodiscard]] const std::optional<std::chrono::steady_clock::duration>& flush_duration() const noexcept {
        return _flush_duration;
    }
    void set_flush_duration(std::chrono::steady_clock::duration value) { _flush_duration = value; }
    [[nodiscard]] bool is_stale() const noexcept { return _stale; }
    void set_stale() noexcept { _stale = true; }
};

} // namespace searchcorespi::index
