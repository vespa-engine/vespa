// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "rawbuf.h"
#include <vespa/vespalib/util/compress.h>
#include <cassert>
#include <cstring>
#include <cstdlib>

namespace search {

RawBuf::RawBuf(size_t size)
    : _bufStart(nullptr),
      _bufEnd(nullptr),
      _bufFillPos(nullptr),
      _bufDrainPos(nullptr)
{
    if (size > 0) {
        _bufStart = static_cast<char *>(malloc(size));
    }
    _bufEnd = _bufStart + size;
    _bufDrainPos = _bufFillPos = _bufStart;
}

RawBuf::~RawBuf()
{
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
RawBuf::ensureSizeInternal(size_t size) {
    expandBuf(size);
    assert(static_cast<size_t>(_bufEnd - _bufFillPos) >= size);
}

}
