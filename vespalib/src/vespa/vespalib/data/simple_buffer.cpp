// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "simple_buffer.h"

namespace vespalib {

SimpleBuffer::SimpleBuffer()
    : _data(),
      _used(0)
{
}

Memory
SimpleBuffer::obtain()
{
    return Memory(&_data[0], _used);
}

Input &
SimpleBuffer::evict(size_t bytes)
{
    assert(bytes <= _used);
    _data.erase(_data.begin(), _data.begin() + bytes);
    _used -= bytes;
    return *this;
}

WritableMemory
SimpleBuffer::reserve(size_t bytes)
{
    assert((_used + bytes) >= _used);
    _data.resize(_used + bytes, char(0x55));
    return WritableMemory(&_data[_used], bytes);
}

Output &
SimpleBuffer::commit(size_t bytes)
{
    assert(bytes <= (_data.size() - _used));
    _used += bytes;
    return *this;
}

std::ostream &operator<<(std::ostream &os, const SimpleBuffer &buf) {
    return os << buf.get();
}

} // namespace vespalib
