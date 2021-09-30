// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "filealign.h"
#include <vespa/fastos/file.h>
#include <vespa/vespalib/util/size_literals.h>
#include <cassert>

namespace search {

namespace {

size_t
gcd(size_t a, size_t b)
{
    size_t remainder;

    for (;;) {
        remainder = a % b;
        if (remainder == 0)
            return b;
        a = b;
        b = remainder;
    }
}


size_t
getMinBlocking(size_t elementsize, size_t alignment)
{
    return alignment / gcd(alignment, elementsize);
}

}


FileAlign::FileAlign()
    : _directIOFileAlign(1),
      _preferredFileAlign(1),
      _minDirectIOSize(1),
      _minAlignedSize(1),
      _elemSize(1),
      _directIOMemAlign(1),
      _directio(false)
{ }


FileAlign::~FileAlign() = default;


size_t
FileAlign::adjustSize(int64_t offset, size_t size) const
{
    if (_directio && (offset & (_directIOFileAlign - 1)) != 0) {
        // Align end of IO to direct IO boundary
        assert(offset % _elemSize == 0);
        size_t maxSize = _minDirectIOSize - (offset % _minDirectIOSize);
        if (size > maxSize)
            size = maxSize;
    } else if ((offset & (_preferredFileAlign - 1)) != 0) {
        // Align end of IO to preferred boundary
        assert(offset % _elemSize == 0);
        size_t tailLen = (offset + size) % _minAlignedSize;
        if (tailLen < size)
            size -= tailLen;
    }
    assert(size % _elemSize == 0);
    return size;
}


size_t
FileAlign::adjustElements(int64_t eoffset, size_t esize) const
{
    return adjustSize(eoffset * _elemSize, esize * _elemSize) / _elemSize;
}


size_t
FileAlign::setupAlign(size_t elements,
                      size_t elemSize,
                      FastOS_FileInterface *file,
                      size_t preferredFileAlignment)
{
    size_t memoryAlignment;
    size_t transferGranularity;
    size_t transferMaximum;

    if (file != nullptr) {
        _directio = file->GetDirectIORestrictions(memoryAlignment, transferGranularity, transferMaximum);
    } else
        _directio = false;
    if (_directio) {
        _directIOFileAlign = transferGranularity;
        _directIOMemAlign = memoryAlignment;
        if (preferredFileAlignment < _directIOFileAlign)
            preferredFileAlignment = _directIOFileAlign;
    } else {
        _directIOFileAlign = 1;
        _directIOMemAlign = 1;
    }
    if (preferredFileAlignment < 4_Ki)
        preferredFileAlignment = 4_Ki;
    _preferredFileAlign = preferredFileAlignment;

    size_t minDirectIOElements = getMinBlocking(elemSize, _directIOFileAlign);
    size_t minAlignedElements = getMinBlocking(elemSize, _preferredFileAlign);

    if (elements % minAlignedElements != 0)
        elements += minAlignedElements - (elements % minAlignedElements);
    _minDirectIOSize = minDirectIOElements * elemSize;
    _minAlignedSize = minAlignedElements * elemSize;
    _elemSize = elemSize;
    return elements;
}

}
