// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "chunk.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/data/memorydatastore.h>
#include <vespa/vespalib/util/threadexecutor.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <map>

namespace search {
namespace docstore {

/**
 * StoreByBucket will organize the data you add to it by buckets.
 * When you drain it it will be drained bucket by bucket. Inside a bucket
 * they will arrive in sorted order on their 'unused' bits.
 */
class StoreByBucket
{
    using MemoryDataStore = vespalib::MemoryDataStore;
    using ThreadExecutor = vespalib::ThreadExecutor;
    using ConstBufferRef = vespalib::ConstBufferRef;
    using CompressionConfig = vespalib::compression::CompressionConfig;
public:
    StoreByBucket(vespalib::MemoryDataStore & backingMemory, const CompressionConfig & compression);
    StoreByBucket(MemoryDataStore & backingMemory, ThreadExecutor & executor, const CompressionConfig & compression);
    StoreByBucket(StoreByBucket &&) = default;
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
    Chunk::UP createChunk();
    void closeChunk(Chunk::UP chunk);
    struct Index {
        using BucketId=document::BucketId;
        Index(BucketId bucketId, uint32_t id, uint32_t chunkId, uint32_t entry) :
            _bucketId(bucketId), _id(id), _chunkId(chunkId), _lid(entry)
        { }
        bool operator < (const Index & b) const {
            return BucketId::bucketIdToKey(_bucketId.getRawId()) < BucketId::bucketIdToKey(b._bucketId.getRawId());
        }
        BucketId _bucketId;
        uint32_t _id;
        uint32_t _chunkId;
        uint32_t _lid;
    };
    using IndexVector = vespalib::Array<Index>;
    uint64_t                                     _chunkSerial;
    Chunk::UP                                    _current;
    std::map<uint64_t, IndexVector>              _where;
    MemoryDataStore                            & _backingMemory;
    ThreadExecutor                             & _executor;
    vespalib::Lock                               _lock;
    vespalib::hash_map<uint64_t, ConstBufferRef> _chunks;
    CompressionConfig                            _compression;
};

}
}
