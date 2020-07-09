// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rawbuf.h"
#include <vespa/vespalib/util/compress.h>
#include <vespa/fastos/file.h>
#include <cassert>
#include <cstring>

namespace search {

static inline size_t smin(size_t a, size_t b) { return (a < b) ? a : b; }

RawBuf::RawBuf(size_t size)
    : _bufStart(nullptr),
      _bufEnd(nullptr),
      _bufFillPos(nullptr),
      _bufDrainPos(nullptr),
      _initialBufStart(nullptr),
      _initialSize(size)
{
    if (size > 0) {
        _bufStart = static_cast<char *>(malloc(size));
    }
    _bufEnd = _bufStart + size;
    _bufDrainPos = _bufFillPos = _bufStart;
}


RawBuf::RawBuf(char *start, size_t size)
    : _bufStart(nullptr),
      _bufEnd(nullptr),
      _bufFillPos(nullptr),
      _bufDrainPos(nullptr),
      _initialBufStart(start),
      _initialSize(size)
{
    _bufStart = start;
    _bufEnd = _bufStart + size;
    _bufDrainPos = _bufFillPos = _bufStart;
}


RawBuf::~RawBuf()
{
    if (_bufStart != _initialBufStart)
        free(_bufStart);
}


/**
 * Allocate a new buffer at least as large as the parameter value,
 * move any content to the new and delete the old buffer.
 */
void
RawBuf::expandBuf(size_t needlen)
{
    size_t  size = (_bufEnd - _bufStart) * 2;
    if (size < 1)
        size = 2;
    needlen += _bufEnd - _bufStart;
    while (size < needlen)
        size *= 2;
    char*  nbuf = static_cast<char *>(malloc(size));
    if (_bufFillPos != _bufDrainPos)
        memcpy(nbuf, _bufDrainPos, _bufFillPos - _bufDrainPos);
    _bufFillPos = _bufFillPos - _bufDrainPos + nbuf;
    _bufDrainPos = nbuf;
    if (_bufStart != _initialBufStart)
        free(_bufStart);
    _bufStart = nbuf;
    _bufEnd = _bufStart + size;
}


/**
 * Put 'data' of 'len'gth into the buffer.  If insufficient room,
 * make the buffer larger.
 */
void
RawBuf::append(const void *data, size_t len)
{
    if (__builtin_expect(len != 0, true)) {
        ensureSize(len);
        memcpy(_bufFillPos, data, len);
        _bufFillPos += len;
    }
}

void
RawBuf::append(uint8_t byte)
{
    ensureSize(1);
    *_bufFillPos++ = byte;
}

void
RawBuf::appendCompressedPositiveNumber(uint64_t n)
{
    size_t len(vespalib::compress::Integer::compressedPositiveLength(n));
    ensureSize(len);
    _bufFillPos += vespalib::compress::Integer::compressPositive(n, _bufFillPos);
}

void
RawBuf::appendCompressedNumber(int64_t n)
{
    size_t len(vespalib::compress::Integer::compressedLength(n));
    ensureSize(len);
    _bufFillPos += vespalib::compress::Integer::compress(n, _bufFillPos);
}


/**
 * Has the entire contents of the buffer been used up, i.e. freed?
 */
bool
RawBuf::IsEmpty()
{
    return _bufFillPos == _bufDrainPos;
}


/**
 * Free 'len' bytes from the start of the contents.  (These
 * have presumably been written or read.)
 */
void
RawBuf::Drain(size_t len)
{
    _bufDrainPos += len;
    if (_bufDrainPos == _bufFillPos)
        reset();
}


/**
 * Compact any free space from the beginning of the buffer, by
 * copying the contents to the start of the buffer.
 * If the resulting buffer doesn't have room for 'len' more
 * bytes of contents, make it large enough.
 */
void
RawBuf::preAlloc(size_t len)
{
    size_t curfree = _bufEnd - _bufFillPos;
    if (curfree >= len)
        return;
    if (_bufEnd - _bufStart < len + _bufFillPos - _bufDrainPos) {
        expandBuf(len);
        assert(_bufEnd - _bufStart >= len + _bufFillPos - _bufDrainPos);
        curfree = _bufEnd - _bufFillPos;
        if (curfree >= len)
            return;
    }
    memmove(_bufStart, _bufDrainPos, _bufFillPos - _bufDrainPos);
    _bufFillPos -= (_bufDrainPos - _bufStart);
    _bufDrainPos = _bufStart;
    assert(static_cast<size_t>(_bufEnd -_bufFillPos) >= len);
}


void
RawBuf::Compact()
{
    if (_bufDrainPos == _bufStart)
        return;
    if (_bufFillPos != _bufDrainPos)
        memmove(_bufStart, _bufDrainPos, _bufFillPos - _bufDrainPos);
    _bufFillPos -= (_bufDrainPos - _bufStart);
    _bufDrainPos = _bufStart;
}


void
RawBuf::Reuse()
{
    if (static_cast<size_t>(_bufEnd - _bufStart) > _initialSize * 4) {
        free(_bufStart);
        if (_initialSize > 0) {
            if (_initialBufStart != nullptr)
                _bufStart = _initialBufStart;
            else
                _bufStart = static_cast<char *>(malloc(_initialSize));
            assert(_bufStart != nullptr);
        } else
            _bufStart = nullptr;
        _bufEnd = _bufStart + _initialSize;
    }
    _bufDrainPos = _bufFillPos = _bufStart;
}


void
RawBuf::operator+=(const char *src)
{
    while (*src) {
        char *cachedBufFillPos = _bufFillPos;
        const char *cachedBufEnd = _bufEnd;
        while (cachedBufFillPos < cachedBufEnd && *src)
            *cachedBufFillPos++ = *src++;
        _bufFillPos = cachedBufFillPos;
        if (_bufFillPos >= _bufEnd)
            expandBuf(1);
    }
}


void
RawBuf::operator+=(const RawBuf& buffer)
{
    size_t nbytes = buffer.GetUsedLen();
    if (nbytes == 0)
        return;

    while (GetFreeLen() < nbytes)
        expandBuf(nbytes);
    memcpy(_bufFillPos, buffer._bufDrainPos, nbytes);
    _bufFillPos += nbytes;
}


bool
RawBuf::operator==(const RawBuf &buffer)
{
    size_t nbytes = buffer.GetUsedLen();
    if (nbytes != GetUsedLen())
        return false;

    const char *p, *t;
    for (p=_bufDrainPos, t=buffer._bufDrainPos; p<_bufFillPos; p++, t++) {
        if (*p != *t)
            return false;
    }

    return true;
}

/**
 * Append the value of param 'num' to the buffer, as a decimal
 * number right adjusted in a field of width 'fieldw', remaining
 * space filled with 'fill' characters.
 */
void
RawBuf::addNum(size_t num, size_t fieldw, char fill)
{
    char buf1[20];
    char *p = buf1;
    do {
        *p++ = '0' + (num % 10);
        num /= 10;
    } while (num != 0);
    size_t plen = p - buf1;
    size_t wantlen = fieldw;
    if (plen > wantlen)
        wantlen = plen;
    if (_bufFillPos + wantlen >= _bufEnd)
        expandBuf(wantlen);
    char *cachedBufFillPos = _bufFillPos;
    while (plen < wantlen) {
        *cachedBufFillPos++ = fill;
        wantlen--;
    }
    while (p > buf1) {
        *cachedBufFillPos++ = *--p;
    }
    _bufFillPos = cachedBufFillPos;
}


void
RawBuf::addNum32(int32_t num, size_t fieldw, char fill)
{
    char buf1[11];
    uint32_t unum = num >= 0 ? num : -num;
    char *p = buf1;
    do {
        *p++ = '0' + (unum % 10);
        unum /= 10;
    } while (unum != 0);
    if (num < 0)
        *p++ = '-';
    size_t plen = p - buf1;
    size_t wantlen = fieldw;
    if (plen > wantlen)
        wantlen = plen;
    if (_bufFillPos + wantlen >= _bufEnd)
        expandBuf(wantlen);
    char *cachedBufFillPos = _bufFillPos;
    while (plen < wantlen) {
        *cachedBufFillPos++ = fill;
        wantlen--;
    }
    while (p > buf1) {
        *cachedBufFillPos++ = *--p;
    }
    _bufFillPos = cachedBufFillPos;
}



void
RawBuf::addNum64(int64_t num, size_t fieldw, char fill)
{
    char buf1[21];
    uint64_t unum = num >= 0 ? num : -num;
    char *p = buf1;
    do {
        *p++ = '0' + (unum % 10);
        unum /= 10;
    } while (unum != 0);
    if (num < 0)
        *p++ = '-';
    size_t plen = p - buf1;
    size_t wantlen = fieldw;
    if (plen > wantlen)
        wantlen = plen;
    if (_bufFillPos + wantlen >= _bufEnd)
        expandBuf(wantlen);
    char *cachedBufFillPos = _bufFillPos;
    while (plen < wantlen) {
        *cachedBufFillPos++ = fill;
        wantlen--;
    }
    while (p > buf1) {
        *cachedBufFillPos++ = *--p;
    }
    _bufFillPos = cachedBufFillPos;
}


void
RawBuf::addHitRank(HitRank num)
{
    char buf1[100];
    snprintf(buf1, sizeof(buf1), "%g", static_cast<double>(num));
    append(buf1, strlen(buf1));
}


void
RawBuf::addSignedHitRank(SignedHitRank num)
{
    char buf1[100];
    snprintf(buf1, sizeof(buf1), "%g", static_cast<double>(num));
    append(buf1, strlen(buf1));
}

/**
 * Read from the indicated file into the buffer, no more that the
 * given number of bytes and no more than will fit in the buffer.
 */
size_t
RawBuf::readFile(FastOS_FileInterface &file, size_t maxlen)
{
    size_t  got = file.Read(_bufFillPos, smin((_bufEnd - _bufFillPos), maxlen));
    if (got > 0)
        _bufFillPos += got;
    return got;
}

void
RawBuf::ensureSizeInternal(size_t size) {
    expandBuf(size);
    assert(static_cast<size_t>(_bufEnd - _bufFillPos) >= size);
}

}
