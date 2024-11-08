// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "disk_io_stats.h"

namespace search {

/*
 * Class tracking disk io when using a cache.
 */
class CacheDiskIoStats {
    DiskIoStats _read;        // cache miss
    DiskIoStats _cached_read; // cache hit

public:
    CacheDiskIoStats() noexcept
        : _read(),
          _cached_read()
    {
    }

    CacheDiskIoStats& read(const DiskIoStats& value) { _read = value; return *this; }
    CacheDiskIoStats& cached_read(DiskIoStats& value) { _cached_read = value; return *this; }
    const DiskIoStats& read() const noexcept { return _read; }
    const DiskIoStats& cached_read() const noexcept { return _cached_read; }
    void merge(const CacheDiskIoStats& rhs) noexcept {
        _read.merge(rhs.read());
        _cached_read.merge(rhs.cached_read());
    }

    bool operator==(const CacheDiskIoStats &rhs) const noexcept {
        return _read == rhs.read() &&
               _cached_read == rhs.cached_read();
    }
    CacheDiskIoStats read_and_clear() noexcept { auto result = *this; clear(); return result; }
    void clear() noexcept {
        _read.clear();
        _cached_read.clear();
    }
    void add_uncached_read_operation(uint64_t bytes) noexcept { _read.add_read_operation(bytes); }
    void add_cached_read_operation(uint64_t bytes) noexcept { _cached_read.add_read_operation(bytes); }
};

std::ostream& operator<<(std::ostream& os, const CacheDiskIoStats& stats);

}
