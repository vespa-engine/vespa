// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "chunk.h"
#include "ibucketizer.h"
#include "lid_info.h"
#include "randread.h"
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <vespa/vespalib/util/time.h>

class FastOS_FileInterface;

namespace vespalib {
    class DataBuffer;
    class GenericHeader;
    class Executor;
}

namespace search {

class DataStoreFileChunkStats;

class IWriteData
{
public:
    using LockGuard = std::unique_lock<std::mutex>;
    using ConstBufferRef = vespalib::ConstBufferRef;

    virtual ~IWriteData() = default;

    virtual void write(LockGuard guard, uint32_t chunkId, uint32_t lid, ConstBufferRef data) = 0;
    virtual void close() = 0;
};

class IFileChunkVisitorProgress
{
public:
    virtual ~IFileChunkVisitorProgress() = default;
    virtual void updateProgress() = 0;
};

class BucketDensityComputer
{
public:
    explicit BucketDensityComputer(const IBucketizer * bucketizer) : _bucketizer(bucketizer), _count(0) { }
    void recordLid(const vespalib::GenerationHandler::Guard & guard, uint32_t lid, uint32_t dataSize) {
        if (_bucketizer && (dataSize > 0)) {
            recordLid(_bucketizer->getBucketOf(guard, lid));
        }
    }
    void recordLid(document::BucketId bucketId) {
        _count++;
        _bucketSet[bucketId.getId()]++;
    }
    size_t getNumBuckets() const { return _bucketSet.size(); }
    vespalib::GenerationHandler::Guard getGuard() const {
        return _bucketizer
               ? _bucketizer->getGuard()
               : vespalib::GenerationHandler::Guard();
    }
private:
    const IBucketizer * _bucketizer;
    size_t _count;
    vespalib::hash_map<uint64_t, uint32_t> _bucketSet;
};

class FileChunk
{
public:
    using unique_lock = std::unique_lock<std::mutex>;
    class NameId {
    public:
        explicit NameId(size_t id) noexcept : _id(id) { }
        uint64_t getId() const { return _id; }
        vespalib::string createName(const vespalib::string &baseName) const;
        bool operator == (const NameId & rhs) const { return _id == rhs._id; }
        bool operator != (const NameId & rhs) const { return _id != rhs._id; }
        bool operator < (const NameId & rhs) const { return _id < rhs._id; }
        NameId next() const { return NameId(_id + 1); }
        static NameId first() { return NameId(0u); }
        static NameId last() { return NameId(std::numeric_limits<uint64_t>::max()); }
    private:
        uint64_t _id;
    };
    class FileId {
    public:
        explicit FileId(uint32_t id) noexcept : _id(id) { }
        uint32_t getId() const { return _id; }
        bool operator != (const FileId & rhs) const { return _id != rhs._id; }
        bool operator == (const FileId & rhs) const { return _id == rhs._id; }
        bool operator < (const FileId & rhs) const { return _id < rhs._id; }
        FileId prev() const { return FileId(_id - 1); }
        FileId next() const { return FileId(_id + 1); }
        bool isActive() const { return _id < 0; }
        static FileId first() { return FileId(0u); }
        static FileId active() { return FileId(-1); }
    private:
        int32_t _id;
    };
    using LidBufferMap = vespalib::hash_map<uint32_t, std::unique_ptr<vespalib::DataBuffer>>;
    using UP = std::unique_ptr<FileChunk>;
    using SubChunkId = uint32_t;
    FileChunk(FileId fileId, NameId nameId, const vespalib::string &baseName, const TuneFileSummary &tune,
              const IBucketizer *bucketizer);
    virtual ~FileChunk();

    virtual void updateLidMap(const unique_lock &guard, ISetLid &lidMap, uint64_t serialNum, uint32_t docIdLimit);
    virtual ssize_t read(uint32_t lid, SubChunkId chunk, vespalib::DataBuffer & buffer) const;
    virtual void read(LidInfoWithLidV::const_iterator begin, size_t count, IBufferVisitor & visitor) const;
    void remove(uint32_t lid, uint32_t size);
    virtual size_t getDiskFootprint() const { return _diskFootprint.load(std::memory_order_relaxed); }
    virtual size_t getMemoryFootprint() const;
    virtual size_t getMemoryMetaFootprint() const;
    virtual vespalib::MemoryUsage getMemoryUsage() const;

    virtual size_t getDiskHeaderFootprint() const { return _dataHeaderLen + _idxHeaderLen; }
    size_t getDiskBloat() const {
        size_t addedBytes = getAddedBytes();
        return (addedBytes == 0)
               ? getDiskFootprint()
               : size_t(getDiskFootprint() * double(getErasedBytes())/addedBytes);
    }
    /**
     * Get a metric for unorder of data in the file relative to when
     * the data is ordered.
     *
     * Consider a two-dimentional matrix, with rows of chunks
     * containing buckets and columns of buckets present in chunks.
     * Each matrix element contains '1' if the bucket is present in
     * the chunk or '0' if the bucket is not present in the chunk.
     *
     * Constraint of matrix is that all row sums must be nonzero, and
     * all column sums must be nonzero.
     *
     * Minimum matrix sum is (max(rows, columns)).
     * Maximum matrix sum is (rows * columns).
     * Maximum matrix sum when all data is ordered is (rows + columns - 1).
     *
     * We use matrix sum divided by minimum matrix sum as metric. When all
     * data is ordered we get a number in the range [1.0, 2.0).
     */
    double getBucketSpread() const {
        return (((_numChunksWithBuckets == 0) || (_numUniqueBuckets == 0))
                ? 1.0
                : (double(_sumNumBuckets) /
                   std::max(_numUniqueBuckets, _numChunksWithBuckets)));
    }
    void addNumBuckets(size_t numBucketsInChunk);

    FileId getFileId() const { return _fileId; }
    NameId       getNameId() const { return _nameId; }
    uint32_t getNumLids() const { return _numLids; }
    size_t   getErasedCount() const { return _erasedCount.load(std::memory_order_relaxed); }
    size_t   getAddedBytes() const { return _addedBytes.load(std::memory_order_relaxed); }
    size_t   getErasedBytes() const { return _erasedBytes.load(std::memory_order_relaxed); }
    uint64_t getLastPersistedSerialNum() const;
    uint32_t getDocIdLimit() const { return _docIdLimit; }
    virtual vespalib::system_time getModificationTime() const;
    virtual bool frozen() const { return true; }
    const vespalib::string & getName() const { return _name; }
    void appendTo(vespalib::Executor & executor, const IGetLid & db, IWriteData & dest,
                  uint32_t numChunks, IFileChunkVisitorProgress *visitorProgress,
                  vespalib::CpuUsage::Category cpu_category);
    /**
     * Must be called after chunk has been created to allow correct
     * underlying file object to be created.  Must be called before
     * any read.
     */
    void enableRead();
    // This should never be done to something that is used. Backing
    // Files are removed and everythings dies.
    void erase();
    /**
     * This will spinn through the data and verify the content of both
     * the '.dat' and the '.idx' files.
     *
     * @param reportOnly If set inconsitencies will be written to 'stderr'.
     */
    void verify(bool reportOnly) const;

    uint32_t      getNumChunks() const;
    size_t       getNumBuckets() const { return _sumNumBuckets; }
    size_t getNumUniqueBuckets() const { return _numUniqueBuckets; }

    virtual DataStoreFileChunkStats getStats() const;

    /**
     * Read header and return number of bytes it consist of.
     */
    static uint64_t readIdxHeader(FastOS_FileInterface &idxFile, uint32_t &docIdLimit);
    static uint64_t readDataHeader(FileRandRead &idxFile);
    static bool isIdxFileEmpty(const vespalib::string & name);
    static void eraseIdxFile(const vespalib::string & name);
    static void eraseDatFile(const vespalib::string & name);
    static vespalib::string createIdxFileName(const vespalib::string & name);
    static vespalib::string createDatFileName(const vespalib::string & name);
private:
    class TmpChunkMeta : public ChunkMeta,
                         public std::vector<LidMeta>
    {
    public:
        void fill(vespalib::nbostream & is);
    };
    using BucketizerGuard = vespalib::GenerationHandler::Guard;
    uint64_t handleChunk(const unique_lock &guard, ISetLid &lidMap, uint32_t docIdLimit,
                         const BucketizerGuard & bucketizerGuard, BucketDensityComputer & global,
                         const TmpChunkMeta & chunkMeta);
    using File = std::unique_ptr<FileRandRead>;
    const FileId           _fileId;
    const NameId           _nameId;
    const vespalib::string _name;
    std::atomic<size_t>    _erasedCount;
    std::atomic<size_t>    _erasedBytes;
    std::atomic<size_t>    _diskFootprint;
    size_t                 _sumNumBuckets;
    size_t                 _numChunksWithBuckets;
    size_t                 _numUniqueBuckets;
    File                   _file;
protected:
    void setDiskFootprint(size_t sz) { _diskFootprint.store(sz, std::memory_order_relaxed); }
    static size_t adjustSize(size_t sz);

    class ChunkInfo
    {
    public:
        ChunkInfo() noexcept : _lastSerial(0), _offset(0), _size(0) { }
        ChunkInfo(uint64_t offset, uint32_t size, uint64_t lastSerial) noexcept;
        uint64_t       getOffset() const { return _offset; }
        uint32_t       getSize() const { return _size; }
        uint64_t getLastSerial() const { return _lastSerial; }

        bool valid() const { return (_offset != 0) || (_size != 0) || (_lastSerial != 0); }
    private:
        uint64_t _lastSerial;
        uint64_t _offset;
        uint32_t _size;
    };

    void setNumUniqueBuckets(size_t numUniqueBuckets) { _numUniqueBuckets = numUniqueBuckets; }
    ssize_t read(uint32_t lid, SubChunkId chunkId, const ChunkInfo & chunkInfo, vespalib::DataBuffer & buffer) const;
    void read(LidInfoWithLidV::const_iterator begin, size_t count, ChunkInfo ci, IBufferVisitor & visitor) const;
    static uint32_t readDocIdLimit(vespalib::GenericHeader &header);
    static void writeDocIdLimit(vespalib::GenericHeader &header, uint32_t docIdLimit);

    using ChunkInfoVector = std::vector<ChunkInfo, vespalib::allocator_large<ChunkInfo>>;
    const IBucketizer    * _bucketizer;
    std::atomic<size_t>    _addedBytes;
    TuneFileSummary        _tune;
    vespalib::string       _dataFileName;
    vespalib::string       _idxFileName;
    ChunkInfoVector        _chunkInfo;
    std::atomic<uint64_t>  _lastPersistedSerialNum;
    uint32_t               _dataHeaderLen;
    uint32_t               _idxHeaderLen;
    uint32_t               _numLids;
    uint32_t               _docIdLimit; // Limit when the file was created. Stored in idx file header.
    vespalib::system_time  _modificationTime;
};

} // namespace search
