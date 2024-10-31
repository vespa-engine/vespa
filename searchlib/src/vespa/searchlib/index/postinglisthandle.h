// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "postinglistcounts.h"
#include <cstdlib>
#include <memory>

namespace search::index {

/**
 * Class for owning a posting list in memory after having read it from
 * posting list file, or referencing a chunk of memory containing the
 * posting list (if the file was memory mapped).
 */
class PostingListHandle {
public:
    uint64_t _bitOffsetMem; // _mem relative to start of file
    const void *_mem;       // Memory backing posting list after read/mmap
    std::shared_ptr<void> _allocMem; // Allocated memory for posting list
    size_t _allocSize;      // Size of allocated memory
    uint64_t _read_bytes;   // Bytes read from disk (used by disk io stats)

    PostingListHandle()
    : _bitOffsetMem(0),
      _mem(nullptr),
      _allocMem(),
      _allocSize(0),
      _read_bytes(0)
    { }

    ~PostingListHandle();
};

}
