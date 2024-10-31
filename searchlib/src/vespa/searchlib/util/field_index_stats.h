// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "disk_io_stats.h"
#include <vespa/vespalib/util/memoryusage.h>
#include <iosfwd>

namespace search {

/**
 * Statistics for a single field index.
 **/
class FieldIndexStats
{
private:
    vespalib::MemoryUsage _memory_usage;
    size_t _size_on_disk; // in bytes
    DiskIoStats _disk_io_stats;

public:
    FieldIndexStats() noexcept
        : _memory_usage(),
          _size_on_disk(0),
          _disk_io_stats()
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

    FieldIndexStats& disk_io_stats(const DiskIoStats& stats) { _disk_io_stats = stats; return *this; }
    const DiskIoStats& disk_io_stats() const noexcept { return _disk_io_stats; }

    void merge(const FieldIndexStats &rhs) noexcept {
        _memory_usage.merge(rhs._memory_usage);
        _size_on_disk += rhs._size_on_disk;
        _disk_io_stats.merge(rhs._disk_io_stats);
    }

    bool operator==(const FieldIndexStats& rhs) const noexcept {
        return _memory_usage == rhs._memory_usage &&
        _size_on_disk == rhs._size_on_disk &&
        _disk_io_stats == rhs._disk_io_stats;
    }
};

std::ostream& operator<<(std::ostream& os, const FieldIndexStats& stats);

}
