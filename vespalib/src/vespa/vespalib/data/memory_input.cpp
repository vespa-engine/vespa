// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "memory_input.h"

namespace vespalib {

Memory
MemoryInput::obtain()
{
    return Memory((_data.data + _pos), (_data.size - _pos));
}

Input &
MemoryInput::evict(size_t bytes)
{
    _pos += bytes;
    return *this;
}

} // namespace vespalib
