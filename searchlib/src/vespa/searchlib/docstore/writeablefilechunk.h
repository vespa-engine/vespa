// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/docstore/filechunk.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/searchlib/transactionlog/syncproxy.h>
#include <map>
#include <deque>

namespace search
{

namespace common
{

class FileHeaderContext;

}

class WriteableFileChunk : public FileChunk
{
public:
    class Config
    {
    public:
        Config()
            : _compression(document::CompressionConfig::LZ4, 9, 60),
              _maxChunkBytes(0x10000),
              _maxChunkEntries(256)
        { }

        Config(const document::CompressionConfig &compression,
               size_t maxChunkBytes, size_t maxChunkEntries)
            : _compression(compression),
              _maxChunkBytes(maxChunkBytes),
              _maxChunkEntries(maxChunkEntries)
        { }

        const document::CompressionConfig & getCompression() const { return _compression; }
        size_t getMaxChunkBytes() const { return _maxChunkBytes; }
        size_t getMaxChunkEntries() const { return _maxChunkEntries; }
    private:
        document::CompressionConfig _compression;
        size_t _maxChunkBytes;
        size_t _maxChunkEntries;
    };

public:
    typedef std::unique_ptr<WriteableFileChunk> UP;
    WriteableFileChunk(vespalib::ThreadStackExecutorBase & executor,
                       FileId fileId, NameId nameId,
                       const vespalib::string & baseName,
                       uint64_t initialSerialNum,
                       const Config & config,
                       const TuneFileSummary &tune,
                       const common::FileHeaderContext &fileHeaderContext,
                       const IBucketizer * bucketizer,
                       bool crcOnReadDisabled);
    ~WriteableFileChunk();

    ssize_t read(uint32_t lid, SubChunkId chunk, vespalib::DataBuffer & buffer) const override;
    void read(LidInfoWithLidV::const_iterator begin, size_t count, IBufferVisitor & visitor) const override;

    LidInfo append(uint64_t serialNum, uint32_t lid, const void * buffer, size_t len);
    void flush(bool block, uint64_t syncToken);
    uint64_t   getSerialNum() const { return _serialNum; }
    void setSerialNum(uint64_t serialNum) { _serialNum = std::max(_serialNum, serialNum); }

    virtual fastos::TimeStamp getModificationTime() const override;
    void freeze();
    size_t getDiskFootprint() const override;
    size_t getMemoryFootprint() const override;
    size_t getMemoryMetaFootprint() const override;
    virtual MemoryUsage getMemoryUsage() const override;
    size_t updateLidMap(const LockGuard & guard, ISetLid & lidMap, uint64_t serialNum) override;
    void waitForDiskToCatchUpToNow() const;
    void flushPendingChunks(uint64_t serialNum);
    virtual DataStoreFileChunkStats getStats() const override;

    static uint64_t writeIdxHeader(const common::FileHeaderContext &fileHeaderContext, FastOS_FileInterface & file);
private:
    class ProcessedChunk
    {
    public:
        typedef std::unique_ptr<ProcessedChunk> UP;
        ProcessedChunk(uint32_t chunkId, uint32_t alignment)
            : _chunkId(chunkId),
              _payLoad(0),
              _buf(0ul, alignment)
        { }
        void setPayLoad() { _payLoad = _buf.getDataLen(); }
        uint32_t getPayLoad() const { return _payLoad; }
        uint32_t getChunkId() const { return _chunkId; }
        const vespalib::DataBuffer & getBuf() const { return _buf; }
        vespalib::DataBuffer & getBuf() { return _buf; }
    private:
        uint32_t             _chunkId;
        uint32_t             _payLoad;
        vespalib::DataBuffer _buf;
    };
    typedef std::map<uint32_t, ProcessedChunk::UP> ProcessedChunkMap;

    typedef std::vector<ProcessedChunk::UP> ProcessedChunkQ;

    /*
     * Information about serialized chunk written to .dat file but not yet
     * synced.
     */
    class PendingChunk
    {
        vespalib::nbostream _idx; // Serialized chunk for .idx file
        uint64_t _lastSerial;
        uint64_t _dataOffset;
        uint32_t _dataLen;
    public:
        typedef std::shared_ptr<PendingChunk> SP;
        PendingChunk(uint64_t lastSerial, uint64_t dataOffset, uint32_t dataLen);
        ~PendingChunk(void);
        vespalib::nbostream & getSerializedIdx(void) { return _idx; }
        const vespalib::nbostream & getSerializedIdx(void) const { return _idx; }
        uint64_t getDataOffset(void) const { return _dataOffset; }
        uint32_t getDataLen(void) const { return _dataLen; }
        uint32_t getIdxLen(void) const { return _idx.size(); }
        uint64_t getLastSerial(void) const { return _lastSerial; }
    };

    bool frozen() const override { return _frozen; }
    void waitForChunkFlushedToDisk(uint32_t chunkId) const;
    void waitForAllChunksFlushedToDisk() const;
    void fileWriter(const uint32_t firstChunkId);
    void internalFlush(uint32_t, uint64_t serialNum);
    void enque(ProcessedChunk::UP);
    int32_t flushLastIfNonEmpty(bool force);
    void restart(const vespalib::MonitorGuard & guard, uint32_t nextChunkId);
    ProcessedChunkQ drainQ();
    void readDataHeader(void);
    void readIdxHeader(void);
    void writeDataHeader(const common::FileHeaderContext &fileHeaderContext);
    bool needFlushPendingChunks(uint64_t serialNum, uint64_t datFileLen);
    bool needFlushPendingChunks(const vespalib::MonitorGuard & guard, uint64_t serialNum, uint64_t datFileLen);
    fastos::TimeStamp unconditionallyFlushPendingChunks(const vespalib::LockGuard & flushGuard, uint64_t serialNum, uint64_t datFileLen);
    static void insertChunks(ProcessedChunkMap & orderedChunks, ProcessedChunkQ & newChunks, const uint32_t nextChunkId);
    static ProcessedChunkQ fetchNextChain(ProcessedChunkMap & orderedChunks, const uint32_t firstChunkId);
    size_t computeDataLen(const ProcessedChunk & tmp, const Chunk & active);
    ChunkMeta computeChunkMeta(const vespalib::LockGuard & guard,
                               const vespalib::GenerationHandler::Guard & bucketizerGuard,
                               size_t offset, const ProcessedChunk & tmp, const Chunk & active);
    ChunkMetaV computeChunkMeta(ProcessedChunkQ & chunks, size_t startPos, size_t & sz, bool & done);
    void writeData(const ProcessedChunkQ & chunks, size_t sz);
    void updateChunkInfo(const ProcessedChunkQ & chunks, const ChunkMetaV & cmetaV, size_t sz);
    void updateCurrentDiskFootprint();
    size_t getDiskFootprint(const vespalib::MonitorGuard & guard) const;

    Config            _config;
    SerialNum         _serialNum;
    bool              _frozen;
    // Lock order is _writeLock, _flushLock, _lock
    vespalib::Monitor _lock;
    vespalib::Lock    _writeLock;
    vespalib::Lock    _flushLock;
    FastOS_File       _dataFile;
    FastOS_File       _idxFile;
    typedef std::map<uint32_t, Chunk::UP> ChunkMap;
    ChunkMap          _chunkMap;
    typedef std::deque<PendingChunk::SP> PendingChunks;
    PendingChunks     _pendingChunks;
    uint64_t          _pendingIdx;
    uint64_t          _pendingDat;
    uint64_t          _currentDiskFootprint;
    uint32_t          _nextChunkId;
    Chunk::UP         _active;
    size_t            _alignment;
    size_t            _granularity;
    size_t            _maxChunkSize;
    uint32_t          _firstChunkIdToBeWritten;
    bool              _writeTaskIsRunning;
    vespalib::Monitor _writeMonitor;
    ProcessedChunkQ   _writeQ;
    vespalib::ThreadStackExecutorBase & _executor;
    ProcessedChunkMap _orderedChunks;
    BucketDensityComputer _bucketMap;
};

} // namespace search

