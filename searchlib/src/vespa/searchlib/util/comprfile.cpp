// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2002-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#include "comprfile.h"
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/fastos/file.h>
#include <cassert>

namespace search {

using vespalib::nbostream;

void
ComprFileReadBase::ReadComprBuffer(uint64_t stopOffset,
                                   bool readAll,
                                   ComprFileDecodeContext &decodeContext,
                                   int &bitOffset,
                                   FastOS_FileInterface &file,
                                   uint64_t &fileReadByteOffset,
                                   uint64_t fileSize,
                                   ComprBuffer &cbuf)
{
    assert(cbuf._comprBuf != NULL);

    bool isretryread = false;

 retry:
    if (decodeContext.lastChunk())
        return;			// Already reached end of file.
    int remainingUnits = decodeContext.remainingUnits();

    // There's a good amount of data here already.
    if (remainingUnits >
        static_cast<ssize_t>(ComprBuffer::minimumPadding()))  //FIX! Tune
        return;

    // Assert that file read offset is aligned on unit boundary
    assert((static_cast<size_t>(fileReadByteOffset) &
            (cbuf._unitSize - 1)) == 0);
    // Get direct IO file alignment
    size_t fileDirectIOAlign = cbuf._aligner.getDirectIOFileAlign();
    // calculate number of pad units before requested start
    int padBeforeUnits = static_cast<int>
                         (static_cast<size_t>(fileReadByteOffset) &
                          (fileDirectIOAlign - 1)) / cbuf._unitSize;
    // No padding before if at end of file.
    if (fileReadByteOffset >= fileSize)
        padBeforeUnits = 0;
    // Continuation reads starts at aligned boundary.
    assert(remainingUnits == 0 || padBeforeUnits == 0);

    if (readAll)
        stopOffset = fileSize << 3;
    else if (!isretryread) {
        stopOffset += 8 * cbuf.getUnitBitSize();	// XXX: Magic integer
        // Realign stop offset to direct IO alignment boundary
        uint64_t fileDirectIOBitAlign =
            static_cast<uint64_t>(fileDirectIOAlign) << 3;
        if ((stopOffset & (fileDirectIOBitAlign - 1)) != 0)
            stopOffset += fileDirectIOBitAlign -
                          (stopOffset & (fileDirectIOBitAlign - 1));
    }

    bool isMore = true;
    if (stopOffset >= (fileSize << 3)) {
        stopOffset = fileSize << 3;
        isMore = false;
    }

    int64_t readBits = static_cast<int64_t>(stopOffset) -
                       (static_cast<int64_t>(fileReadByteOffset) << 3) +
                       padBeforeUnits * cbuf.getUnitBitSize();
    int64_t bufferBits = cbuf._comprBufSize * cbuf.getUnitBitSize();
    if (readBits > 0 && (bufferBits < readBits))
    {
        isMore = true;
        readBits = bufferBits;
    }

    int extraRemainingUnits = 0;
    if (bitOffset == -1) {
        // Ensure that compressed data for current position is still available
        // in buffer form.
        extraRemainingUnits = 2;
    }
    // Move remaining integers to padding area before start of buffer
    if (remainingUnits + extraRemainingUnits > 0)
        memmove(static_cast<char *>(cbuf._comprBuf) -
                (remainingUnits + extraRemainingUnits) * cbuf._unitSize,
                static_cast<const char *>(decodeContext.getUnitPtr()) -
                extraRemainingUnits * cbuf._unitSize,
                (remainingUnits + extraRemainingUnits) * cbuf._unitSize);

    // Adjust file position to direct IO boundary if needed before read
    if (padBeforeUnits != 0) {
        fileReadByteOffset -= padBeforeUnits * cbuf._unitSize;
        file.SetPosition(fileReadByteOffset);
    }
    int readUnits0 = 0;
    if (readBits > 0)
        readUnits0 = static_cast<int>((readBits + cbuf.getUnitBitSize() - 1) /
                                      cbuf.getUnitBitSize());

    // Try to align end of read to an alignment boundary
    int readUnits = cbuf._aligner.adjustElements(fileReadByteOffset /
            cbuf._unitSize, readUnits0);
    if (readUnits < readUnits0)
        isMore = true;

    if (readUnits > 0) {
        int64_t padBytes = fileReadByteOffset +
                           static_cast<int64_t>(readUnits) * cbuf._unitSize -
                           fileSize;
        if (!isMore && padBytes > 0) {
            // Pad reading of file written with smaller unit size with
            // NUL bytes.
            file.ReadBuf(cbuf._comprBuf, readUnits * cbuf._unitSize -
                         padBytes);
            memset(static_cast<char *>(cbuf._comprBuf) +
                   readUnits * cbuf._unitSize - padBytes,
                   0,
                   padBytes);
        } else
            file.ReadBuf(cbuf._comprBuf, readUnits * cbuf._unitSize);
    }
    // If at end of file then add units of zero bits as padding
    if (!isMore)
        memset(static_cast<char *>(cbuf._comprBuf) +
               readUnits * cbuf._unitSize,
               0,
               cbuf._unitSize * ComprBuffer::minimumPadding());

    assert(remainingUnits + readUnits >= 0);
    decodeContext.afterRead(static_cast<char *>(cbuf._comprBuf) +
                            (padBeforeUnits - remainingUnits) *
                            static_cast<int32_t>(cbuf._unitSize),
                            (remainingUnits + readUnits - padBeforeUnits),
                            fileReadByteOffset +
                            readUnits * cbuf._unitSize,
                            isMore);
    fileReadByteOffset += readUnits * cbuf._unitSize;
    if (!isretryread &&
        decodeContext.endOfChunk() &&
        isMore) {
        isretryread = true;
        goto retry;		// Alignment caused too short read
    }

    if (bitOffset != -1) {
        decodeContext.setupBits(bitOffset);
        bitOffset = -1;
    }

}


void
ComprFileReadBase::SetPosition(uint64_t newPosition,
                               uint64_t stopOffset,
                               bool readAll,
                               ComprFileDecodeContext &decodeContext,
                               int &bitOffset,
                               FastOS_FileInterface &file,
                               uint64_t &fileReadByteOffset,
                               uint64_t fileSize,
                               ComprBuffer &cbuf)
{
    int64_t pos;
    uint64_t oldPosition;

    oldPosition = decodeContext.getBitPos(bitOffset, fileReadByteOffset);
    assert(oldPosition == decodeContext.getBitPosV());
    if (newPosition == oldPosition)
        return;
    if (newPosition > oldPosition && newPosition <= (fileReadByteOffset << 3)) {
        size_t skip = newPosition - oldPosition;
        if (skip < 2 * cbuf.getUnitBitSize()) {
            // Cached bits might still be needed, just read and ignore bits
            if (decodeContext.endOfChunk())
                ReadComprBuffer(stopOffset,
                                readAll,
                                decodeContext,
                                bitOffset,
                                file,
                                fileReadByteOffset,
                                fileSize,
                                cbuf);
            decodeContext.skipBits(skip);
            assert(decodeContext.getBitPos(bitOffset,
                           fileReadByteOffset) == newPosition);
            assert(decodeContext.getBitPosV() == newPosition);
            return;
        }
        // Cached bits not needed, skip to new position in buffer
        size_t left = (fileReadByteOffset << 3) - newPosition;
        decodeContext.adjUnitPtr((left + cbuf.getUnitBitSize() - 1) /
                                 cbuf.getUnitBitSize());
        bitOffset = static_cast<int>
                    (static_cast<uint32_t>(newPosition) &
                     (cbuf.getUnitBitSize() - 1));
        // We might now be at end of chunk, read more if needed in order
        // for setupBits() to be safe.
        if (decodeContext.endOfChunk())
            ReadComprBuffer(stopOffset,
                            readAll,
                            decodeContext,
                            bitOffset,
                            file,
                            fileReadByteOffset,
                            fileSize,
                            cbuf);
        // Only call SetupBits() if ReadComprBuffer() didn't do it.
        if (bitOffset != -1) {
            decodeContext.setupBits(bitOffset);
            bitOffset = -1;
        }
        assert(decodeContext.getBitPos(bitOffset,
                                        fileReadByteOffset) == newPosition);
        assert(decodeContext.getBitPosV() == newPosition);
        return;
    }
    pos = newPosition / cbuf.getUnitBitSize();
    pos *= cbuf._unitSize;
    fileReadByteOffset = pos;
    bitOffset = static_cast<int>(static_cast<uint32_t>(newPosition) &
                                 (cbuf.getUnitBitSize() - 1));

    assert(pos <= static_cast<int64_t>(fileSize));

    file.SetPosition(pos);
    assert(pos == file.GetPosition());

    decodeContext.emptyBuffer(newPosition);
    assert(decodeContext.getBitPos(bitOffset,
                                   fileReadByteOffset) == newPosition);
    assert(decodeContext.getBitPosV() == newPosition);
}


void
ComprFileWriteBase::
WriteComprBuffer(ComprFileEncodeContext &encodeContext,
                 ComprBuffer &cbuf,
                 FastOS_FileInterface &file,
                 uint64_t &fileWriteByteOffset,
                 bool flushSlack)
{
    assert(cbuf._comprBuf != NULL);

    int chunkUsedUnits = encodeContext.getUsedUnits(cbuf._comprBuf);

    if (chunkUsedUnits == 0)
        return;
    int chunkSizeNormalMax = encodeContext.getNormalMaxUnits(cbuf._comprBuf);
    int chunksize = chunkUsedUnits;
    /*
     * Normally, only flush the normal buffer and copy the slack
     * after the buffer to the start of buffer.
     */
    if (!flushSlack && chunksize > chunkSizeNormalMax)
        chunksize = chunkSizeNormalMax;
    assert(static_cast<unsigned int>(chunksize) <= cbuf._comprBufSize ||
                 (flushSlack &&
                  static_cast<unsigned int>(chunksize) <= cbuf._comprBufSize +
                  ComprBuffer::minimumPadding()));
    file.WriteBuf(cbuf._comprBuf, cbuf._unitSize * chunksize);

    int remainingUnits = chunkUsedUnits - chunksize;
    assert(remainingUnits == 0 ||
           (!flushSlack &&
            static_cast<unsigned int>(remainingUnits) <=
            ComprBuffer::minimumPadding()));
    // Copy any slack after buffer to the start of the buffer
    if (remainingUnits > 0)
        memmove(cbuf._comprBuf,
                static_cast<char *>(cbuf._comprBuf) +
                chunksize * cbuf._unitSize,
                cbuf._unitSize * remainingUnits);

    fileWriteByteOffset += chunksize * cbuf._unitSize;
    encodeContext.afterWrite(cbuf,
                             remainingUnits,
                             fileWriteByteOffset);
}


ComprFileReadContext::
ComprFileReadContext(ComprFileDecodeContext &decodeContext)
    : ComprBuffer(decodeContext.getUnitByteSize()),
      _decodeContext(&decodeContext),
      _fileSize(0),
      _fileReadByteOffset(0),
      _bitOffset(0),
      _stopOffset(0),
      _readAll(true),
      _checkPointOffsetValid(false),
      _file(NULL),
      _checkPointOffset(0)
{
}


ComprFileReadContext::
ComprFileReadContext(uint32_t unitSize)
    : ComprBuffer(unitSize),
      _decodeContext(NULL),
      _fileSize(0),
      _fileReadByteOffset(0),
      _bitOffset(0),
      _stopOffset(0),
      _readAll(true),
      _checkPointOffsetValid(false),
      _file(NULL),
      _checkPointOffset(0)
{
}


ComprFileReadContext::~ComprFileReadContext()
{
}


void
ComprFileReadContext::readComprBuffer(uint64_t stopOffset, bool readAll)
{
    search::ComprFileReadBase::ReadComprBuffer(stopOffset,
            readAll,
            *_decodeContext,
            _bitOffset,
            *_file,
            _fileReadByteOffset,
            _fileSize,
            *this);
}


void
ComprFileReadContext::readComprBuffer()
{
    search::ComprFileReadBase::ReadComprBuffer(_stopOffset,
            _readAll,
            *_decodeContext,
            _bitOffset,
            *_file,
            _fileReadByteOffset,
            _fileSize,
            *this);
}


void
ComprFileReadContext::setPosition(uint64_t newPosition,
                                  uint64_t stopOffset,
                                  bool readAll)
{
    setStopOffset(stopOffset, readAll);
    search::ComprFileReadBase::SetPosition(newPosition,
            stopOffset,
            readAll,
            *_decodeContext,
            _bitOffset,
            *_file,
            _fileReadByteOffset,
            _fileSize,
            *this);
}


void
ComprFileReadContext::setPosition(uint64_t newPosition)
{
    search::ComprFileReadBase::SetPosition(newPosition,
            _stopOffset,
            _readAll,
            *_decodeContext,
            _bitOffset,
            *_file,
            _fileReadByteOffset,
            _fileSize,
            *this);
}


void
ComprFileReadContext::allocComprBuf(unsigned int comprBufSize,
                                    size_t preferredFileAlignment)
{
    ComprBuffer::allocComprBuf(comprBufSize, preferredFileAlignment,
                               _file, true);
}


void
ComprFileReadContext::referenceWriteContext(const ComprFileWriteContext &rhs)
{
    ComprFileEncodeContext *e = rhs.getEncodeContext();
    ComprFileDecodeContext *d = getDecodeContext();

    assert(e != NULL);
    int usedUnits = e->getUsedUnits(rhs._comprBuf);
    assert(usedUnits >= 0);

    referenceComprBuf(rhs);
    setBufferEndFilePos(static_cast<uint64_t>(usedUnits) * _unitSize);
    setFileSize(static_cast<uint64_t>(usedUnits) * _unitSize);
    if (d != NULL) {
        d->afterRead(_comprBuf,
                     usedUnits,
                     static_cast<uint64_t>(usedUnits) * _unitSize,
                     false);
        d->setupBits(0);
        setBitOffset(-1);
        assert(d->getBitPosV() == 0);
    }
}


void
ComprFileReadContext::copyWriteContext(const ComprFileWriteContext &rhs)
{
    ComprFileEncodeContext *e = rhs.getEncodeContext();
    ComprFileDecodeContext *d = getDecodeContext();

    assert(e != NULL);
    int usedUnits = e->getUsedUnits(rhs._comprBuf);
    assert(usedUnits >= 0);

    dropComprBuf();
    allocComprBuf(usedUnits, 32768);
    assert(_comprBufSize >= static_cast<unsigned int>(usedUnits));
    memcpy(_comprBuf, rhs._comprBuf,
           static_cast<size_t>(usedUnits) * _unitSize);
    setBufferEndFilePos(static_cast<uint64_t>(usedUnits) * _unitSize);
    setFileSize(static_cast<uint64_t>(usedUnits) * _unitSize);
    if (d != NULL) {
        d->afterRead(_comprBuf,
                     usedUnits,
                     static_cast<uint64_t>(usedUnits) * _unitSize,
                     false);
        d->setupBits(0);
        setBitOffset(-1);
        assert(d->getBitPosV() == 0);
    }
}


void
ComprFileReadContext::referenceReadContext(const ComprFileReadContext &rhs)
{
    ComprFileDecodeContext *d = getDecodeContext();

    int usedUnits = rhs.getBufferEndFilePos() / _unitSize;
    assert(usedUnits >= 0);
    assert(static_cast<uint64_t>(usedUnits) * _unitSize ==
           rhs.getBufferEndFilePos());

    referenceComprBuf(rhs);
    setBufferEndFilePos(static_cast<uint64_t>(usedUnits) * _unitSize);
    setFileSize(static_cast<uint64_t>(usedUnits) * _unitSize);
    if (d != NULL) {
        d->afterRead(_comprBuf,
                     usedUnits,
                     static_cast<uint64_t>(usedUnits) * _unitSize,
                     false);
        d->setupBits(0);
        setBitOffset(-1);
        assert(d->getBitPosV() == 0);
    }
}


void
ComprFileReadContext::copyReadContext(const ComprFileReadContext &rhs)
{
    ComprFileDecodeContext *d = getDecodeContext();

    int usedUnits = rhs.getBufferEndFilePos() / _unitSize;
    assert(usedUnits >= 0);
    assert(static_cast<uint64_t>(usedUnits) * _unitSize ==
           rhs.getBufferEndFilePos());

    dropComprBuf();
    allocComprBuf(usedUnits, 32768);
    assert(_comprBufSize >= static_cast<unsigned int>(usedUnits));
    memcpy(_comprBuf, rhs._comprBuf,
           static_cast<size_t>(usedUnits) * _unitSize);
    setBufferEndFilePos(static_cast<uint64_t>(usedUnits) * _unitSize);
    setFileSize(static_cast<uint64_t>(usedUnits) * _unitSize);
    if (d != NULL) {
        d->afterRead(_comprBuf,
                     usedUnits,
                     static_cast<uint64_t>(usedUnits) * _unitSize,
                     false);
        d->setupBits(0);
        setBitOffset(-1);
        assert(d->getBitPosV() == 0);
    }
}


void
ComprFileReadContext::checkPointWrite(nbostream &out)
{
    ComprBuffer::checkPointWrite(out);
    ComprFileDecodeContext &d = *_decodeContext;
    d.checkPointWrite(out);
    uint64_t bitOffset = d.getBitPosV();
    out << bitOffset;
}


void
ComprFileReadContext::checkPointRead(nbostream &in)
{
    ComprBuffer::checkPointRead(in);
    ComprFileDecodeContext &d = *_decodeContext;
    d.checkPointRead(in);
    in >> _checkPointOffset;	// Cannot seek until file is opened
    _checkPointOffsetValid = true;
}

ComprFileWriteContext::
ComprFileWriteContext(ComprFileEncodeContext &encodeContext)
    : ComprBuffer(encodeContext.getUnitByteSize()),
      _encodeContext(&encodeContext),
      _file(NULL),
      _fileWriteByteOffset(0)
{
}


ComprFileWriteContext::
ComprFileWriteContext(uint32_t unitSize)
    : ComprBuffer(unitSize),
      _encodeContext(NULL),
      _file(NULL),
      _fileWriteByteOffset(0)
{
}


ComprFileWriteContext::~ComprFileWriteContext()
{
}


void
ComprFileWriteContext::writeComprBuffer(bool flushSlack)
{
    if (_file != NULL) {
        search::ComprFileWriteBase::WriteComprBuffer(*_encodeContext,
                *this,
                *_file,
                _fileWriteByteOffset,
                flushSlack);
        return;
    }

    int chunkUsedUnits = _encodeContext->getUsedUnits(_comprBuf);
    int chunkSizeNormalMax = _encodeContext->getNormalMaxUnits(_comprBuf);

    if (chunkUsedUnits >= chunkSizeNormalMax) {
        int overflowUnits = chunkUsedUnits - chunkSizeNormalMax;
        expandComprBuf(overflowUnits);
    }

    _encodeContext->afterWrite(*this,
                               chunkUsedUnits,
                               0);
}


std::pair<void *, size_t>
ComprFileWriteContext::grabComprBuffer(void *&comprBufMalloc)
{
    assert(_file == NULL);
    std::pair<void *, size_t> res =
        std::make_pair(_comprBuf, _encodeContext->getUsedUnits(_comprBuf));
    comprBufMalloc = _comprBufMalloc;
    _comprBuf = _comprBufMalloc = NULL;
    _comprBufSize = 0;
    return res;
}


void
ComprFileWriteContext::allocComprBuf(unsigned int comprBufSize,
                                     size_t preferredFileAlignment)
{
    ComprBuffer::allocComprBuf(comprBufSize, preferredFileAlignment,
                               _file, false);
}


void
ComprFileWriteContext::allocComprBuf()
{
    allocComprBuf(32768, 32768);
}


void
ComprFileWriteContext::checkPointWrite(nbostream &out)
{
    ComprBuffer::checkPointWrite(out);
    ComprFileEncodeContext &e = *_encodeContext;
    uint64_t bufferStartFilePos = getBufferStartFilePos();
    uint64_t usedSize = e.getUsedUnits(_comprBuf) *
                        e.getUnitByteSize();
    out << bufferStartFilePos << usedSize;
    e.checkPointWrite(out);
    if (usedSize != 0) {
        out.write(_comprBuf, usedSize);
    }
    uint64_t bitOffset = e.getBitPosV();
    out << bitOffset;
}


void
ComprFileWriteContext::checkPointRead(nbostream &in)
{
    ComprBuffer::checkPointRead(in);
    ComprFileEncodeContext &e = *_encodeContext;
    uint64_t bufferStartFilePos = 0;
    uint64_t usedSize = 0;
    in >> bufferStartFilePos >> usedSize;
    e.checkPointRead(in);
    if (usedSize != 0) {
        assert((usedSize % e.getUnitByteSize()) == 0);
        assert(_comprBufSize >= usedSize / e.getUnitByteSize());
        in.read(_comprBuf, usedSize);
    }
    setBufferStartFilePos(bufferStartFilePos);
    e.afterWrite(*this, usedSize / e.getUnitByteSize(), bufferStartFilePos);
    uint64_t bitOffset = 0;
    in >> bitOffset;
    uint64_t writeOffset = e.getBitPosV();
    assert(bitOffset == writeOffset);
    (void) writeOffset;
}


}
