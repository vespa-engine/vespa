// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "alignedmemory.h"
#include <stdint.h>

namespace vespalib {

AlignedMemory::AlignedMemory(size_t size, size_t align)
    : _alloc(size == 0 ? 0 : new char[size + ((align > 1) ? (align - 1) : 0)]),
      _align(_alloc)
{
    if (align > 1) {
        _align += (align - (((uintptr_t)_alloc) % align)) % align;
    }
}

void
AlignedMemory::swap(AlignedMemory &rhs)
{
    std::swap(_alloc, rhs._alloc);
    std::swap(_align, rhs._align);
}

AlignedMemory::~AlignedMemory()
{
    delete[] _alloc;
}

} // namespace search
