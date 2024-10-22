// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <iosfwd>

namespace search {

/*
 * Class tracking disk io.
 */
class DiskIoStats {
    uint64_t _read_operations;
    uint64_t _read_bytes;

public:
    DiskIoStats() noexcept
        : _read_operations(0),
          _read_bytes(0)
    {}

    void add_read_operation(uint64_t bytes) noexcept {
        ++_read_operations;
        _read_bytes += bytes;
    }
    void merge(const DiskIoStats& rhs) noexcept {
        _read_operations += rhs._read_operations;
        _read_bytes += rhs._read_bytes;
    }
    bool operator==(const DiskIoStats& rhs) const noexcept {
        return _read_operations == rhs._read_operations &&
               _read_bytes == rhs._read_bytes;
    }
    void clear() noexcept {
        _read_operations = 0;
        _read_bytes = 0;
    }
    DiskIoStats read_and_clear() noexcept { auto result = *this; clear(); return result; }

    DiskIoStats& read_operations(uint64_t value) { _read_operations = value; return *this; }
    DiskIoStats& read_bytes(uint64_t value) { _read_bytes = value; return *this; }
    uint64_t read_operations() const noexcept { return _read_operations; }
    uint64_t read_bytes() const noexcept { return _read_bytes; }
};

std::ostream& operator<<(std::ostream& os, const DiskIoStats& stats);

}
