// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/memfilepersistence/mapper/buffer.h>
#include <algorithm>
#include <stdlib.h>

using vespalib::DefaultAlloc;
using vespalib::alloc::MMapAllocator;
using vespalib::alloc::Alloc;

namespace storage {
namespace memfile {

// Use AutoAlloc to transparently use mmap for large buffers.
// It is crucial that any backing buffer type returns an address that is
// 512-byte aligned, or direct IO will scream at us and fail everything.
Buffer::Buffer(size_t size)
    : _buffer(DefaultAlloc::create(size, MMapAllocator::HUGEPAGE_SIZE, 512)),
      _size(size)
{
}

void
Buffer::resize(size_t size)
{
    Alloc buffer = _buffer.create(size);
    size_t commonSize(std::min(size, _size));
    memcpy(buffer.get(), _buffer.get(), commonSize);
    _buffer.swap(buffer);
    _size = size;
}

} // memfile
} // storage
