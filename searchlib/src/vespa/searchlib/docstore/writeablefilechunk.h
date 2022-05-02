// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "filechunk.h"
#include <vespa/vespalib/util/executor.h>
#include <vespa/searchlib/transactionlog/syncproxy.h>
#include <vespa/fastos/file.h>
#include <map>
#include <deque>
#include <condition_variable>

namespace search {

class PendingChunk;
class ProcessedChunk;

namespace common { class FileHeaderContext; }

class WriteableFileChunk : public FileChunk
{
public:
    class Config
    {
    public:
        using CompressionConfig = vespalib::compression::CompressionConfig;
        Config() : Config({CompressionConfig::LZ4, 9, 60}, 0x10000) { }

        Config(const CompressionConfig &compression, size_t maxChunkBytes)
            : _compression(compression),
              _maxChunkBytes(maxChunkBytes)
        { }

        const CompressionConfig & getCompression() const { return _compression; }
        size_t getMaxChunkBytes() const { return _maxChunkBytes; }
        bool operator == (const Config & rhs) const {
            return (_compression == rhs._compression) && (_maxChunkBytes == rhs._maxChunkBytes);
        }
    private:
        CompressionConfig _compression;
        size_t _maxChunkBytes;
    };

public:
    typedef std::unique_ptr<WriteableFileChunk> UP;
    WriteableFileChunk(vespalib::Executor & executor, FileId fileId, NameId nameId,
                       const vespalib::string & baseName, uint64_t initialSerialNum,
                       uint32_t docIdLimit, const Config & config,
                       const TuneFileSummary &tune, const common::FileHeaderContext &fileHeaderContext,
                       const IBucketizer * bucketizer, bool crcOnReadDisabled);
    ~WriteableFileChunk() override;

    ssize_t read(uint32_t lid, SubChunkId chunk, vespalib::DataBuffer & buffer) const override;
    void read(LidInfoWithLidV::const_iterator begin, size_t count, IBufferVisitor & visitor) const override;

    LidInfo append(uint64_t serialNum, uint32_t lid, const void * buffer, size_t len,
                   vespalib::CpuUsage::Category cpu_category);
    void flush(bool block, uint64_t syncToken, vespalib::CpuUsage::Category cpu_category);
    uint64_t   getSerialNum() const { return _serialNum; }
    void setSerialNum(uint64_t serialNum) { _serialNum = std::max(_serialNum, serialNum); }

    vespalib::system_time getModificationTime() const override;
    void freeze(vespalib::CpuUsage::Category cpu_category);
    size_t getDiskFootprint() const override;
    size_t getMemoryFootprint() const override;
    size_t getMemoryMetaFootprint() const override;
    vespalib::MemoryUsage getMemoryUsage() const override;
    size_t updateLidMap(const unique_lock &guard, ISetLid &lidMap, uint64_t serialNum, uint32_t docIdLimit) override;
    void waitForDiskToCatchUpToNow() const;
    void flushPendingChunks(uint64_t serialNum);
    DataStoreFileChunkStats getStats() const override;

    static uint64_t writeIdxHeader(const common::FileHeaderContext &fileHeaderContext, uint32_t docIdLimit, FastOS_FileInterface &file);
private:
    using ProcessedChunkUP = std::unique_ptr<ProcessedChunk>;
    typedef std::map<uint32_t, ProcessedChunkUP > ProcessedChunkMap;

    typedef std::vector<ProcessedChunkUP> ProcessedChunkQ;

    bool frozen() const override { return _frozen.load(std::memory_order_acquire); }
    void waitForChunkFlushedToDisk(uint32_t chunkId) const;
    void waitForAllChunksFlushedToDisk() const;
    void fileWriter(const uint32_t firstChunkId);
    void internalFlush(uint32_t, uint64_t serialNum, vespalib::CpuUsage::Category cpu_category);
    void enque(ProcessedChunkUP, vespalib::CpuUsage::Category cpu_category);
    int32_t flushLastIfNonEmpty(bool force);
    // _writeMonitor should not be held when calling restart
    void restart(uint32_t nextChunkId, vespalib::CpuUsage::Category cpu_category);
    ProcessedChunkQ drainQ(unique_lock & guard);
    void readDataHeader();
    void readIdxHeader(FastOS_FileInterface & idxFile);
    void writeDataHeader(const common::FileHeaderContext &fileHeaderContext);
    bool needFlushPendingChunks(uint64_t serialNum, uint64_t datFileLen);
    bool needFlushPendingChunks(const unique_lock & guard, uint64_t serialNum, uint64_t datFileLen);
    vespalib::system_time unconditionallyFlushPendingChunks(const unique_lock & flushGuard, uint64_t serialNum, uint64_t datFileLen);
    static void insertChunks(ProcessedChunkMap & orderedChunks, ProcessedChunkQ & newChunks, uint32_t nextChunkId);
    static ProcessedChunkQ fetchNextChain(ProcessedChunkMap & orderedChunks, uint32_t firstChunkId);
    ChunkMeta computeChunkMeta(const unique_lock & guard,
                               const vespalib::GenerationHandler::Guard & bucketizerGuard,
                               size_t offset, const ProcessedChunk & tmp, const Chunk & active);
    ChunkMetaV computeChunkMeta(ProcessedChunkQ & chunks, size_t startPos, size_t & sz, bool & done);
    void writeData(const ProcessedChunkQ & chunks, size_t sz);
    void updateChunkInfo(const ProcessedChunkQ & chunks, const ChunkMetaV & cmetaV, size_t sz);
    void updateCurrentDiskFootprint();
    size_t getDiskFootprint(const unique_lock & guard) const;
    std::unique_ptr<FastOS_FileInterface> openIdx();
    const Chunk& get_chunk(uint32_t chunk) const;

    Config            _config;
    SerialNum         _serialNum;
    std::atomic<bool> _frozen;
    // Lock order is _writeLock, _flushLock, _lock
    mutable std::mutex             _lock;
    mutable std::condition_variable _cond;
    std::mutex        _writeLock;
    std::mutex        _flushLock;
    FastOS_File       _dataFile;
    using ChunkMap = std::map<uint32_t, Chunk::UP>;
    ChunkMap          _chunkMap;
    using PendingChunks = std::deque<std::shared_ptr<PendingChunk>>;
    PendingChunks     _pendingChunks;
    uint64_t          _pendingIdx;
    uint64_t          _pendingDat;
    std::atomic<uint64_t> _idxFileSize;
    std::atomic<uint64_t> _currentDiskFootprint;
    uint32_t          _nextChunkId;
    Chunk::UP         _active;
    size_t            _alignment;
    size_t            _granularity;
    size_t            _maxChunkSize;
    uint32_t          _firstChunkIdToBeWritten;
    bool              _writeTaskIsRunning;
    std::mutex              _writeMonitor;
    std::condition_variable _writeCond;
    ProcessedChunkQ   _writeQ;
    vespalib::Executor  & _executor;
    ProcessedChunkMap     _orderedChunks;
    BucketDensityComputer _bucketMap;
};

} // namespace search
