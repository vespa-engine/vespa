// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/compress.h>

#include <cassert>
#include <cstring>

/**
 * This is a buffer that may hold the stream representation of
 * packets. It has helper methods in order to simplify and standardize
 * packet encoding and decoding. The default byte order for
 * reading/writing integers is internet order (big endian). The
 * methods with a 'Reverse' suffix assume that the data in the buffer
 * is stored in reverse internet order (little endian).
 *
 * A databuffer covers a continuous chunk of memory that is split into
 * 3 parts; 'dead', 'data' and 'free'. 'dead' denotes the space at the
 * beginning of the buffer that may not currently be utilized, 'data'
 * denotes the part that contains the actual data and 'free' denotes
 * the free space at the end of the buffer. Initially, the 'dead' and
 * 'data' parts are empty, and the 'free' part spans the entire
 * buffer. When writing to the buffer, bytes are transfered from the
 * 'free' part to the 'data' part of the buffer. When reading from the
 * buffer, bytes are transferred from the 'data' part to the 'dead'
 * part of the buffer. If the 'free' part of the buffer becomes empty,
 * the data will be relocated within the buffer and/or a bigger buffer
 * will be allocated.
 **/
class FNET_DataBuffer {
private:
    using Alloc = vespalib::alloc::Alloc;
    char* _bufstart;
    char* _bufend;
    char* _datapt;
    char* _freept;
    Alloc _ownedBuf;

    FNET_DataBuffer(const FNET_DataBuffer&);
    FNET_DataBuffer& operator=(const FNET_DataBuffer&);

public:
    /**
     * Construct a databuffer.
     *
     * @param len the initial size of the buffer.
     **/
    FNET_DataBuffer(uint32_t len = 1024);

    /**
     * Construct a databuffer using externally allocated memory. Note
     * that the externally allocated memory will not be freed by the
     * databuffer.
     *
     * @param buf pointer to preallocated memory
     * @param len length of preallocated memory
     **/
    FNET_DataBuffer(char* buf, uint32_t len);
    ~FNET_DataBuffer();

    /**
     * @return a pointer to the dead part of this buffer.
     **/
    char* GetDead() { return _bufstart; }

    /**
     * @return a pointer to the data part of this buffer.
     **/
    char* GetData() { return _datapt; }

    /**
     * @return a pointer to the free part of this buffer.
     **/
    char* GetFree() { return _freept; }

    /**
     * @return the length of the dead part of this buffer.
     **/
    uint32_t GetDeadLen() const { return _datapt - _bufstart; }

    /**
     * @return the length of the data part of this buffer.
     **/
    uint32_t GetDataLen() const { return _freept - _datapt; }

    /**
     * @return the length of the free part of this buffer.
     **/
    uint32_t GetFreeLen() const { return _bufend - _freept; }

    /**
     * @return the length of the entire buffer.
     **/
    uint32_t GetBufSize() const { return _bufend - _bufstart; }

    /**
     * 'Move' bytes from the free part to the data part of this buffer.
     * This will have the same effect as if the data already located in
     * the free part of this buffer was written to the buffer.
     *
     * @param len number of bytes to 'move'.
     **/
    void FreeToData(uint32_t len);

    /**
     * 'Move' bytes from the data part to the dead part of this buffer.
     * This will have the effect of discarding data without having to
     * read it.
     *
     * @param len number of bytes to 'move'.
     **/
    void DataToDead(uint32_t len) { _datapt += len; }

    /**
     * 'Move' bytes from the dead part to the data part of this buffer.
     * This may be used to undo a read operation (un-discarding
     * data). Note that writing to the buffer may result in
     * reorganization making the data part of the buffer disappear.
     *
     * @param len number of bytes to 'move'.
     **/
    void DeadToData(uint32_t len);

    /**
     * 'Move' bytes from the data part to the free part of this buffer.
     * This may be used to undo a write operation; discarding the data
     * most recently written.
     *
     * @param len number of bytes to 'move'.
     **/
    void DataToFree(uint32_t len);

    /**
     * Clear this buffer.
     **/
    void Clear() { _datapt = _freept = _bufstart; }

    /**
     * Shrink this buffer. The given value is the new wanted size of
     * this buffer. If the buffer is already smaller or equal in size
     * compared to the given value, no resizing is performed and false
     * is returned (Use the @ref EnsureFree method to ensure free
     * space). If the buffer currently contains more data than can be
     * held in a buffer of the wanted size, no resizing is performed and
     * false is returned.
     *
     * @param newsize the wanted new size of this buffer (in bytes).
     * @return true if the buffer was shrunk, false otherwise.
     **/
    bool Shrink(uint32_t newsize);

    /**
     * Reorganize this buffer such that the dead part becomes empty and
     * the free part contains at least the given number of
     * bytes. Allocate a bigger buffer if needed.
     *
     * @param needbytes required size of free part.
     **/
    void Pack(uint32_t needbytes);

    /**
     * Ensure that the free part contains at least the given number of
     * bytes. This method invokes the @ref Pack method if the free part
     * of the buffer is too small.
     *
     * @param needbytes required size of free part.
     **/
    void EnsureFree(uint32_t needbytes) {
        if (needbytes > GetFreeLen())
            Pack(needbytes);
    }

    /**
     * Write an 8-bit unsigned integer to this buffer.
     *
     * @param n the integer to write.
     **/
    void WriteInt8(uint8_t n) {
        EnsureFree(1);
        *_freept++ = (char)n;
    }

    /**
     * Write a 16-bit unsigned integer to this buffer.
     *
     * @param n the integer to write.
     **/
    void WriteInt16(uint16_t n) {
        EnsureFree(2);
        _freept[1] = (char)n;
        n >>= 8;
        _freept[0] = (char)n;
        _freept += 2;
    }

    /**
     * Write a 32-bit unsigned integer to this buffer.
     *
     * @param n the integer to write.
     **/
    void WriteInt32(uint32_t n) {
        EnsureFree(4);
        _freept[3] = (char)n;
        n >>= 8;
        _freept[2] = (char)n;
        n >>= 8;
        _freept[1] = (char)n;
        n >>= 8;
        _freept[0] = (char)n;
        _freept += 4;
    }

    /**
     * Write a 64-bit unsigned integer to this buffer.
     *
     * @param n the integer to write.
     **/
    void WriteInt64(uint64_t n) {
        EnsureFree(8);
        _freept[7] = (char)n;
        n >>= 8;
        _freept[6] = (char)n;
        n >>= 8;
        _freept[5] = (char)n;
        n >>= 8;
        _freept[4] = (char)n;
        n >>= 8;
        _freept[3] = (char)n;
        n >>= 8;
        _freept[2] = (char)n;
        n >>= 8;
        _freept[1] = (char)n;
        n >>= 8;
        _freept[0] = (char)n;
        _freept += 8;
    }

    /**
     * Write an 8-bit unsigned integer to this buffer. Skip checking for
     * free space.
     *
     * @param n the integer to write.
     **/
    void WriteInt8Fast(uint8_t n) { *_freept++ = (char)n; }

    /**
     * Write a 16-bit unsigned integer to this buffer. Skip checking for
     * free space.
     *
     * @param n the integer to write.
     **/
    void WriteInt16Fast(uint16_t n) {
        _freept[1] = (char)n;
        n >>= 8;
        _freept[0] = (char)n;
        _freept += 2;
    }

    /**
     * Write a 32-bit unsigned integer to this buffer. Skip checking for
     * free space.
     *
     * @param n the integer to write.
     **/
    void WriteInt32Fast(uint32_t n) {
        _freept[3] = (char)n;
        n >>= 8;
        _freept[2] = (char)n;
        n >>= 8;
        _freept[1] = (char)n;
        n >>= 8;
        _freept[0] = (char)n;
        _freept += 4;
    }

    /**
     * Write a 64-bit unsigned integer to this buffer. Skip checking for
     * free space.
     *
     * @param n the integer to write.
     **/
    void WriteInt64Fast(uint64_t n) {
        _freept[7] = (char)n;
        n >>= 8;
        _freept[6] = (char)n;
        n >>= 8;
        _freept[5] = (char)n;
        n >>= 8;
        _freept[4] = (char)n;
        n >>= 8;
        _freept[3] = (char)n;
        n >>= 8;
        _freept[2] = (char)n;
        n >>= 8;
        _freept[1] = (char)n;
        n >>= 8;
        _freept[0] = (char)n;
        _freept += 8;
    }

    /**
     * Read an 8-bit unsigned integer from this buffer.
     *
     * @return the integer that has been read.
     **/
    uint8_t ReadInt8() { return (unsigned char)(*_datapt++); }

    /**
     * Read a 16-bit unsigned integer from this buffer.
     *
     * @return the integer that has been read.
     **/
    uint16_t ReadInt16() {
        unsigned char* tmp = (unsigned char*)(_datapt);
        _datapt += 2;
        return ((*tmp << 8) + *(tmp + 1));
    }

    /**
     * Read a 16-bit unsigned integer stored in reverse internet order
     * from this buffer.
     *
     * @return the integer that has been read.
     **/
    uint16_t ReadInt16Reverse() {
        unsigned char* tmp = (unsigned char*)(_datapt);
        _datapt += 2;
        return ((*(tmp + 1) << 8) + *tmp);
    }

    /**
     * Read a 32-bit unsigned integer from this buffer.
     *
     * @return the integer that has been read.
     **/
    uint32_t ReadInt32() {
        unsigned char* tmp = (unsigned char*)(_datapt);
        _datapt += 4;
        return ((((((uint32_t)(*tmp << 8) + *(tmp + 1)) << 8) + *(tmp + 2)) << 8) + *(tmp + 3));
    }

    /**
     * Read a 32-bit unsigned integer stored in reverse internet order
     * from this buffer.
     *
     * @return the integer that has been read.
     **/
    uint32_t ReadInt32Reverse() {
        unsigned char* tmp = (unsigned char*)(_datapt);
        _datapt += 4;
        return ((((((uint32_t)(*(tmp + 3) << 8) + *(tmp + 2)) << 8) + *(tmp + 1)) << 8) + *tmp);
    }

    /**
     * Read a 64-bit unsigned integer from this buffer.
     *
     * @return the integer that has been read.
     **/
    uint64_t ReadInt64() {
        unsigned char* tmp = (unsigned char*)(_datapt);
        _datapt += 8;
        return ((((((((((((((uint64_t)(*tmp << 8) + *(tmp + 1)) << 8) + *(tmp + 2)) << 8) + *(tmp + 3)) << 8) +
                      *(tmp + 4))
                     << 8) +
                    *(tmp + 5))
                   << 8) +
                  *(tmp + 6))
                 << 8) +
                *(tmp + 7));
    }

    /**
     * Read a 64-bit unsigned integer stored in reverse internet order
     * from this buffer.
     *
     * @return the integer that has been read.
     **/
    uint64_t ReadInt64Reverse() {
        unsigned char* tmp = (unsigned char*)(_datapt);
        _datapt += 8;
        return ((((((((((((((uint64_t)(*(tmp + 7) << 8) + *(tmp + 6)) << 8) + *(tmp + 5)) << 8) + *(tmp + 4)) << 8) +
                      *(tmp + 3))
                     << 8) +
                    *(tmp + 2))
                   << 8) +
                  *(tmp + 1))
                 << 8) +
                *tmp);
    }

    /**
     * Peek at an 8-bit unsigned integer in this buffer. Unlike a read
     * operation, this will not modify the buffer.
     *
     * @param offset offset of the integer to access.
     * @return value of the accessed integer.
     **/
    uint8_t PeekInt8(uint32_t offset) {
        assert(GetDataLen() >= offset + 1);
        return (uint8_t)*(_datapt + offset);
    }

    /**
     * Peek at a 16-bit unsigned integer in this buffer. Unlike a read
     * operation, this will not modify the buffer.
     *
     * @param offset offset of the integer to access.
     * @return value of the accessed integer.
     **/
    uint16_t PeekInt16(uint32_t offset) {
        assert(GetDataLen() >= offset + 2);
        unsigned char* tmp = (unsigned char*)(_datapt + offset);
        return (uint16_t)((*tmp << 8) + *(tmp + 1));
    }

    /**
     * Peek at a 16-bit unsigned integer stored in reverse internet
     * order in this buffer. Unlike a read operation, this will not
     * modify the buffer.
     *
     * @param offset offset of the integer to access.
     * @return value of the accessed integer.
     **/
    uint16_t PeekInt16Reverse(uint32_t offset) {
        assert(GetDataLen() >= offset + 2);
        unsigned char* tmp = (unsigned char*)(_datapt + offset);
        return (uint16_t)((*(tmp + 1) << 8) + *tmp);
    }

    /**
     * Peek at a 32-bit unsigned integer in this buffer. Unlike a read
     * operation, this will not modify the buffer.
     *
     * @param offset offset of the integer to access.
     * @return value of the accessed integer.
     **/
    uint32_t PeekInt32(uint32_t offset) {
        assert(GetDataLen() >= offset + 4);
        unsigned char* tmp = (unsigned char*)(_datapt + offset);
        return ((((((uint32_t)(*tmp << 8) + *(tmp + 1)) << 8) + *(tmp + 2)) << 8) + *(tmp + 3));
    }

    /**
     * Peek at a 32-bit unsigned integer stored in reverse internet
     * order in this buffer. Unlike a read operation, this will not
     * modify the buffer.
     *
     * @param offset offset of the integer to access.
     * @return value of the accessed integer.
     **/
    uint32_t PeekInt32Reverse(uint32_t offset) {
        assert(GetDataLen() >= offset + 4);
        unsigned char* tmp = (unsigned char*)(_datapt + offset);
        return ((((((uint32_t)(*(tmp + 3) << 8) + *(tmp + 2)) << 8) + *(tmp + 1)) << 8) + *tmp);
    }

    /**
     * Peek at a 64-bit unsigned integer in this buffer. Unlike a read
     * operation, this will not modify the buffer.
     *
     * @param offset offset of the integer to access.
     * @return value of the accessed integer.
     **/
    uint64_t PeekInt64(uint32_t offset) {
        assert(GetDataLen() >= offset + 8);
        unsigned char* tmp = (unsigned char*)(_datapt + offset);
        return ((((((((((((((uint64_t)(*tmp << 8) + *(tmp + 1)) << 8) + *(tmp + 2)) << 8) + *(tmp + 3)) << 8) +
                      *(tmp + 4))
                     << 8) +
                    *(tmp + 5))
                   << 8) +
                  *(tmp + 6))
                 << 8) +
                *(tmp + 7));
    }

    /**
     * Peek at a 64-bit unsigned integer stored in reverse internet
     * order in this buffer. Unlike a read operation, this will not
     * modify the buffer.
     *
     * @param offset offset of the integer to access.
     * @return value of the accessed integer.
     **/
    uint64_t PeekInt64Reverse(uint32_t offset) {
        assert(GetDataLen() >= offset + 8);
        unsigned char* tmp = (unsigned char*)(_datapt + offset);
        return ((((((((((((((uint64_t)(*(tmp + 7) << 8) + *(tmp + 6)) << 8) + *(tmp + 5)) << 8) + *(tmp + 4)) << 8) +
                      *(tmp + 3))
                     << 8) +
                    *(tmp + 2))
                   << 8) +
                  *(tmp + 1))
                 << 8) +
                *tmp);
    }

    /**
     * Write bytes to this buffer.
     *
     * @param src source byte buffer.
     * @param len number of bytes to write.
     **/
    void WriteBytes(const void* src, uint32_t len) {
        if (len != 0) [[likely]] {
            EnsureFree(len);
            memcpy(_freept, src, len);
            _freept += len;
        }
    }

    /**
     * Write bytes to this buffer. Skip checking for free space.
     *
     * @param src source byte buffer.
     * @param len number of bytes to write.
     **/
    void WriteBytesFast(const void* src, uint32_t len) {
        if (len != 0) [[likely]] {
            memcpy(_freept, src, len);
            _freept += len;
        }
    }

    /**
     * Read bytes from this buffer.
     *
     * @param dst destination byte buffer.
     * @param len number of bytes to read.
     **/
    void ReadBytes(void* dst, uint32_t len) {
        if (len != 0) [[likely]] {
            memcpy(dst, _datapt, len);
            _datapt += len;
        }
    }

    /**
     * Peek at bytes in this buffer. Unlike a read operation, this will
     * not modify the buffer.
     *
     * @param dst destination byte buffer.
     * @param len number of bytes to extract.
     * @param offset byte offset into the buffer.
     **/
    void PeekBytes(void* dst, uint32_t len, uint32_t offset) {
        if (len != 0) [[likely]] {
            assert(_freept >= _datapt + offset + len);
            memcpy(dst, _datapt + offset, len);
        }
    }

    /**
     * Check if the data stored in this buffer equals the data stored in
     * another buffer.
     *
     * @return true(equal)/false(not equal)
     * @param other the other buffer.
     **/
    bool Equals(FNET_DataBuffer* other);

    /**
     * Print a human-readable representation of this buffer to
     * stdout. This method may be used for debugging purposes.
     **/
    void HexDump();

    /**
     * Run some asserts to verify that this databuffer is in a legal
     * state.
     **/
    void AssertValid() {
        assert(_bufstart <= _datapt);
        assert(_datapt <= _freept);
        assert(_freept <= _bufend);
    }

    void resetIfEmpty() {
        if (GetDataLen() == 0) {
            Clear();
        }
    }
};
