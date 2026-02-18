// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "databuffer.h"

#include <cstdio>

FNET_DataBuffer::FNET_DataBuffer(uint32_t len)
    : _bufstart(nullptr), _bufend(nullptr), _datapt(nullptr), _freept(nullptr) {
    if (len > 0 && len < 256)
        len = 256;

    if (len > 0) {
        Alloc::alloc(len).swap(_ownedBuf);
        _bufstart = static_cast<char*>(_ownedBuf.get());
        assert(_bufstart != nullptr);
    } else { // len == 0
        _bufstart = nullptr;
    }
    _bufend = _bufstart + len;
    _datapt = _freept = _bufstart;
}

FNET_DataBuffer::FNET_DataBuffer(char* buf, uint32_t len)
    : _bufstart(buf), _bufend(buf + len), _datapt(_bufstart), _freept(_bufstart) {}

FNET_DataBuffer::~FNET_DataBuffer() {}

void FNET_DataBuffer::FreeToData(uint32_t len) {
    assert(GetFreeLen() >= len);
    _freept += len;
}

void FNET_DataBuffer::DeadToData(uint32_t len) {
    assert(GetDeadLen() >= len);
    _datapt -= len;
}

void FNET_DataBuffer::DataToFree(uint32_t len) {
    assert(GetDataLen() >= len);
    _freept -= len;
}

bool FNET_DataBuffer::Shrink(uint32_t newsize) {
    const auto data_len = GetDataLen();
    if (GetBufSize() <= newsize || data_len > newsize) {
        return false;
    }

    Alloc newBuf(Alloc::alloc(newsize));
    if (data_len > 0) [[likely]] {
        memcpy(newBuf.get(), _datapt, data_len);
    }
    _ownedBuf.swap(newBuf);
    _bufstart = static_cast<char*>(_ownedBuf.get());
    _freept = _bufstart + data_len;
    _datapt = _bufstart;
    _bufend = _bufstart + newsize;
    return true;
}

void FNET_DataBuffer::Pack(uint32_t needbytes) {
    if ((GetDeadLen() + GetFreeLen()) < needbytes || (GetDeadLen() + GetFreeLen()) * 4 < GetDataLen()) {
        uint32_t bufsize = GetBufSize() * 2;
        if (bufsize < 256) {
            bufsize = 256;
        }
        while (bufsize - GetDataLen() < needbytes)
            bufsize *= 2;

        Alloc newBuf(Alloc::alloc(bufsize));
        if (_datapt != nullptr) [[likely]] {
            memcpy(newBuf.get(), _datapt, GetDataLen());
        }
        _ownedBuf.swap(newBuf);
        _bufstart = static_cast<char*>(_ownedBuf.get());
        _freept = _bufstart + GetDataLen();
        _datapt = _bufstart;
        _bufend = _bufstart + bufsize;
    } else {
        memmove(_bufstart, _datapt, GetDataLen());
        _freept = _bufstart + GetDataLen();
        _datapt = _bufstart;
    }
}

bool FNET_DataBuffer::Equals(FNET_DataBuffer* other) {
    if (GetDataLen() != other->GetDataLen())
        return false;
    return memcmp(GetData(), other->GetData(), GetDataLen()) == 0;
}

void FNET_DataBuffer::HexDump() {
    char* pt = _datapt;
    printf("*** FNET_DataBuffer HexDump BEGIN ***\n");
    uint32_t i = 0;
    while (pt < _freept) {
        printf("%x ", (unsigned char)*pt++);
        if ((++i % 16) == 0)
            printf("\n");
    }
    if ((i % 16) != 0)
        printf("\n");
    printf("*** FNET_DataBuffer HexDump END ***\n");
}
