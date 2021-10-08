// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace vespalib {
namespace ws {

class Buffer
{
private:
    std::vector<char> _data;
    size_t _read_pos;
    size_t _write_pos;
    void ensure_free(size_t bytes);
public:
    Buffer() : _data(), _read_pos(0), _write_pos(0) {}
    void clear() {
        _read_pos = 0;
        _write_pos = 0;
    }
    size_t dead() const { return _read_pos; }
    size_t used() const { return (_write_pos - _read_pos); }
    size_t free() const { return (_data.size() - _write_pos); }
    bool has_next() const { return (used() > 0); }
    char next() { return _data[_read_pos++]; }
    void push(char value) {
        *reserve(1) = value;
        commit(1);
    }
    const char *obtain() const { return &_data[_read_pos]; }
    void evict(size_t bytes) { _read_pos += bytes; }
    char *reserve(size_t bytes) {
        if (free() < bytes) {
            ensure_free(bytes);
        }
        return &_data[_write_pos];
    }
    void commit(size_t bytes) { _write_pos += bytes; }
};

} // namespace vespalib::ws
} // namespace vespalib
