// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storebybucket.h"
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/data/databuffer.h>
#include <algorithm>

namespace search::docstore {

using document::BucketId;
using vespalib::makeLambdaTask;

StoreByBucket::StoreByBucket(MemoryDataStore & backingMemory, Executor & executor, const CompressionConfig & compression) noexcept
    : _chunkSerial(0),
      _current(),
      _where(),
      _backingMemory(backingMemory),
      _executor(executor),
      _lock(std::make_unique<std::mutex>()),
      _cond(std::make_unique<std::condition_variable>()),
      _numChunksPosted(0),
      _chunks(),
      _compression(compression)
{
    createChunk().swap(_current);
}

StoreByBucket::~StoreByBucket() = default;

void
StoreByBucket::add(BucketId bucketId, uint32_t chunkId, uint32_t lid, const void *buffer, size_t sz)
{
    if ( ! _current->hasRoom(sz)) {
        Chunk::UP tmpChunk = createChunk();
        _current.swap(tmpChunk);
        incChunksPosted();
        _executor.execute(makeLambdaTask([this, chunk=std::move(tmpChunk)]() mutable {
            closeChunk(std::move(chunk));
        }));
    }
    Index idx(bucketId, _current->getId(), chunkId, lid);
    _current->append(lid, buffer, sz);
    _where[bucketId.toKey()].push_back(idx);
}

Chunk::UP
StoreByBucket::createChunk()
{
    return std::make_unique<Chunk>(_chunkSerial++, Chunk::Config(0x10000));
}

size_t
StoreByBucket::getChunkCount() const {
    std::lock_guard guard(*_lock);
    return _chunks.size();
}

void
StoreByBucket::closeChunk(Chunk::UP chunk)
{
    vespalib::DataBuffer buffer;
    chunk->pack(1, buffer, _compression);
    buffer.shrink(buffer.getDataLen());
    ConstBufferRef bufferRef(_backingMemory.push_back(buffer.getData(), buffer.getDataLen()).data(), buffer.getDataLen());
    std::lock_guard guard(*_lock);
    _chunks[chunk->getId()] = bufferRef;
    if (_numChunksPosted == _chunks.size()) {
        _cond->notify_one();
    }
}

void
StoreByBucket::incChunksPosted() {
    std::lock_guard guard(*_lock);
    _numChunksPosted++;
}

void
StoreByBucket::waitAllProcessed() {
    std::unique_lock guard(*_lock);
    while (_numChunksPosted != _chunks.size()) {
        _cond->wait(guard);
    }
}

void
StoreByBucket::drain(IWrite & drainer)
{
    incChunksPosted();
    _executor.execute(makeLambdaTask([this, chunk=std::move(_current)]() mutable {
        closeChunk(std::move(chunk));
    }));
    waitAllProcessed();
    std::vector<Chunk::UP> chunks;
    chunks.resize(_chunks.size());
    for (const auto & it : _chunks) {
        ConstBufferRef buf(it.second);
        chunks[it.first] = std::make_unique<Chunk>(it.first, buf.data(), buf.size());
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

VESPALIB_HASH_MAP_INSTANTIATE(uint64_t, vespalib::ConstBufferRef);
