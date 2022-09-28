// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "comprbuffer.h"
#include <utility>

class FastOS_FileInterface;

namespace search {

class ComprFileWriteContext;

class ComprFileDecodeContext
{
public:
    virtual ~ComprFileDecodeContext() = default;

    /**
     *
     * Check if the chunk referenced by the decode context was the
     * last chunk in the file (e.g. _valE > _realValE)
     */
    virtual bool lastChunk() const = 0;

    /**
     * Check if we're at the end of the current chunk (e.g. _valI >= _valE)
     */
    virtual bool endOfChunk() const = 0;

    /**
     * Get remaining units in buffer (e.g. _realValE - _valI)
     */

    virtual int32_t remainingUnits() const = 0;

    /**
     * Get unit ptr (e.g. _valI) from decode context.
     */
    virtual const void *getUnitPtr() const = 0;

    /**
     * Setup unit buffer in decode context after read.
     */
    virtual void afterRead(const void *start, size_t bufferUnits, uint64_t bufferEndFilePos, bool isMore) = 0;

    /**
     * Setup for bitwise reading.
     */
    virtual void setupBits(int bitOffset) = 0;
    virtual uint64_t getBitPos(int bitOffset, uint64_t bufferEndFilePos) const = 0;
    virtual uint64_t getBitPosV() const = 0;
    virtual void skipBits(int bits) = 0;
    virtual void adjUnitPtr(int newRemainingUnits) = 0;
    virtual void emptyBuffer(uint64_t newBitPosition) = 0;

    /**
     * Get size of each unit (typically 4 or 8)
     */
    virtual uint32_t getUnitByteSize() const = 0;
};

class ComprFileReadBase
{
public:
    static void ReadComprBuffer(uint64_t stopOffset,
                                bool readAll,
                                ComprFileDecodeContext &decodeContext,
                                int &bitOffset,
                                FastOS_FileInterface &file,
                                uint64_t &fileReadByteOffset,
                                uint64_t fileSize,
                                ComprBuffer &cbuf);
    static void SetPosition(uint64_t newPosition,
                            uint64_t stopOffset,
                            bool readAll,
                            ComprFileDecodeContext &decodeContext,
                            int &bitOffset,
                            FastOS_FileInterface *file,
                            uint64_t &fileReadByteOffset,
                            uint64_t fileSize,
                            ComprBuffer &cbuf);

protected:
    virtual ~ComprFileReadBase() = default;
};


class ComprFileReadContext : public ComprBuffer
{
private:
    ComprFileDecodeContext *_decodeContext;
    uint64_t _fileSize;
    uint64_t _fileReadByteOffset;
    int _bitOffset;
    uint64_t _stopOffset;
    bool _readAll;
    FastOS_FileInterface *_file;

public:
    ComprFileReadContext(ComprFileDecodeContext &decodeContext);
    ComprFileReadContext(uint32_t unitSize);
    ~ComprFileReadContext();

    void readComprBuffer(uint64_t stopOffset, bool readAll);
    void readComprBuffer();
    void setPosition(uint64_t newPosition);
    void allocComprBuf(unsigned int comprBufSize, size_t preferredFileAlignment);
    void setDecodeContext(ComprFileDecodeContext *decodeContext) { _decodeContext = decodeContext; }
    ComprFileDecodeContext *getDecodeContext() const { return _decodeContext; }
    void setFile(FastOS_FileInterface *file) { _file = file; }

    /**
     * Get file offset for end of compressed buffer.
     */
    uint64_t getBufferEndFilePos() const { return _fileReadByteOffset; }

    /**
     * Set file offset for end of compressed byffer.
     */
    void setBufferEndFilePos(uint64_t bufferEndFilePos) { _fileReadByteOffset = bufferEndFilePos; }
    void setBitOffset(int bitOffset) { _bitOffset = bitOffset; }
    void setFileSize(uint64_t fileSize) { _fileSize = fileSize; }

    /*
     * For unit testing only. Reference data owned by rhs, only works as
     * long as rhs is live and unchanged.
     */
    void referenceWriteContext(const ComprFileWriteContext &rhs);
    void reference_compressed_buffer(void *buffer, size_t usedUnits);
};


class ComprFileEncodeContext
{
public:
    virtual ~ComprFileEncodeContext() = default;

    /**
     * Get number of used units (e.g. _valI - start)
     */
    virtual int getUsedUnits(const uint64_t * start) = 0;

    /**
     * Get normal full buffer size (e.g. _valE - start)
     */
    virtual int getNormalMaxUnits(void *start) = 0;

    /**
     * Adjust buffer after write (e.g. _valI, _fileWriteBias)
     */
    virtual void afterWrite(ComprBuffer &cbuf, uint32_t remainingUnits, uint64_t bufferStartFilePos) = 0;

    /**
     * Adjust buffer size to align end of buffer.
     */
    virtual void adjustBufSize(ComprBuffer &cbuf) = 0;

    /**
     * Get size of each unit (typically 4 or 8)
     */
    virtual uint32_t getUnitByteSize() const = 0;
};

class ComprFileWriteBase
{
public:
    static void WriteComprBuffer(ComprFileEncodeContext &encodeContext,
                                 ComprBuffer &cbuf,
                                 FastOS_FileInterface &file,
                                 uint64_t &fileWriteByteOffset,
                                 bool flushSlack);

protected:
    virtual ~ComprFileWriteBase() = default;
};


class ComprFileWriteContext : public ComprBuffer
{
private:
    ComprFileEncodeContext *_encodeContext;
    FastOS_FileInterface *_file;
    uint64_t _fileWriteByteOffset; // XXX: Migrating from encode context

public:
    ComprFileWriteContext(ComprFileEncodeContext &encodeContext);
    ComprFileWriteContext(uint32_t unitSize);
    ~ComprFileWriteContext();

    void writeComprBuffer(bool flushSlack);
    void allocComprBuf(unsigned int comprBufSize, size_t preferredFileAlignment);
    void allocComprBuf();
    void setEncodeContext(ComprFileEncodeContext *encodeContext) { _encodeContext = encodeContext; }
    ComprFileEncodeContext *getEncodeContext() const { return _encodeContext; }
    void setFile(FastOS_FileInterface *file) { _file = file; }

    /**
     * Get file offset for start of compressed buffer.
     */
    uint64_t getBufferStartFilePos() const { return _fileWriteByteOffset; }

    /**
     * Grab compressed buffer from write context.  This is only legal when
     * no file is attached.
     */
    std::pair<uint64_t *, size_t> grabComprBuffer(vespalib::alloc::Alloc & comprAlloc);
};

}
