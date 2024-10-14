// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/memoryusage.h>

namespace search {

/**
 * Statistics for a single field index.
 **/
class FieldIndexStats
{
private:
    vespalib::MemoryUsage _memory_usage;
    size_t _size_on_disk; // in bytes

public:
    FieldIndexStats() noexcept
        : _memory_usage(),
          _size_on_disk(0)
    {}
    FieldIndexStats &memory_usage(const vespalib::MemoryUsage &usage) noexcept {
        _memory_usage = usage;
        return *this;
    }
    const vespalib::MemoryUsage &memory_usage() const noexcept { return _memory_usage; }
    FieldIndexStats &size_on_disk(size_t value) noexcept {
        _size_on_disk = value;
        return *this;
    }
    size_t size_on_disk() const noexcept { return _size_on_disk; }

    void merge(const FieldIndexStats &rhs) noexcept {
        _memory_usage.merge(rhs._memory_usage);
        _size_on_disk += rhs._size_on_disk;
    }

    bool operator==(const FieldIndexStats& rhs) const noexcept {
        return _memory_usage == rhs._memory_usage &&
        _size_on_disk == rhs._size_on_disk;
    }
};

std::ostream& operator<<(std::ostream& os, const FieldIndexStats& stats);

}
