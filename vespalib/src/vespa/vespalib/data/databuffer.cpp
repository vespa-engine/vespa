// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "databuffer.h"
#include <algorithm>
#include <cstdio>

namespace vespalib {

namespace {
size_t padbefore(size_t alignment, const char *buf) {
    return (alignment - (size_t(buf) % alignment)) % alignment;
}
}

DataBuffer::DataBuffer(size_t len, size_t alignment, const Alloc & initial) noexcept
    : _alignment(alignment),
      _externalBuf(nullptr),
      _bufstart(nullptr),
      _bufend(nullptr),
      _datapt(nullptr),
      _freept(nullptr),
      _buffer(initial.create(0))
{
    assert(_alignment > 0);
    if (len > 0) {
        // avoid very small buffers for performance reasons:
        size_t bufsize = std::max(256ul, roundUp2inN(len + (_alignment - 1)));
        Alloc newBuf(initial.create(bufsize));
        _bufstart = static_cast<char *>(newBuf.get());
        _buffer.swap(newBuf);

        _datapt = _bufstart + padbefore(alignment, _bufstart);
        _freept = _datapt;
        _bufend = _bufstart + bufsize;
        assert(_bufstart != nullptr);
    }
}

DataBuffer::~DataBuffer() = default;

void
DataBuffer::moveFreeToData(size_t len)
{
    assert(getFreeLen() >= len);
    _freept += len;
}


void
DataBuffer::moveDeadToData(size_t len)
{
    assert(getDeadLen() >= len);
    _datapt -= len;
    if (_bufstart != _externalBuf) {
        assert(getDeadLen() >= padbefore(_alignment, _bufstart));  // Do not move ahead of alignment.
    }
}


void
DataBuffer::moveDataToFree(size_t len)
{
    assert(getDataLen() >= len);
    _freept -= len;
}


bool
DataBuffer::shrink(size_t newsize)
{
    if (getBufSize() <= newsize || getDataLen() > newsize) {
        return false;
    }
    char *newbuf = nullptr;
    char *newdata = nullptr;
    newsize += (_alignment - 1);
    Alloc newBuf(_buffer.create(newsize));
    if (newsize != 0) {
        newbuf = static_cast<char *>(newBuf.get());
        newdata = newbuf + padbefore(_alignment, newbuf);
        if (getDataLen() > 0) {
            memcpy(newdata, _datapt, getDataLen());
        }
    }
    _buffer.swap(newBuf);
    _bufstart = newbuf;
    _freept   = newdata + getDataLen();
    _datapt   = newdata;
    _bufend   = newbuf + newsize;
    return true;
}


void
DataBuffer::pack(size_t needbytes)
{
    needbytes += (_alignment - 1);
    size_t dataLen = getDataLen();

    if ((getDeadLen() + getFreeLen()) < needbytes ||
        (getDeadLen() + getFreeLen()) * 4 < dataLen)
    {
        size_t bufsize = std::max(256ul, roundUp2inN(needbytes+dataLen));
        Alloc newBuf(_buffer.create(bufsize));
        char *newbuf = static_cast<char *>(newBuf.get());
        char *newdata = newbuf + padbefore(_alignment, newbuf);
        if (dataLen > 0) {
            memcpy(newdata, _datapt, dataLen);
        }
        _bufstart = newbuf;
        _datapt   = newdata;
        _freept   = newdata + dataLen;
        _bufend   = newbuf + bufsize;
        _buffer.swap(newBuf);
    } else {
        char *datapt = _bufstart + padbefore(_alignment, _bufstart);
        if (dataLen > 0) {
            memmove(datapt, _datapt, dataLen);
        }
        _datapt = datapt;
        _freept = _datapt + dataLen;
    }
}


bool
DataBuffer::equals(DataBuffer *other)
{
    if (getDataLen() != other->getDataLen()) {
        return false;
    }
    if (getDataLen() == 0) {
        return true;
    }
    return memcmp(getData(), other->getData(), getDataLen()) == 0;
}


void
DataBuffer::hexDump()
{
    char *pt = _datapt;
    printf("*** DataBuffer HexDump BEGIN ***\n");
    uint32_t i = 0;
    while (pt < _freept) {
        printf("%x ", (unsigned char) *pt++);
        if ((++i % 16) == 0)
            printf("\n");
    }
    if ((i % 16) != 0)
        printf("\n");
    printf("*** DataBuffer HexDump END ***\n");
}


void
DataBuffer::swap(DataBuffer &other)
{
    _buffer.swap(other._buffer);
    std::swap(_alignment, other._alignment);
    std::swap(_externalBuf, other._externalBuf);
    std::swap(_bufstart, other._bufstart);
    std::swap(_bufend, other._bufend);
    std::swap(_datapt, other._datapt);
    std::swap(_freept, other._freept);
}

vespalib::alloc::Alloc
DataBuffer::stealBuffer() &&
{
    assert( ! referencesExternalData() );
    _externalBuf = nullptr;
    _bufstart = nullptr;
    _bufend = nullptr;
    _datapt = nullptr;
    _freept = nullptr;
    return std::move(_buffer);
}

bool
DataBuffer::referencesExternalData() const {
    return (_externalBuf == _bufstart) && (getBufSize() > 0);
}

} // namespace vespalib
