// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <cstddef>

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
    char* _bufStart;        // ref. to start of buffer (don't move this!)
    char* _bufEnd;          // ref. to byte after last in buffer (don't mo)
    char* _bufFillPos;      // ref. to byte where next should be put in
    char* _bufDrainPos;     // ref. to next byte to take out of buffer

    void ensureSizeInternal(size_t size);
    void    expandBuf(size_t needlen);
    /**
     * Convert unsigned int.s 'src', to interNet highendian order, at 'dst'
     * or _bufFillPos.  Update or return ref to next char after those filled in.
     */
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
    static unsigned char* ToInet(uint64_t src, unsigned char* dst) {
        ToInet(static_cast<uint32_t>(src >> 32), dst);
        ToInet(static_cast<uint32_t>(src & 0xffffffffull), dst + 4);
        return dst + 8;
    };
public:
    RawBuf(const RawBuf &) = delete;
    RawBuf& operator=(const RawBuf &) = delete;
    explicit RawBuf(size_t size);    // malloc-s given size, assigns to _bufStart
    ~RawBuf();      // Frees _bufStart, i.e. the char[].

    void    append(const void *data, size_t len);
    void    append(uint8_t byte);
    void    appendCompressedPositiveNumber(uint64_t n);
    void    appendCompressedNumber(int64_t n);
    size_t      GetFreeLen() const { return _bufEnd - _bufFillPos; }
    const char *GetDrainPos() const { return _bufDrainPos; }
    char *      GetWritableFillPos(size_t len) { preAlloc(len); return _bufFillPos; }
    void    preAlloc(size_t len);   // Ensure room for 'len' more bytes.
    void    reset() { _bufDrainPos = _bufFillPos = _bufStart; }
    size_t  GetUsedLen() const { return _bufFillPos - _bufDrainPos; }
    void    Fill(size_t len) { _bufFillPos += len; }

    void ensureSize(size_t size) {
        if (static_cast<size_t>(_bufEnd - _bufFillPos) < size) {
            ensureSizeInternal(size);
        }
    }

    void Put64ToInet(uint64_t src) {
        ensureSize(8);
        _bufFillPos = reinterpret_cast<char *>(ToInet(src,reinterpret_cast<unsigned char*>(_bufFillPos)));
    };


};

}
