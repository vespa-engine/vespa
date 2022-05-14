// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <memory>

namespace vsm {

/**
 * Simple growable char buffer.
 **/
class CharBuffer
{
private:
    std::vector<char> _buffer;
    size_t            _pos;

public:
    typedef std::shared_ptr<CharBuffer> SP;

    /**
     * Creates a char buffer with len bytes.
     **/
    CharBuffer(size_t len = 0);

    /**
     * Copies n bytes from the src array into the underlying buffer at the
     * current position, and updates the position accordingly.
     * Resizing will occur if needed.
     **/
    void put(const char * src, size_t n);

    /**
     * Resizes the buffer so that the new length becomes len.
     * Resizing will not occur if len < current length.
     **/
    void resize(size_t len);

    /**
     * Resets the position to the beginning of the buffer.
     **/
    void reset() { _pos = 0; }

    const char * getBuffer() const { return &_buffer[0]; }
    size_t getLength() const { return _buffer.size(); }
    size_t getPos() const { return _pos; }
    size_t getRemaining() const { return getLength() - getPos(); }
    void put(char c) { put(&c, 1); }
};

}

