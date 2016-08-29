// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storebybucket.h"

namespace search {
namespace docstore {

using document::BucketId;

StoreByBucket::StoreByBucket(vespalib::MemoryDataStore & backingMemory) :
    _chunks(),
    _current(),
    _where(),
    _backingMemory(backingMemory)
{
    createCurrent();
}

void
StoreByBucket::add(BucketId bucketId, uint32_t chunkId, uint32_t lid, const void *buffer, size_t sz)
{
    if ( ! _current->hasRoom(sz)) {
        closeCurrent();
        createCurrent();
    }
    Index idx(bucketId, _chunks.size(), chunkId, lid);
    _current->append(lid, buffer, sz);
    _where[bucketId.toKey()].push_back(idx);
}

void StoreByBucket::createCurrent()
{
    _current.reset(new Chunk(_chunks.size(), Chunk::Config(0x10000, 1000)));
}

void
StoreByBucket::closeCurrent()
{
    vespalib::DataBuffer buffer;
    document::CompressionConfig lz4(document::CompressionConfig::LZ4);
    _current->pack(1, buffer, lz4);
    buffer.shrink(buffer.getDataLen());
    _chunks.emplace_back(_backingMemory.push_back(buffer.getData(), buffer.getDataLen()).data(), buffer.getDataLen());
    _current.reset();
}

void
StoreByBucket::drain(IWrite & drainer)
{
    closeCurrent();
    std::vector<Chunk::UP> chunks;
    chunks.reserve(_chunks.size());
    for (const vespalib::ConstBufferRef buffer : _chunks) {
        chunks.push_back(std::make_unique<Chunk>(chunks.size(), buffer.data(), buffer.size()));
    }
    _chunks.clear();
    for (auto & it : _where) {
        std::sort(it.second.begin(), it.second.end());
        for (Index idx : it.second) {
            vespalib::ConstBufferRef data(chunks[idx._id]->getLid(idx._lid));
            drainer.write(idx._bucketId, idx._chunkId, idx._lid, data.c_str(), data.size());
        }
    }
}

}
}
