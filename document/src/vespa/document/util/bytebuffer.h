// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::ByteBuffer
 * \ingroup util
 *
 * \brief Java like bytebuffer class
 *
 * This class wraps a char* buffer with a length and position.
 * It can be used to hide from the user whether the buffer was
 * allocated or not, and can hold a position in the buffer which
 * can be used for streaming-like behaviour.
 *
 * @author Thomas F. Gundersen, �ystein Fledsberg, Einar Rosenvinge
 */
#pragma once

#include <vespa/vespalib/util/alloc.h>

namespace document {

class ByteBuffer
{
public:
    typedef std::unique_ptr<ByteBuffer> UP;

    ByteBuffer(const ByteBuffer &);
    ByteBuffer& operator=(const ByteBuffer &) = delete;
    ByteBuffer(ByteBuffer &&) = default;
    ByteBuffer& operator=(ByteBuffer &&) = default;

    ~ByteBuffer();

    /** Allocates buffer with len bytes. */
    ByteBuffer(size_t len);

    /**
     * Create a buffer with the given content.
     *
     * @param buffer The buffer to represent.
     * @param len The length of the buffer
     */
    ByteBuffer(const char* buffer, size_t len);

    /**
     * Create a buffer with the given content.
     *
     * @param buffer The buffer to represent.
     * @param len The length of the buffer
     */
    ByteBuffer(vespalib::alloc::Alloc buffer, size_t len);

    /**
     * Creates a ByteBuffer object from another buffer. allocates
     * a new buffer of same size and copies the content.
     *
     * @param buffer The buffer to copy.
     * @param len The length of the buffer.
     *
     *  @return Returns a newly created bytebuffer object, or nullptr
     *  if buffer was nullptr, or len was <=0.
     */
    static ByteBuffer* copyBuffer(const char* buffer, size_t len);

    /** @return Returns the buffer pointed to by this object (at position 0) */
    char* getBuffer() const { return _buffer; }

    /** @return Returns the length of the buffer pointed to by this object. */
    size_t getLength() const { return _len; }

    /** @return Returns a pointer to the current position in the buffer. */
    char* getBufferAtPos() const { return _buffer + _pos; }

    /** @return Returns the index of the current position in the buffer. */
    size_t getPos() const { return _pos; }

    /**
     * @return Returns the number of bytes remaining in the buffer - that is,
     *         getLimit()-getPos().
    */
    size_t getRemaining() const { return _len -_pos; }

    /**
     * Changes the position in the buffer.
     *
     * @throws BufferOutOfBoundsException;
     */
    void setPos(size_t pos);

    /**
     * Moves the position in the buffer.
     *
     * @param pos The number of bytes to move the position. The new position
     *            will be oldPos + pos. This is the same as doing
     *            setPos(getPos()+pos)
     * @return    True if the position could be moved (it was inside the limit
     *            of the buffer).
     * @throws BufferOutOfBoundsException;
     */
    void incPos(size_t pos);

    /**
     * Resets pos to 0, and sets limit to old pos. Use this before reading
     * from a buffer you have written to
     */
    void flip() {
        _pos = 0;
    }

    void getNumeric(uint8_t & v);
    void getNumericNetwork(int16_t & v);
    void getNumericNetwork(int32_t & v);

    void getNumericNetwork(int64_t & v);
    void getNumeric(int64_t& v);
    void getNumericNetwork(double & v);

    void getChar(char & val) { unsigned char t;getByte(t); val=t; }
    void getByte(uint8_t & v)         { getNumeric(v); }
    void getShortNetwork(int16_t & v) { getNumericNetwork(v); }
    void getIntNetwork(int32_t & v)   { getNumericNetwork(v); }
    void getLongNetwork(int64_t & v)  { getNumericNetwork(v); }
    void getLong(int64_t& v)          { getNumeric(v); }
    void getDoubleNetwork(double & v) { getNumericNetwork(v); }

    /**
     *  Reads the given number of bytes into the given pointer, and updates the
     *  positition accordingly
     *
     *   @param	buffer	where to store the bytes
     *   @param	count	number of bytes to read
     *   @return	    True if all the bytes could be read, false if end of
     *                  buffer is reached
     */
    void getBytes(void *buffer, size_t count);

    /**
     * Writes the given number of bytes into the ByteBuffer at the current
     * position, and updates the positition accordingly
     *
     * @param buf the bytes to store
     * @param count number of bytes to store
     */
    void putBytes(const void *buf, size_t count);

private:
    template<typename T>
    void getDoubleLongNetwork(T &val);

    void incPosNoCheck(size_t pos) { _pos += pos; }

    char   * _buffer;
    size_t   _len;
    size_t   _pos;
    vespalib::alloc::Alloc _ownedBuffer;
};

} // document

