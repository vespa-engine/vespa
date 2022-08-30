// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "smart_buffer.h"
#include <cassert>

namespace vespalib {

void
SmartBuffer::ensure_free(size_t bytes)
{
    if (write_len() >= bytes) {
        return;
    }
    if ((unused() < bytes) || ((unused() * 3) < read_len())) {
        size_t new_size = std::max(_data.size() * 2, read_len() + bytes);
        alloc::Alloc new_buf(alloc::Alloc::alloc(new_size));
        if (read_ptr()) {
            memcpy(new_buf.get(), read_ptr(), read_len());
        }
        _data.swap(new_buf);
    } else {
        if (read_ptr()) {
            memmove(_data.get(), read_ptr(), read_len());
        }
    }
    _write_pos = read_len();
    _read_pos = 0;
}

void
SmartBuffer::drop()
{
    alloc::Alloc empty_buf;
    _data.swap(empty_buf);
    reset();
}

SmartBuffer::SmartBuffer(size_t initial_size)
    : _data(alloc::Alloc::alloc(initial_size)),
      _read_pos(0),
      _write_pos(0)
{
}

SmartBuffer::~SmartBuffer() = default;

Memory
SmartBuffer::obtain()
{
    return Memory(read_ptr(), read_len());
}

Input &
SmartBuffer::evict(size_t bytes)
{
    assert(read_len() >= bytes);
    _read_pos += bytes;
    if (_read_pos == _write_pos) {
        _read_pos = 0;
        _write_pos = 0;
    }
    return *this;
}

WritableMemory
SmartBuffer::reserve(size_t bytes)
{
    ensure_free(bytes);
    return WritableMemory(write_ptr(), write_len());
}

Output &
SmartBuffer::commit(size_t bytes)
{
    assert(write_len() >= bytes);
    _write_pos += bytes;
    return *this;
}

} // namespace vespalib
