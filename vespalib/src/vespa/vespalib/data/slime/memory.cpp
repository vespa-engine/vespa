// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory.h"
#include "stored_memory.h"

namespace vespalib {
namespace slime {

Memory::Memory(const StoredMemory &sm)
    : data(sm._data),
      size(sm._size)
{
}

vespalib::string
Memory::make_string() const
{
    return vespalib::string(data, size);
}

} // namespace vespalib::slime
} // namespace vespalib
