// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>

namespace vespalib::datastore {

/*
 * Class representing candidate buffer for compaction.
 */
class CompactBufferCandidate {
    uint32_t _buffer_id;
    size_t   _used;
    size_t   _dead;
public:
    CompactBufferCandidate(uint32_t buffer_id, size_t used, size_t dead) noexcept
        : _buffer_id(buffer_id),
          _used(used),
          _dead(dead)
    {
    }

    CompactBufferCandidate() noexcept
        : CompactBufferCandidate(0, 0, 0)
    {
    }

    bool operator<(const CompactBufferCandidate& rhs) const noexcept { return _dead > rhs._dead; }
    uint32_t get_buffer_id() const noexcept { return _buffer_id; }
    size_t get_used() const noexcept { return _used; }
    size_t get_dead() const noexcept { return _dead; }
};

}
