// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "simple_buffer.h"

namespace vbench {

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

bool
SimpleBuffer::operator==(const SimpleBuffer &rhs) const
{
    Memory a = get();
    Memory b = rhs.get();
    if (a.size != b.size) {
        return false;
    }
    for (size_t i = 0; i < a.size; ++i) {
        if (a.data[i] != b.data[i]) {
            return false;
        }
    }
    return true;
}

std::ostream &operator<<(std::ostream &os, const SimpleBuffer &buf) {
    Memory memory = buf.get();
    uint32_t written = 0;
    uint32_t hexCount = 25;
    os << "size: " << memory.size << "(bytes)" << std::endl;
    for (size_t i = 0; i < memory.size; ++i, ++written) {
        if (written > hexCount) {
            os << std::endl;
            written = 0;
        }
        os << strfmt("0x%02x ", memory.data[i] & 0xff);
    }
    if (written > 0) {
        os << std::endl;
    }
    return os;
}

} // namespace vbench
