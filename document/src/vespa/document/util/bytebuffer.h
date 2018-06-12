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
#include <vespa/vespalib/util/referencecounter.h>

namespace document {

class ByteBuffer
{
public:
    typedef std::unique_ptr<ByteBuffer> UP;
    /**
     * Creates a byte buffer with no underlying buffer.
     * Use set() to set the buffer.
     */
    ByteBuffer();

    ByteBuffer(const ByteBuffer &);
    ByteBuffer& operator=(const ByteBuffer &);

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
     * Sets the buffer pointed to by this buffer. Allows for multiple
     * usages of the same ByteBuffer.
     */
    void set(const char* buffer, size_t len) {
        cleanUp();
        _buffer = const_cast<char*>(buffer);
        _len=len;
        _limit=len;
        _pos=0;
    }

    /** Clear this buffer, and set free the underlying BufferHolder. */
    void reset() { set(NULL, 0); }

    /**
     * Creates a ByteBuffer object from another buffer. allocates
     * a new buffer of same size and copies the content.
     *
     * @param buffer The buffer to copy.
     * @param len The length of the buffer.
     *
     *  @return Returns a newly created bytebuffer object, or NULL
     *  if buffer was NULL, or len was <=0.
     */
    static ByteBuffer* copyBuffer(const char* buffer, size_t len);

    std::unique_ptr<ByteBuffer> sliceCopy() const;

    /**
     * @throws BufferOutOfBoundsException If faulty range is given.
     */
    void sliceFrom(const ByteBuffer& buf, size_t from, size_t to);

    /** @return Returns the buffer pointed to by this object (at position 0) */
    char* getBuffer() const { return _buffer; }

    /** @return Returns the length of the buffer pointed to by this object. */
    size_t getLength() const { return _len; }

    /**
     * Adjust the length of the buffer. Only sane to shorten it, as you do not
     * know what is ahead.
     */
    void setLength(size_t len) { _len = len; }

    /** @return Returns a pointer to the current position in the buffer. */
    char* getBufferAtPos() const { return _buffer + _pos; }

    /** @return Returns the index of the current position in the buffer. */
    size_t getPos() const { return _pos; }

    /** @return Returns the limit. */
    size_t getLimit() const { return _limit; }

    /**
     * @return Returns the number of bytes remaining in the buffer - that is,
     *         getLimit()-getPos().
    */
    size_t getRemaining() const { return _limit-_pos; }

    /**
     * Changes the position in the buffer.
     *
     * @throws BufferOutOfBoundsException;
     */
    void setPos(size_t pos);

    /**
     * Sets the buffer limit.
     *
     * @param limit The new limit.
     * @return True if the limit is legal (less than the length)
     * @throws BufferOutOfBoundsException;
     */
    void setLimit(size_t limit);
    size_t forceValgrindStart2Pos() const __attribute__ ((noinline));
    size_t forceValgrindPos2Lim() const __attribute__ ((noinline));

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

    void incPosNoCheck(size_t pos) {
        _pos += pos;
#ifdef __FORCE_VALGRIND_ON_SERIALIZE__
        forceValgrindStart2Pos();
#endif
    }

    /**
     * Resets pos to 0, and sets limit to old pos. Use this before reading
     * from a buffer you have written to
     */
    void flip() {
        _limit = _pos;
        _pos = 0;
    }

    /**
     * Sets pos to 0 and limit to length. Use this to start writing from the
     * start of the buffer.
     */
    void clear() {
        _pos=0;
        _limit=_len;
    }

    void getNumericNetwork(uint8_t & v) { getNumeric(v); }
    void getNumeric(uint8_t & v);
    void putNumericNetwork(uint8_t v) { putNumeric(v); }
    void putNumeric(uint8_t v);
    void getNumericNetwork(int16_t & v);
    void getNumeric(int16_t & v);
    void putNumericNetwork(int16_t v);
    void putNumeric(int16_t v);
    void getNumericNetwork(int32_t & v);
    void getNumeric(int32_t & v);
    void putNumericNetwork(int32_t v);
    void putNumeric(int32_t v);
    void getNumericNetwork(float & v);
    void getNumeric(float & v);
    void putNumericNetwork(float v);
    void putNumeric(float v);
    void getNumericNetwork(int64_t & v);
    void getNumeric(int64_t& v);
    void putNumericNetwork(int64_t v);
    void putNumeric(int64_t v);
    void getNumericNetwork(double & v);
    void getNumeric(double& v);
    void putNumericNetwork(double v);
    void putNumeric(double v);

    void getByte(uint8_t & v)         { getNumeric(v); }
    void putByte(uint8_t v)           { putNumeric(v); }
    void getShortNetwork(int16_t & v) { getNumericNetwork(v); }
    void getShort(int16_t & v)        { getNumeric(v); }
    void putShortNetwork(int16_t v)   { putNumericNetwork(v); }
    void putShort(int16_t v)          { putNumeric(v); }
    void getIntNetwork(int32_t & v)   { getNumericNetwork(v); }
    void getInt(int32_t & v)          { getNumeric(v); }
    void putIntNetwork(int32_t v)     { putNumericNetwork(v); }
    void putInt(int32_t v)            { putNumeric(v); }
    void getFloatNetwork(float & v)   { getNumericNetwork(v); }
    void getFloat(float & v)          { getNumeric(v); }
    void putFloatNetwork(float v)     { putNumericNetwork(v); }
    void putFloat(float v)            { putNumeric(v); }
    void getLongNetwork(int64_t & v)  { getNumericNetwork(v); }
    void getLong(int64_t& v)          { getNumeric(v); }
    void putLongNetwork(int64_t v)    { putNumericNetwork(v); }
    void putLong(int64_t v)           { putNumeric(v); }
    void getDoubleNetwork(double & v) { getNumericNetwork(v); }
    void getDouble(double& v)         { getNumeric(v); }
    void putDoubleNetwork(double v)   { putNumericNetwork(v); }
    void putDouble(double v)          { putNumeric(v); }

 private:
    void throwOutOfBounds(size_t want, size_t has) __attribute__((noinline,noreturn));
    uint8_t peekByte() const { return *getBufferAtPos(); }

#if defined(__i386__) || defined(__x86_64__)

    template<typename T>
    void putDoubleLongNetwork(T val) {
        //TODO: Change this if we move to big-endian hardware
        if (__builtin_expect(getRemaining() < (int)sizeof(T), 0)) {
            throwOutOfBounds(sizeof(T), getRemaining());
        }
        unsigned char* data = reinterpret_cast<unsigned char*>(&val);
        for (int i=sizeof(T)-1; i>=0; --i) {
            putByte(data[i]);
        }
    }

    template<typename T>
    void getDoubleLongNetwork(T &val) {
        //TODO: Change this if we move to big-endian hardware
        if (__builtin_expect(getRemaining() < (int)sizeof(T), 0)) {
            throwOutOfBounds(sizeof(T), getRemaining());
        }

        unsigned char* data = reinterpret_cast<unsigned char*>(&val);
        for (int i=sizeof(T)-1; i>=0; --i) {
            getByte(data[i]);
        }
    }
#else
    #error "getDoubleLongNetwork is undefined for this arcitecture"
#endif

 public:
    /**
     * Writes a 62-bit positive integer to the buffer, using 2, 4, or 8 bytes.
     *
     * @param number the integer to write
     */
    void putInt2_4_8Bytes(int64_t number) {
        putInt2_4_8Bytes(number, 0);
    }

    /**
     * Writes a 62-bit positive integer to the buffer, using 2, 4, or 8 bytes.
     *
     * @param number the integer to write
     * @param len if non-zero, force writing number using len bytes, possibly
     *            with truncation
     */
    void putInt2_4_8Bytes(int64_t number, size_t len);

    /**
     * Reads a 62-bit positive integer from the buffer, which was written using
     * 2, 4, or 8 bytes.
     *
     * @param v the integer read
     */
    void getInt2_4_8Bytes(int64_t & v);

    /**
     * Computes the size used for storing the given integer using 2, 4 or 8
     * bytes.
     *
     * @param number the integer to check length of
     * @return the number of bytes used to store it; 2, 4 or 8
     */
    static size_t getSerializedSize2_4_8Bytes(int64_t number);

    /**
     * Writes a 30-bit positive integer to the buffer, using 1, 2, or 4 bytes.
     *
     * @param number the integer to write
     */
    void putInt1_2_4Bytes(int32_t number);

    /**
     * Reads a 30-bit positive integer from the buffer, which was written using
     * 1, 2, or 4 bytes.
     *
     * @param v the integer read
     */
    void getInt1_2_4Bytes(int32_t & v);

    /**
     * Computes the size used for storing the given integer using 1, 2 or 4
     * bytes.
     *
     * @param number the integer to check length of
     * @return the number of bytes used to store it; 1, 2 or 4
     */
    static size_t getSerializedSize1_2_4Bytes(int32_t number);

    /**
     * Writes a 31-bit positive integer to the buffer, using 1 or 4 bytes.
     *
     * @param number the integer to write
     */
    void putInt1_4Bytes(int32_t number);

    /**
     * Reads a 31-bit positive integer from the buffer, which was written using
     * 1 or 4 bytes.
     *
     * @param v the integer read
     */
    void getInt1_4Bytes(int32_t & v);

    /**
     * Computes the size used for storing the given integer using 1 or 4 bytes.
     *
     * @param number the integer to check length of
     * @return the number of bytes used to store it; 1 or 4
     */
    static size_t getSerializedSize1_4Bytes(int32_t number);

    /**
     * Writes a 8 bit integer to the buffer at the current position, and
     * increases the positition accordingly.
     *
     * @param  val the int to store
     * @return True if the value could be stored, false if end of buffer is
     *         reached
    */
    void getChar(char & val) { unsigned char t;getByte(t); val=t; }
    void putChar(char val)   { putByte(static_cast<unsigned char>(val)); }

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

    /** Debug */
    void dump() const;

    class BufferHolder : public vespalib::ReferenceCounter
    {
    private:
        BufferHolder(const BufferHolder &);
        BufferHolder& operator=(const BufferHolder &);

    public:
        BufferHolder(vespalib::alloc::Alloc buffer);
        virtual ~BufferHolder();

        vespalib::alloc::Alloc _buffer;
    };

    ByteBuffer(BufferHolder* buf, size_t pos, size_t len, size_t limit);

    void set(BufferHolder* buf, size_t pos, size_t len, size_t limit);

private:
    char * _buffer;
    size_t _len;
    size_t _pos;
    size_t _limit;
    mutable BufferHolder   * _bufHolder;
    vespalib::alloc::Alloc _ownedBuffer;
public:

    std::string toString();

    void swap(ByteBuffer& other);

    void cleanUp();
};

} // document

