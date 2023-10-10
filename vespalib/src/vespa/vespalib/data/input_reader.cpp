// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "input_reader.h"
#include "input.h"

namespace vespalib {

size_t
InputReader::obtain_slow()
{
    _data = _input.evict(_pos).obtain();
    _bytes_evicted += _pos;
    _pos = 0;
    if (_data.size == 0) {
        _eof = true;
    }
    return size();
}

char
InputReader::read_slow()
{
    if (!failed()) {
        fail("input underflow");
    }
    return 0;
}

Memory
InputReader::read_slow(size_t bytes)
{
    _space.clear();
    while ((_space.size() < bytes) && (obtain() > 0)) {
        size_t copy_now = std::min(size(), (bytes - _space.size()));
        _space.insert(_space.end(), data(), data() + copy_now);
        _pos += copy_now;
    }
    if (_space.size() == bytes) {
        return Memory(&_space[0], _space.size());
    }
    if (!failed()) {
        fail("input underflow");
    }
    return Memory();
}

InputReader::~InputReader()
{
    _input.evict(_pos);
}

void
InputReader::fail(const vespalib::string &msg) {
    if (!failed()) {
        _error = msg;
        _input.evict(_pos);
        _data = Memory();
        _bytes_evicted += _pos;
        _pos = 0;
        _eof = true;
    }
}

} // namespace vespalib
