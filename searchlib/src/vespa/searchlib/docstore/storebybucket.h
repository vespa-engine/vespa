// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "chunk.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/data/memorydatastore.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <condition_variable>

namespace search::docstore {

/**
 * StoreByBucket will organize the data you add to it by buckets.
 * When you drain it it will be drained bucket by bucket. Inside a bucket
 * they will arrive in sorted order on their 'unused' bits.
 */
class StoreByBucket
{
    using MemoryDataStore = vespalib::MemoryDataStore;
    using Executor = vespalib::Executor;
    using ConstBufferRef = vespalib::ConstBufferRef;
    using CompressionConfig = vespalib::compression::CompressionConfig;
public:
    struct Index {
        using BucketId=document::BucketId;
        Index(BucketId bucketId, uint32_t localChunkId, uint32_t chunkId, uint32_t entry) noexcept :
                _bucketId(bucketId), _localChunkId(localChunkId), _chunkId(chunkId), _lid(entry)
        { }
        bool operator < (const Index & b) const noexcept {
            return BucketId::bucketIdToKey(_bucketId.getRawId()) < BucketId::bucketIdToKey(b._bucketId.getRawId());
        }
        BucketId _bucketId;
        uint32_t _localChunkId;
        uint32_t _chunkId;
        uint32_t _lid;
    };
    using IndexVector = std::vector<Index, vespalib::allocator_large<Index>>;
    struct IWrite {
        using BucketId=document::BucketId;
        virtual ~IWrite() = default;
        virtual void write(BucketId bucketId, uint32_t chunkId, uint32_t lid, ConstBufferRef data) = 0;
    };
    struct IndexIterator {
        virtual ~IndexIterator() = default;
        virtual bool has_next() noexcept = 0;
        virtual Index next() noexcept = 0;
    };
    struct StoreIndex {
        virtual ~StoreIndex() = default;
        virtual void store(const Index & index) = 0;
    };
    StoreByBucket(StoreIndex & storeIndex, MemoryDataStore & backingMemory,
                  Executor & executor, CompressionConfig compression) noexcept;
    //TODO Putting the below move constructor into cpp file fails for some unknown reason. Needs to be resolved.
    StoreByBucket(StoreByBucket &&) noexcept = delete;
    StoreByBucket(const StoreByBucket &) = delete;
    StoreByBucket & operator=(StoreByBucket &&) noexcept = delete;
    StoreByBucket & operator = (const StoreByBucket &) = delete;
    ~StoreByBucket();
    void add(document::BucketId bucketId, uint32_t chunkId, uint32_t lid, ConstBufferRef data);
    void close();
    /// close() must have been called prior to calling getBucketCount() or drain()
    void drain(IWrite & drain, IndexIterator & iterator);
    size_t getChunkCount() const noexcept;
private:
    void incChunksPosted();
    void waitAllProcessed();
    Chunk::UP createChunk();
    void closeChunk(Chunk::UP chunk);
    uint64_t                                     _chunkSerial;
    Chunk::UP                                    _current;
    StoreIndex                                 & _storeIndex;
    MemoryDataStore                            & _backingMemory;
    Executor                                   & _executor;
    mutable std::mutex                           _lock;
    std::condition_variable                      _cond;
    size_t                                       _numChunksPosted;
    vespalib::hash_map<uint64_t, ConstBufferRef> _chunks;
    CompressionConfig                            _compression;
};

}
