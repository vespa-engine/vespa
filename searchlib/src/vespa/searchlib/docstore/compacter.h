// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "filechunk.h"
#include "storebybucket.h"
#include <vespa/vespalib/data/memorydatastore.h>

namespace search { class LogDataStore; }

namespace search::docstore {

/**
 * A simple write through implementation of the IWriteData interface.
 */
class Compacter : public IWriteData
{
public:
    explicit Compacter(LogDataStore & ds) : _ds(ds) { }
    void write(LockGuard guard, uint32_t chunkId, uint32_t lid, ConstBufferRef data) override;
    void close() override { }
private:
    LogDataStore & _ds;
};

class BucketIndexStore : public StoreByBucket::StoreIndex {
public:
    BucketIndexStore(size_t maxSignificantBucketBits, uint32_t numPartitions) noexcept;
    ~BucketIndexStore() override;
    size_t toPartitionId(document::BucketId bucketId) const noexcept {
        uint64_t sortableBucketId = bucketId.toKey();
        return (sortableBucketId >> _inSignificantBucketBits) % _numPartitions;
    }
    void store(const StoreByBucket::Index & index) override;
    size_t getBucketCount() const noexcept;
    size_t getLidCount() const noexcept { return _where.size(); }
    void prepareForIterate();
    std::unique_ptr<StoreByBucket::IndexIterator> createIterator(uint32_t partitionId) const;
private:
    using IndexVector = std::vector<StoreByBucket::Index, vespalib::allocator_large<StoreByBucket::Index>>;
    class LidIterator : public StoreByBucket::IndexIterator {
    public:
        LidIterator(const BucketIndexStore & bc, size_t partitionId);
        bool has_next() noexcept override;
        StoreByBucket::Index next() noexcept override;
    private:
        const BucketIndexStore       & _store;
        size_t                        _partitionId;
        IndexVector::const_iterator   _current;
    };
    size_t       _inSignificantBucketBits;
    IndexVector  _where;
    uint32_t     _numPartitions;
    bool         _readyForIterate;
};

/**
 * This will split the incoming data into buckets.
 * The buckets data will then be written out in bucket order.
 * The buckets will be ordered, and the objects inside the buckets will be further ordered.
 * All data are kept compressed to minimize memory usage.
 **/
class BucketCompacter : public IWriteData,
                        public StoreByBucket::IWrite
{
    using CompressionConfig = vespalib::compression::CompressionConfig;
    using Executor = vespalib::Executor;
public:
    using FileId = FileChunk::FileId;
    BucketCompacter(size_t maxSignificantBucketBits, CompressionConfig compression, LogDataStore & ds,
                    Executor & executor, const IBucketizer & bucketizer, FileId source, FileId destination);
    ~BucketCompacter() override;
    void write(LockGuard guard, uint32_t chunkId, uint32_t lid, ConstBufferRef data) override;
    void write(BucketId bucketId, uint32_t chunkId, uint32_t lid, ConstBufferRef data) override;
    void close() override;
private:
    static constexpr size_t NUM_PARTITIONS = 256;
    using GenerationHandler = vespalib::GenerationHandler;
    using Partitions = std::array<std::unique_ptr<StoreByBucket>, NUM_PARTITIONS>;
    FileId getDestinationId(const LockGuard & guard) const;
    FileId                                 _sourceFileId;
    FileId                                 _destinationFileId;
    LogDataStore                         & _ds;
    const IBucketizer                    & _bucketizer;
    std::mutex                             _lock;
    vespalib::MemoryDataStore              _backingMemory;
    BucketIndexStore                       _bucketIndexStore;
    Partitions                             _tmpStore;
    GenerationHandler::Guard               _lidGuard;
    vespalib::hash_map<uint64_t, uint32_t> _stat;
};

}
