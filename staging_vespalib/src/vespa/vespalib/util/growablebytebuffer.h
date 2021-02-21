// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

/**
Class that wraps a vector<char> and resizes it as necessary to hold data
put into it. Also has several utility functions commonly used in
data serialization.

All of the numeric helper methods (putInt/Long/Double etc.) in this class
store the numbers in network byte order.
*/
class GrowableByteBuffer
{
public:
    /**
       Creates a new GrowableByteBuffer with the given initial length and grow factor.
    */
    GrowableByteBuffer(uint32_t initialLen = 256);

    /**
       If necessary, grows the buffer so it can hold the specified number of bytes,
       then moves the position to after that length, and returns a pointer to the previous
       position.

       @param len The length to allocate
       @return Returns a pointer to a buffer that the user can write data to.
    */
    char* allocate(uint32_t len);

    /**
       Returns a pointer to the start of the allocated buffer.
    */
    const char* getBuffer() const { return static_cast<const char *>(_buffer.get()); }

    /**
       Returns the current position.
    */
    uint32_t position() const { return _position; }

    /**
       Adds the given buffer to this buffer.
    */
    void putBytes(const void * buffer, uint32_t length);

    /**
       Adds a short to the buffer.
    */
    void putShort(uint16_t v);

    /**
       Adds an int to the buffer.
    */
    void putInt(uint32_t v);

    /**
       Adds a long to the buffer.
    */
    void putLong(uint64_t v);

    /**
       Adds a double to the buffer.
    */
    void putDouble(double v);

    /**
       Adds a string to the buffer.
    */
    void putString(vespalib::stringref v);

    /**
       Adds a single byte to the buffer.
    */
    void putByte(uint8_t v);

    /**
       Adds a boolean to the buffer.
    */
    void putBoolean(bool v);

private:
    void putReverse(const char* buffer, uint32_t length);
    using Alloc = vespalib::alloc::Alloc;
    Alloc _buffer;

    uint32_t _position;
};

}

