// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <algorithm>
#include <cstdint>
#include <iosfwd>

namespace search {

/*
 * Class tracking disk io.
 */
class DiskIoStats {
    uint64_t _read_operations;
    uint64_t _read_bytes_total;
    uint64_t _read_bytes_min;
    uint64_t _read_bytes_max;

public:
    DiskIoStats() noexcept
        : _read_operations(0),
          _read_bytes_total(0),
          _read_bytes_min(0),
          _read_bytes_max(0)
    {}

    void add_read_operation(uint64_t bytes) noexcept {
        if (++_read_operations == 1) {
            _read_bytes_total = bytes;
            _read_bytes_min = bytes;
            _read_bytes_max = bytes;
        } else {
            _read_bytes_total += bytes;
            _read_bytes_min = std::min(_read_bytes_min, bytes);
            _read_bytes_max = std::max(_read_bytes_max, bytes);
        }
    }
    void merge(const DiskIoStats& rhs) noexcept {
        if (rhs._read_operations != 0) {
            if (_read_operations == 0) {
                *this = rhs;
            } else {
                _read_operations += rhs._read_operations;
                _read_bytes_total += rhs._read_bytes_total;
                _read_bytes_min = std::min(_read_bytes_min, rhs._read_bytes_min);
                _read_bytes_max = std::max(_read_bytes_max, rhs._read_bytes_max);
            }
        }
    }
    bool operator==(const DiskIoStats& rhs) const noexcept {
        return _read_operations == rhs._read_operations &&
               _read_bytes_total == rhs._read_bytes_total &&
               _read_bytes_min == rhs._read_bytes_min &&
               _read_bytes_max == rhs._read_bytes_max;
    }
    void clear() noexcept {
        _read_operations = 0;
        _read_bytes_total = 0;
        _read_bytes_min = 0;
        _read_bytes_max = 0;
    }
    DiskIoStats read_and_clear() noexcept { auto result = *this; clear(); return result; }

    DiskIoStats& read_operations(uint64_t value) { _read_operations = value; return *this; }
    DiskIoStats& read_bytes_total(uint64_t value) { _read_bytes_total = value; return *this; }
    DiskIoStats& read_bytes_min(uint64_t value) { _read_bytes_min = value; return *this; }
    DiskIoStats& read_bytes_max(uint64_t value) { _read_bytes_max = value; return *this; }
    uint64_t read_operations() const noexcept { return _read_operations; }
    uint64_t read_bytes_total() const noexcept { return _read_bytes_total; }
    uint64_t read_bytes_min() const noexcept { return _read_bytes_min; }
    uint64_t read_bytes_max() const noexcept { return _read_bytes_max; }
};

std::ostream& operator<<(std::ostream& os, const DiskIoStats& stats);

}
