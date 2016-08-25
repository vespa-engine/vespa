// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "chunk.h"
#include <vespa/document/bucket/bucketid.h>
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
public:
    StoreByBucket();
    class IWrite {
    public:
        using BucketId=document::BucketId;
        virtual ~IWrite() { }
        virtual void write(BucketId bucketId, uint32_t chunkId, uint32_t lid, const void *buffer, size_t sz) = 0;
    };
    void add(document::BucketId bucketId, uint32_t chunkId, uint32_t lid, const void *buffer, size_t sz);
    void drain(IWrite & drain);
    size_t getChunkCount() const { return _chunks.size(); }
    size_t getBucketCount() const { return _where.size(); }
    size_t getLidCount() const {
        size_t lidCount(0);
        for (const auto & it : _where) {
            lidCount += it.second.size();
        }
        return lidCount;
    }
private:
    typedef std::unique_ptr<vespalib::DataBuffer> BufferUP;
    void closeCurrent();
    void createCurrent();
    struct Index {
        using BucketId=document::BucketId;
        Index(BucketId bucketId, uint32_t id, uint32_t chunkId, uint32_t entry) :
            _bucketId(bucketId), _id(id), _chunkId(chunkId), _lid(entry)
        { }
        bool operator < (const Index & b) const {
            return BucketId::reverse(_bucketId.getRawId()) < BucketId::reverse(b._bucketId.getRawId());
        }
        BucketId _bucketId;
        uint32_t _id;
        uint32_t _chunkId;
        uint32_t _lid;
    };
    std::vector<BufferUP> _chunks;
    Chunk::UP _current;
    std::map<uint64_t, std::vector<Index>> _where;
};

}
}
