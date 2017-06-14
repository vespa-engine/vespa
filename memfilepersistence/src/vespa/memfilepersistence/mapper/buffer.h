// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::memfile::Buffer
 * \ingroup memfile
 *
 * \brief Simple wrapper class to contain an aligned buffer.
 *
 * For direct IO operations, we need to use 512 byte aligned buffers. This is
 * a simple wrapper class to get such a buffer.
 */

#pragma once

#include <vespa/vespalib/util/alloc.h>

namespace storage {
namespace memfile {

class Buffer
{
    vespalib::alloc::Alloc _buffer;
    // Actual, non-aligned size (as opposed to _buffer.size()).
    size_t _size;

public:
    using UP = std::unique_ptr<Buffer>;

    Buffer(const Buffer &) = delete;
    Buffer & operator = (const Buffer &) = delete;
    Buffer(size_t size);

    /**
     * Resize buffer while keeping data that exists in the intersection of
     * the old and new buffers' sizes.
     */
    void resize(size_t size);

    char* getBuffer() noexcept {
        return static_cast<char*>(_buffer.get());
    }
    const char* getBuffer() const noexcept {
        return static_cast<const char*>(_buffer.get());
    }
    size_t getSize() const noexcept {
        return _size;
    }

    operator char*() noexcept { return getBuffer(); }

};

} // storage
} // memfile


