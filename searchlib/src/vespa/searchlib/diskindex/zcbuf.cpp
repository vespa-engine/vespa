// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zcbuf.h"
#include <cstdlib>
#include <cstring>

namespace search::diskindex {

ZcBuf::ZcBuf()
    : _valI(nullptr),
      _valE(nullptr),
      _mallocStart(nullptr),
      _mallocSize(0)
{
}

ZcBuf::~ZcBuf()
{
    free(_mallocStart);
}

void
ZcBuf::clearReserve(size_t reserveSize)
{
    if (reserveSize + zcSlack() > _mallocSize) {
        size_t newSize = _mallocSize * 2;
        if (newSize < 16) {
            newSize = 16;
        }
        while (newSize < reserveSize + zcSlack()) {
            newSize *= 2;
        }
        uint8_t *newBuf = static_cast<uint8_t *>(malloc(newSize));
        free(_mallocStart);
        _mallocStart = newBuf;
        _mallocSize = newSize;
    }
    _valE = _mallocStart + _mallocSize - zcSlack();
    _valI = _mallocStart;
}


void
ZcBuf::expand()
{
    size_t newSize = _mallocSize * 2;
    size_t oldSize = size();
    if (newSize < 16) {
        newSize = 16;
    }

    uint8_t *newBuf = static_cast<uint8_t *>(malloc(newSize));

    if (oldSize > 0) {
        memcpy(newBuf, _mallocStart, oldSize);
    }
    free(_mallocStart);
    _mallocStart = newBuf;
    _mallocSize = newSize;
    _valI = _mallocStart + oldSize;
    _valE = _mallocStart + newSize - zcSlack();
}

}
