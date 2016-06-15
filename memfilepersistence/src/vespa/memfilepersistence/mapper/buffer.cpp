// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/memfilepersistence/mapper/buffer.h>
#include <algorithm>
#include <stdlib.h>

namespace storage {
namespace memfile {

Buffer::Buffer(size_t size)
    : _buffer(size),
      _size(size)
{
}

void
Buffer::resize(size_t size)
{
    BackingType buffer(size);
    size_t commonSize(std::min(size, _size));
    memcpy(buffer.get(), _buffer.get(), commonSize);
    _buffer.swap(buffer);
    _size = size;
}

} // memfile
} // storage
