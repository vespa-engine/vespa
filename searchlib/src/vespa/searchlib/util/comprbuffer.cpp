// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "comprbuffer.h"
#include <vespa/fastos/file.h>
#include <cassert>
#include <cstring>

namespace search {

ComprBuffer::ComprBuffer(uint32_t unitSize)
    : _unitSize(unitSize),
      _padBefore(false),
      _comprBuf(nullptr),
      _comprBufSize(0),
      _comprAlloc()
{ }


ComprBuffer::~ComprBuffer()
{
    dropComprBuf();
}


void
ComprBuffer::dropComprBuf()
{
    _comprBuf = nullptr;
}


void
ComprBuffer::allocComprBuf(size_t comprBufSize,
                           size_t preferredFileAlignment,
                           FastOS_FileInterface *file,
                           bool padBefore)
{
    comprBufSize = _aligner.setupAlign(comprBufSize, _unitSize, file, preferredFileAlignment);
    _comprBufSize = comprBufSize;
    _padBefore = padBefore;
    allocComprBuf();
}

void
ComprBuffer::allocComprBuf()
{
    dropComprBuf();
    /*
     * Add padding after normal buffer, to allow buffer to be completely
     * full before normal flushes for encoding.  Any spillover into padding
     * area should be copied to start of buffer after write.  This allows
     * for better alignment of write operations since buffer writes can then
     * normally write full buffers.
     *
     * For read, the padding after normal buffer gives some slack for the
     * decoder prefetch at end of file.
     */
    size_t paddingAfter = minimumPadding() * _unitSize;
    size_t paddingBefore = 0;
    size_t memalign = FastOS_File::getMaxDirectIOMemAlign();
    if (_padBefore) {
        /*
         * Add padding before normal buffer, to allow last data at end of
         * buffer to be copied to the padding area before the normal buffer
         * prior to a full buffer read.  This allows for better alignment of
         * read operations since buffer reads can then normally read full
         * buffers.
         */
        paddingBefore = paddingAfter + 2 * _unitSize;

        if (paddingBefore < memalign)
            paddingBefore = memalign;
    }
    size_t fullpadding = paddingAfter + paddingBefore;
    size_t allocLen = _comprBufSize * _unitSize + fullpadding;
    _comprAlloc = Alloc::alloc_aligned(allocLen, memalign);
    void *alignedBuf = _comprAlloc.get();
    memset(alignedBuf, 0, allocLen);
    /*
     * Set pointer to the start of normal buffer, which should be properly
     * aligned in memory for direct IO.
     */
    _comprBuf = (static_cast<char *>(alignedBuf) + paddingBefore);
    _comprBufSize = (_comprAlloc.size() - fullpadding) / _unitSize;
}


void
ComprBuffer::expandComprBuf(uint32_t overflowUnits)
{
    size_t newSize = static_cast<size_t>(_comprBufSize) * 2;
    assert(static_cast<unsigned int>(newSize) == newSize);
    if (newSize < 16)
        newSize = 16;
    size_t paddingAfter = minimumPadding() * _unitSize;
    assert(overflowUnits <= minimumPadding());
    Alloc newBuf = Alloc::alloc(newSize * _unitSize + paddingAfter);
    size_t oldLen = (static_cast<size_t>(_comprBufSize) + overflowUnits) * _unitSize;
    if (oldLen > 0) {
        memcpy(newBuf.get(), _comprBuf, oldLen);
    }
    _comprAlloc = std::move(newBuf);
    _comprBuf = _comprAlloc.get();
    _comprBufSize = (_comprAlloc.size() - paddingAfter) / _unitSize;
}


void
ComprBuffer::referenceComprBuf(const ComprBuffer &rhs)
{
    _comprBuf = rhs._comprBuf;
    _comprBufSize = rhs._comprBufSize;
}

}
