// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::datastore {

/*
 * Class describing how to compact a compactable data structure.
 *
 * memory        - to reduce amount of "dead" memory
 * address_space - to avoid running out of free buffers in data store
 *                 (i.e. move data from small buffers to larger buffers)
 */
class CompactionSpec
{
    bool _compact_memory;
    bool _compact_address_space;
public:
    CompactionSpec()
        : _compact_memory(false),
          _compact_address_space(false)
    {
    }
    CompactionSpec(bool compact_memory_, bool compact_address_space_) noexcept
        : _compact_memory(compact_memory_),
          _compact_address_space(compact_address_space_)
    {
    }
    bool compact() const noexcept { return _compact_memory || _compact_address_space; }
    bool compact_memory() const noexcept { return _compact_memory; }
    bool compact_address_space() const noexcept { return _compact_address_space; }
};

}
