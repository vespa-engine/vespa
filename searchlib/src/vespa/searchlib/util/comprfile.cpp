// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "comprfile.h"
#include <vespa/fastos/file.h>
#include <vespa/vespalib/util/size_literals.h>
#include <cassert>
#include <cstring>

namespace search {

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
    assert(cbuf.getComprBuf() != nullptr);

    bool isretryread = false;

 retry:
    if (decodeContext.lastChunk())
        return;         // Already reached end of file.
    int remainingUnits = decodeContext.remainingUnits();

    // There's a good amount of data here already.
    if (remainingUnits >
        static_cast<ssize_t>(ComprBuffer::minimumPadding()))  //FIX! Tune
        return;

    // Assert that file read offset is aligned on unit boundary
    assert((static_cast<size_t>(fileReadByteOffset) &
            (cbuf.getUnitSize() - 1)) == 0);
    // Get direct IO file alignment
    size_t fileDirectIOAlign = cbuf.getAligner().getDirectIOFileAlign();
    // calculate number of pad units before requested start
    int padBeforeUnits = static_cast<int>
                         (static_cast<size_t>(fileReadByteOffset) &
                          (fileDirectIOAlign - 1)) / cbuf.getUnitSize();
    // No padding before if at end of file.
    if (fileReadByteOffset >= fileSize)
        padBeforeUnits = 0;
    // Continuation reads starts at aligned boundary.
    assert(remainingUnits == 0 || padBeforeUnits == 0);

    if (readAll)
        stopOffset = fileSize << 3;
    else if (!isretryread) {
        stopOffset += 8 * cbuf.getUnitBitSize();    // XXX: Magic integer
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
    int64_t bufferBits = cbuf.getComprBufSize() * cbuf.getUnitBitSize();
    if (readBits > 0 && (bufferBits < readBits)) {
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
        memmove(reinterpret_cast<char *>(cbuf.getComprBuf()) -
                (remainingUnits + extraRemainingUnits) * cbuf.getUnitSize(),
                static_cast<const char *>(decodeContext.getUnitPtr()) -
                extraRemainingUnits * cbuf.getUnitSize(),
                (remainingUnits + extraRemainingUnits) * cbuf.getUnitSize());

    // Adjust file position to direct IO boundary if needed before read
    if (padBeforeUnits != 0) {
        fileReadByteOffset -= padBeforeUnits * cbuf.getUnitSize();
        file.SetPosition(fileReadByteOffset);
    }
    int readUnits0 = 0;
    if (readBits > 0)
        readUnits0 = static_cast<int>((readBits + cbuf.getUnitBitSize() - 1) /
                                      cbuf.getUnitBitSize());

    // Try to align end of read to an alignment boundary
    int readUnits = cbuf.getAligner().adjustElements(fileReadByteOffset /
            cbuf.getUnitSize(), readUnits0);
    if (readUnits < readUnits0)
        isMore = true;

    if (readUnits > 0) {
        int64_t padBytes = fileReadByteOffset +
                           static_cast<int64_t>(readUnits) * cbuf.getUnitSize() - fileSize;
        if (!isMore && padBytes > 0) {
            // Pad reading of file written with smaller unit size with
            // NUL bytes.
            file.ReadBuf(cbuf.getComprBuf(), readUnits * cbuf.getUnitSize() - padBytes);
            memset(reinterpret_cast<char *>(cbuf.getComprBuf()) +
                   readUnits * cbuf.getUnitSize() - padBytes,
                   0,
                   padBytes);
        } else
            file.ReadBuf(cbuf.getComprBuf(), readUnits * cbuf.getUnitSize());
    }
    // If at end of file then add units of zero bits as padding
    if (!isMore)
        memset(reinterpret_cast<char *>(cbuf.getComprBuf()) +
               readUnits * cbuf.getUnitSize(),
               0,
               cbuf.getUnitSize() * ComprBuffer::minimumPadding());

    assert(remainingUnits + readUnits >= 0);
    decodeContext.afterRead(reinterpret_cast<char *>(cbuf.getComprBuf()) +
                            (padBeforeUnits - remainingUnits) *
                            static_cast<int32_t>(cbuf.getUnitSize()),
                            (remainingUnits + readUnits - padBeforeUnits),
                            fileReadByteOffset +
                            readUnits * cbuf.getUnitSize(),
                            isMore);
    fileReadByteOffset += readUnits * cbuf.getUnitSize();
    if (!isretryread &&
        decodeContext.endOfChunk() &&
        isMore) {
        isretryread = true;
        goto retry;     // Alignment caused too short read
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
    pos *= cbuf.getUnitSize();
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
    assert(cbuf.getComprBuf() != nullptr);

    int chunkUsedUnits = encodeContext.getUsedUnits(cbuf.getComprBuf());

    if (chunkUsedUnits == 0)
        return;
    int chunkSizeNormalMax = encodeContext.getNormalMaxUnits(cbuf.getComprBuf());
    int chunksize = chunkUsedUnits;
    /*
     * Normally, only flush the normal buffer and copy the slack
     * after the buffer to the start of buffer.
     */
    if (!flushSlack && chunksize > chunkSizeNormalMax)
        chunksize = chunkSizeNormalMax;
    assert(static_cast<unsigned int>(chunksize) <= cbuf.getComprBufSize() ||
                 (flushSlack &&
                  static_cast<unsigned int>(chunksize) <= cbuf.getComprBufSize() +
                  ComprBuffer::minimumPadding()));
    file.WriteBuf(cbuf.getComprBuf(), cbuf.getUnitSize() * chunksize);

    int remainingUnits = chunkUsedUnits - chunksize;
    assert(remainingUnits == 0 ||
           (!flushSlack &&
            static_cast<unsigned int>(remainingUnits) <=
            ComprBuffer::minimumPadding()));
    // Copy any slack after buffer to the start of the buffer
    if (remainingUnits > 0)
        memmove(cbuf.getComprBuf(),
                reinterpret_cast<const char *>(cbuf.getComprBuf()) +
                chunksize * cbuf.getUnitSize(),
                cbuf.getUnitSize() * remainingUnits);

    fileWriteByteOffset += chunksize * cbuf.getUnitSize();
    encodeContext.afterWrite(cbuf, remainingUnits, fileWriteByteOffset);
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
      _file(nullptr)
{
}

ComprFileReadContext::
ComprFileReadContext(uint32_t unitSize)
    : ComprBuffer(unitSize),
      _decodeContext(nullptr),
      _fileSize(0),
      _fileReadByteOffset(0),
      _bitOffset(0),
      _stopOffset(0),
      _readAll(true),
      _file(nullptr)
{
}

ComprFileReadContext::~ComprFileReadContext() = default;

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
ComprFileReadContext::allocComprBuf(unsigned int comprBufSize, size_t preferredFileAlignment)
{
    ComprBuffer::allocComprBuf(comprBufSize, preferredFileAlignment, _file, true);
}

void
ComprFileReadContext::referenceWriteContext(const ComprFileWriteContext &rhs)
{
    ComprFileEncodeContext *e = rhs.getEncodeContext();
    ComprFileDecodeContext *d = getDecodeContext();

    assert(e != nullptr);
    int usedUnits = e->getUsedUnits(rhs.getComprBuf());
    assert(usedUnits >= 0);

    referenceComprBuf(rhs);
    setBufferEndFilePos(static_cast<uint64_t>(usedUnits) * getUnitSize());
    setFileSize(static_cast<uint64_t>(usedUnits) * getUnitSize());
    if (d != nullptr) {
        d->afterRead(getComprBuf(),
                     usedUnits,
                     static_cast<uint64_t>(usedUnits) * getUnitSize(),
                     false);
        d->setupBits(0);
        setBitOffset(-1);
        assert(d->getBitPosV() == 0);
    }
}

void
ComprFileReadContext::reference_compressed_buffer(void *buffer, size_t usedUnits)
{
    ComprFileDecodeContext *d = getDecodeContext();

    setComprBuf(buffer, usedUnits);
    setBufferEndFilePos(static_cast<uint64_t>(usedUnits) * getUnitSize());
    setFileSize(static_cast<uint64_t>(usedUnits) * getUnitSize());
    if (d != nullptr) {
        d->afterRead(getComprBuf(),
                     usedUnits,
                     static_cast<uint64_t>(usedUnits) * getUnitSize(),
                     false);
        d->setupBits(0);
        setBitOffset(-1);
        assert(d->getBitPosV() == 0);
    }
}

ComprFileWriteContext::
ComprFileWriteContext(ComprFileEncodeContext &encodeContext)
    : ComprBuffer(encodeContext.getUnitByteSize()),
      _encodeContext(&encodeContext),
      _file(nullptr),
      _fileWriteByteOffset(0)
{
}

ComprFileWriteContext::
ComprFileWriteContext(uint32_t unitSize)
    : ComprBuffer(unitSize),
      _encodeContext(nullptr),
      _file(nullptr),
      _fileWriteByteOffset(0)
{
}

ComprFileWriteContext::~ComprFileWriteContext() = default;

void
ComprFileWriteContext::writeComprBuffer(bool flushSlack)
{
    if (_file != nullptr) {
        search::ComprFileWriteBase::WriteComprBuffer(*_encodeContext,
                *this,
                *_file,
                _fileWriteByteOffset,
                flushSlack);
        return;
    }

    int chunkUsedUnits = _encodeContext->getUsedUnits(getComprBuf());
    int chunkSizeNormalMax = _encodeContext->getNormalMaxUnits(getComprBuf());

    if (chunkUsedUnits >= chunkSizeNormalMax) {
        int overflowUnits = chunkUsedUnits - chunkSizeNormalMax;
        expandComprBuf(overflowUnits);
    }

    _encodeContext->afterWrite(*this, chunkUsedUnits, 0);
}

std::pair<uint64_t *, size_t>
ComprFileWriteContext::grabComprBuffer(vespalib::alloc::Alloc & comprAlloc)
{
    assert(_file == nullptr);
    std::pair<uint64_t *, size_t> res =
        std::make_pair(getComprBuf(), _encodeContext->getUsedUnits(getComprBuf()));
    comprAlloc = stealComprBuf();
    return res;
}

void
ComprFileWriteContext::allocComprBuf(unsigned int comprBufSize, size_t preferredFileAlignment)
{
    ComprBuffer::allocComprBuf(comprBufSize, preferredFileAlignment, _file, false);
}

void
ComprFileWriteContext::allocComprBuf()
{
    allocComprBuf(32_Ki, 32_Ki);
}

}
