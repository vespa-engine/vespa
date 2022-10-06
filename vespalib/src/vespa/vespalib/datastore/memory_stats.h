// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdint>

namespace vespalib::datastore {

/**
 * Represents aggregated memory statistics for all buffers in a data store.
 */
class MemoryStats
{
public:
    size_t _allocElems;
    size_t _usedElems;
    size_t _deadElems;
    size_t _holdElems;
    size_t _allocBytes;
    size_t _usedBytes;
    size_t _deadBytes;
    size_t _holdBytes;
    uint32_t _freeBuffers;
    uint32_t _activeBuffers;
    uint32_t _holdBuffers;

    MemoryStats();
    MemoryStats& operator+=(const MemoryStats& rhs);
};

}
