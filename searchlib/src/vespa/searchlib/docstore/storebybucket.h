// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "chunk.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/data/memorydatastore.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <map>
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
    StoreByBucket(MemoryDataStore & backingMemory, Executor & executor, const CompressionConfig & compression) noexcept;
    //TODO Putting the below move constructor into cpp file fails for some unknown reason. Needs to be resolved.
    StoreByBucket(StoreByBucket &&) noexcept = default;
    StoreByBucket(const StoreByBucket &) = delete;
    StoreByBucket & operator=(StoreByBucket &&) noexcept = delete;
    StoreByBucket & operator = (const StoreByBucket &) = delete;
    ~StoreByBucket();
    class IWrite {
    public:
        using BucketId=document::BucketId;
        virtual ~IWrite() { }
        virtual void write(BucketId bucketId, uint32_t chunkId, uint32_t lid, const void *buffer, size_t sz) = 0;
    };
    void add(document::BucketId bucketId, uint32_t chunkId, uint32_t lid, const void *buffer, size_t sz);
    void drain(IWrite & drain);
    size_t getChunkCount() const;
    size_t getBucketCount() const { return _where.size(); }
    size_t getLidCount() const {
        size_t lidCount(0);
        for (const auto & it : _where) {
            lidCount += it.second.size();
        }
        return lidCount;
    }
private:
    void incChunksPosted();
    void waitAllProcessed();
    Chunk::UP createChunk();
    void closeChunk(Chunk::UP chunk);
    struct Index {
        using BucketId=document::BucketId;
        Index(BucketId bucketId, uint32_t id, uint32_t chunkId, uint32_t entry) noexcept :
            _bucketId(bucketId), _id(id), _chunkId(chunkId), _lid(entry)
        { }
        bool operator < (const Index & b) const noexcept {
            return BucketId::bucketIdToKey(_bucketId.getRawId()) < BucketId::bucketIdToKey(b._bucketId.getRawId());
        }
        BucketId _bucketId;
        uint32_t _id;
        uint32_t _chunkId;
        uint32_t _lid;
    };
    using IndexVector = std::vector<Index, vespalib::allocator_large<Index>>;
    uint64_t                                     _chunkSerial;
    Chunk::UP                                    _current;
    std::map<uint64_t, IndexVector>              _where;
    MemoryDataStore                            & _backingMemory;
    Executor                                   & _executor;
    std::unique_ptr<std::mutex>                  _lock;
    std::unique_ptr<std::condition_variable>     _cond;
    size_t                                       _numChunksPosted;
    vespalib::hash_map<uint64_t, ConstBufferRef> _chunks;
    CompressionConfig                            _compression;
};

}
