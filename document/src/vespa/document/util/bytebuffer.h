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
    ByteBuffer& operator=(ByteBuffer &&) = delete;

    ~ByteBuffer();

    /**
     * Create a buffer with the given content.
     *
     * @param buffer The buffer to represent.
     * @param len The length of the buffer
     */
    ByteBuffer(const char* buffer, uint32_t len);

    /**
     * Create a buffer with the given content.
     *
     * @param buffer The buffer to represent.
     * @param len The length of the buffer
     */
    ByteBuffer(vespalib::alloc::Alloc buffer, uint32_t len);

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
    static ByteBuffer* copyBuffer(const char* buffer, uint32_t len);

    /** @return Returns the buffer pointed to by this object (at position 0) */
    const char* getBuffer() const { return _buffer; }

    /** @return Returns the length of the buffer pointed to by this object. */
    uint32_t getLength() const { return _len; }

    /** @return Returns a pointer to the current position in the buffer. */
    const char* getBufferAtPos() const { return _buffer + _pos; }

    /** @return Returns the index of the current position in the buffer. */
    uint32_t getPos() const { return _pos; }

    /**
     * @return Returns the number of bytes remaining in the buffer - that is,
     *         getLength()-getPos().
    */
    uint32_t getRemaining() const { return _len -_pos; }

    /**
     * Moves the position in the buffer.
     *
     * @param pos The number of bytes to move the position. The new position
     *            will be oldPos + pos. This is the same as doing
     *            setPos(getPos()+pos)
     * @throws BufferOutOfBoundsException;
     */
    void incPos(uint32_t pos);

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
    void getBytes(void *buffer, uint32_t count);

private:
    template<typename T>
    void getDoubleLongNetwork(T &val);

    void incPosNoCheck(uint32_t pos) { _pos += pos; }

    const char *   _buffer;
    const uint32_t _len;
    uint32_t       _pos;
    vespalib::alloc::Alloc _ownedBuffer;
};

} // document

