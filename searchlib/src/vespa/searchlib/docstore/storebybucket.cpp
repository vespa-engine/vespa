// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storebybucket.h"

#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/lambdatask.h>

#include <vespa/vespalib/stllike/hash_map.hpp>

#include <algorithm>
#include <cassert>

namespace search::docstore {

using document::BucketId;
using vespalib::CpuUsage;
using vespalib::makeLambdaTask;

namespace {

uint32_t calc_max_inflight_chunks() {
    return std::min(64u, std::thread::hardware_concurrency());
}

} // namespace

StoreByBucket::CompressChunksTracker::CompressChunksTracker()
    : _lock(),
      _cond(),
      _inflight_memory(0),
      _inflight_chunks(0),
      _max_inflight_memory(32_Mi),
      _max_inflight_chunks(calc_max_inflight_chunks()) {
}

StoreByBucket::CompressChunksTracker::~CompressChunksTracker() {
    assert(_inflight_memory == 0);
    assert(_inflight_chunks == 0);
}

bool StoreByBucket::CompressChunksTracker::is_full(size_t chunk_size) noexcept {
    return (_inflight_chunks > 0 &&
            (_inflight_chunks >= _max_inflight_chunks || _inflight_memory + chunk_size > _max_inflight_memory));
}

StoreByBucket::StoreByBucket(StoreIndex& storeIndex, CompressChunksTracker& compress_chunks_tracker,
                             MemoryDataStore& backingMemory, Executor& executor,
                             CompressionConfig compression) noexcept
    : _chunkSerial(0),
      _current(),
      _storeIndex(storeIndex),
      _compress_chunks_tracker(compress_chunks_tracker),
      _backingMemory(backingMemory),
      _executor(executor),
      _numChunksPosted(0),
      _chunks(),
      _compression(compression) {
    createChunk().swap(_current);
}

StoreByBucket::~StoreByBucket() = default;

void StoreByBucket::add(BucketId bucketId, uint32_t chunkId, uint32_t lid, ConstBufferRef data) {
    if (!_current->hasRoom(data.size())) {
        Chunk::UP tmpChunk = createChunk();
        _current.swap(tmpChunk);
        post_compress_chunk(std::move(tmpChunk));
    }
    _current->append(lid, data);
    _storeIndex.store(Index(bucketId, _current->getId(), chunkId, lid));
}

Chunk::UP StoreByBucket::createChunk() {
    return std::make_unique<Chunk>(_chunkSerial++, Chunk::Config(0x10000));
}

size_t StoreByBucket::getChunkCount() const noexcept {
    std::lock_guard guard(_compress_chunks_tracker._lock);
    return _chunks.size();
}

void StoreByBucket::closeChunk(Chunk::UP chunk, size_t chunk_size) {
    vespalib::DataBuffer buffer;
    chunk->pack(1, buffer, _compression);
    buffer.shrink(buffer.getDataLen());
    auto            bufferRef = _backingMemory.push_back(buffer.as_bytes());
    std::lock_guard guard(_compress_chunks_tracker._lock);
    _chunks[chunk->getId()] = bufferRef;
    _compress_chunks_tracker._inflight_memory -= chunk_size;
    --_compress_chunks_tracker._inflight_chunks;
    _compress_chunks_tracker._cond.notify_all();
}

void StoreByBucket::post_compress_chunk(Chunk::UP chunk) {
    size_t chunk_size = chunk->size();
    incChunksPosted(chunk_size);
    auto task = makeLambdaTask(
        [this, chunk = std::move(chunk), chunk_size]() mutable { closeChunk(std::move(chunk), chunk_size); });
    _executor.execute(CpuUsage::wrap(std::move(task), CpuUsage::Category::COMPACT));
}

void StoreByBucket::incChunksPosted(size_t chunk_size) {
    std::unique_lock guard(_compress_chunks_tracker._lock);
    while (_compress_chunks_tracker.is_full(chunk_size)) {
        _compress_chunks_tracker._cond.wait(guard);
    }
    _numChunksPosted++;
    _compress_chunks_tracker._inflight_memory += chunk_size;
    ++_compress_chunks_tracker._inflight_chunks;
}

void StoreByBucket::waitAllProcessed() {
    std::unique_lock guard(_compress_chunks_tracker._lock);
    while (_numChunksPosted != _chunks.size()) {
        _compress_chunks_tracker._cond.wait(guard);
    }
}

void StoreByBucket::close() {
    post_compress_chunk(std::move(_current));
    waitAllProcessed();
}

void StoreByBucket::drain(IWrite& drainer, IndexIterator& indexIterator) {
    std::vector<Chunk::UP> chunks;
    chunks.resize(_chunks.size());
    for (const auto& it : _chunks) {
        auto buf(it.second);
        chunks[it.first] = std::make_unique<Chunk>(it.first, buf.data(), buf.size());
    }
    _chunks.clear();
    while (indexIterator.has_next()) {
        Index                    idx = indexIterator.next();
        vespalib::ConstBufferRef data(chunks[idx._localChunkId]->getLid(idx._lid));
        drainer.write(idx._bucketId, idx._chunkId, idx._lid, data);
    }
}

} // namespace search::docstore

VESPALIB_HASH_MAP_INSTANTIATE(uint64_t, vespalib::ConstBufferRef);
