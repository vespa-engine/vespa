// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include "databuffer.h"
#include <vespa/log/log.h>
LOG_SETUP(".databuffer");

namespace vespalib {

namespace {
size_t padbefore(size_t alignment, const char *buf) {
    return (alignment - (size_t(buf) % alignment)) % alignment;
}
}

template <typename T>
DataBufferT<T>::DataBufferT(size_t len, size_t alignment)
    : _alignment(alignment),
      _externalBuf(NULL),
      _bufstart(NULL),
      _bufend(NULL),
      _datapt(NULL),
      _freept(NULL),
      _buffer()
{
    assert(_alignment > 0);
    if (len > 0) {
        // avoid very small buffers for performance reasons:
        size_t bufsize = std::max(256ul, roundUp2inN(len + (_alignment - 1)));
        T newBuf(bufsize);
        _bufstart = static_cast<char *>(newBuf.get());
        _buffer.swap(newBuf);

        _datapt = _bufstart + padbefore(alignment, _bufstart);
        _freept = _datapt;
        _bufend = _bufstart + bufsize;
        assert(_bufstart != NULL);
    }
}


template <typename T>
void
DataBufferT<T>::moveFreeToData(size_t len)
{
    assert(getFreeLen() >= len);
    _freept += len;
}


template <typename T>
void
DataBufferT<T>::moveDeadToData(size_t len)
{
    assert(getDeadLen() >= len);
    _datapt -= len;
    if (_bufstart != _externalBuf) {
        assert(getDeadLen() >= padbefore(_alignment, _bufstart));  // Do not move ahead of alignment.
    }
}


template <typename T>
void
DataBufferT<T>::moveDataToFree(size_t len)
{
    assert(getDataLen() >= len);
    _freept -= len;
}


template <typename T>
bool
DataBufferT<T>::shrink(size_t newsize)
{
    if (getBufSize() <= newsize || getDataLen() > newsize) {
        return false;
    }
    char *newbuf = NULL;
    char *newdata = NULL;
    newsize += (_alignment - 1);
    T newBuf(newsize);
    if (newsize != 0) {
        newbuf = static_cast<char *>(newBuf.get());
        newdata = newbuf + padbefore(_alignment, newbuf);
        memcpy(newdata, _datapt, getDataLen());
    }
    _buffer.swap(newBuf);
    _bufstart = newbuf;
    _freept   = newdata + getDataLen();
    _datapt   = newdata;
    _bufend   = newbuf + newsize;
    return true;
}


template <typename T>
void
DataBufferT<T>::pack(size_t needbytes)
{
    needbytes += (_alignment - 1);
    size_t dataLen = getDataLen();

    if ((getDeadLen() + getFreeLen()) < needbytes ||
        (getDeadLen() + getFreeLen()) * 4 < dataLen)
    {
        size_t bufsize = std::max(256ul, roundUp2inN(needbytes+dataLen));
        T newBuf(bufsize);
        char *newbuf = static_cast<char *>(newBuf.get());
        char *newdata = newbuf + padbefore(_alignment, newbuf);
        memcpy(newdata, _datapt, dataLen);
        _bufstart = newbuf;
        _datapt   = newdata;
        _freept   = newdata + dataLen;
        _bufend   = newbuf + bufsize;
        _buffer.swap(newBuf);
    } else {
        char *datapt = _bufstart + padbefore(_alignment, _bufstart);
        memmove(datapt, _datapt, dataLen);
        _datapt = datapt;
        _freept = _datapt + dataLen;
    }
}


template <typename T>
bool
DataBufferT<T>::equals(DataBufferT *other)
{
    if (getDataLen() != other->getDataLen())
        return false;
    return memcmp(getData(), other->getData(), getDataLen()) == 0;
}


template <typename T>
void
DataBufferT<T>::hexDump()
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


template <typename T>
void
DataBufferT<T>::swap(DataBufferT &other)
{
    _buffer.swap(other._buffer);
    std::swap(_alignment, other._alignment);
    std::swap(_externalBuf, other._externalBuf);
    std::swap(_bufstart, other._bufstart);
    std::swap(_bufend, other._bufend);
    std::swap(_datapt, other._datapt);
    std::swap(_freept, other._freept);
}

template <typename T>
T
DataBufferT<T>::stealBuffer()
{
    assert( ! referencesExternalData() );
    _externalBuf = nullptr;
    _bufstart = nullptr;
    _bufend = nullptr;
    _datapt = nullptr;
    _freept = nullptr;
    return std::move(_buffer);
}

template <typename T>
bool
DataBufferT<T>::referencesExternalData() const {
    return (_externalBuf == _bufstart) && (getBufSize() > 0);
}

template class DataBufferT<HeapAlloc>;
template class DataBufferT<MMapAlloc>;
template class DataBufferT<DefaultAlloc>;


} // namespace vespalib
