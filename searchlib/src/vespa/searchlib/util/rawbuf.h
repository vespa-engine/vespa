// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/hitrank.h>
#include <cstdint>
#include <sys/types.h>

class FastOS_FileInterface;

namespace search {
/**
 * A buffer with an input point and an output point.  The space
 * is dynamically allocated by the constructor, and can be extended
 * when needed.  Buffer contents may be moved around when there is
 * insufficient room.
 */

class RawBuf
{
private:
    RawBuf(const RawBuf &);
    RawBuf& operator=(const RawBuf &);

    char* _bufStart;        // ref. to start of buffer (don't move this!)
    char* _bufEnd;          // ref. to byte after last in buffer (don't mo)
    char* _bufFillPos;      // ref. to byte where next should be put in
    char* _bufDrainPos;     // ref. to next byte to take out of buffer
    char* _initialBufStart;
    size_t _initialSize;

    void ensureSizeInternal(size_t size);
public:

    RawBuf(char *start, size_t size);// Initially use provided buffer
    RawBuf(size_t size);    // malloc-s given size, assigns to _bufStart
    ~RawBuf();      // Frees _bufStart, i.e. the char[].

    void    operator+=(const char *src);
    void    operator+=(const RawBuf& buffer);
    bool    operator==(const RawBuf &buffer) const;
    void    addNum(size_t num, size_t fieldw, char fill);
    void    addNum32(int32_t num, size_t fieldw, char fill);
    void    addNum64(int64_t num, size_t fieldw, char fill);

    void    addHitRank(HitRank num);
    void    addSignedHitRank(SignedHitRank num);

    void    append(const void *data, size_t len);
    void    append(uint8_t byte);
    void    appendLong(uint64_t n);
    void    appendCompressedPositiveNumber(uint64_t n);
    void    appendCompressedNumber(int64_t n);
    bool    IsEmpty();  // Return whether all written.
    void    expandBuf(size_t needlen);
    size_t      GetFreeLen() const { return _bufEnd - _bufFillPos; }
    size_t      GetDrainLen() const { return _bufDrainPos - _bufStart; }
    const char *GetDrainPos() const { return _bufDrainPos; }
    const char *GetFillPos() const { return _bufFillPos; }
    char *      GetWritableFillPos() const { return _bufFillPos; }
    char *      GetWritableFillPos(size_t len) { preAlloc(len); return _bufFillPos; }
    char *      GetWritableDrainPos(size_t offset) { return _bufDrainPos + offset; }
    void    truncate(size_t offset) { _bufFillPos = _bufDrainPos + offset; }
    void    preAlloc(size_t len);   // Ensure room for 'len' more bytes.
    size_t  readFile(FastOS_FileInterface &file, size_t maxlen);
    void    reset() { _bufDrainPos = _bufFillPos = _bufStart; }
    void    Compact();
    void    Reuse();
    size_t  GetUsedAndDrainLen() const { return _bufFillPos - _bufStart; }
    size_t  GetUsedLen() const { return _bufFillPos - _bufDrainPos; }
    void    Drain(size_t len);  // Adjust drain pos.
    void    Fill(size_t len) { _bufFillPos += len; }

    void ensureSize(size_t size) {
        if (static_cast<size_t>(_bufEnd - _bufFillPos) < size) {
            ensureSizeInternal(size);
        }
    }

    /**
     * Convert from interNet highendian order at 'src', to unsigned integers
     */
    static uint16_t InetTo16(const unsigned char *src) {
        return (static_cast<uint16_t>(*src) << 8) + *(src + 1);
    };
    static uint16_t InetTo16(const char* src) {
        return InetTo16(reinterpret_cast<const unsigned char *>(src));
    };
    static uint32_t InetTo32(const unsigned char* src) {
        return (((((static_cast<uint32_t>(*src) << 8) + *(src + 1)) << 8)
                 + *(src + 2)) << 8) + *(src + 3);
    };
    static uint32_t InetTo32(const char* src) {
        return InetTo32(reinterpret_cast<const unsigned char *>(src));
    };

    /**
     * Convert unsigned int.s 'src', to interNet highendian order, at 'dst'
     * or _bufFillPos.  Update or return ref to next char after those filled in.
     */
    static unsigned char* ToInet(uint16_t src, unsigned char* dst) {
        *(dst + 1) = static_cast<unsigned char>(src); // The least significant 8 bits
        src >>= 8;          //  of 'src' are stored.
        *dst     = static_cast<unsigned char>(src);
        return dst + 2;
    };
    void Put16ToInet(uint16_t src) {
        ensureSize(2);
        _bufFillPos = reinterpret_cast<char *>
                      (ToInet(src,
                              reinterpret_cast<unsigned char*>(_bufFillPos)));
    };
    static unsigned char* ToInet(uint32_t src, unsigned char* dst) {
        *(dst + 3) = src;       // The least significant 8 bits
        src >>= 8;          //  of 'src' are stored.
        *(dst + 2) = src;
        src >>= 8;
        *(dst + 1) = src;
        src >>= 8;
        *dst     = src;
        return dst + 4;
    };
    void PutToInet(uint32_t src) {
        ensureSize(4);
        _bufFillPos = reinterpret_cast<char *>
                      (ToInet(src,
                              reinterpret_cast<unsigned char*>(_bufFillPos)));
    };

    static unsigned char* ToInet(uint64_t src, unsigned char* dst) {
        ToInet(static_cast<uint32_t>(src >> 32), dst);
        ToInet(static_cast<uint32_t>(src & 0xffffffffull), dst + 4);
        return dst + 8;
    };
    void Put64ToInet(uint64_t src) {
        ensureSize(8);
        _bufFillPos = reinterpret_cast<char *>
                      (ToInet(src,
                              reinterpret_cast<unsigned char*>(_bufFillPos)));
    };

};

}
