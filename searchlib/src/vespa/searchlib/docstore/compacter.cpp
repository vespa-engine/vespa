// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compacter.h"
#include "logdatastore.h"
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/array.hpp>
#include <cassert>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docstore.compacter");

namespace search::docstore {

using vespalib::alloc::Alloc;

namespace {
    constexpr size_t INITIAL_BACKING_BUFFER_SIZE = 64_Mi;
}

void
Compacter::write(LockGuard guard, uint32_t chunkId, uint32_t lid, ConstBufferRef data) {
    (void) chunkId;
    FileChunk::FileId fileId = _ds.getActiveFileId(guard);
    _ds.write(std::move(guard), fileId, lid, data);
}

BucketIndexStore::BucketIndexStore(size_t maxSignificantBucketBits, uint32_t numPartitions) noexcept
    : _inSignificantBucketBits((maxSignificantBucketBits > 8) ? (maxSignificantBucketBits - 8) : 0),
      _where(),
      _numPartitions(numPartitions),
      _readyForIterate(true)
{}
BucketIndexStore::~BucketIndexStore() = default;

void
BucketIndexStore::prepareForIterate() {
    std::sort(_where.begin(), _where.end());
    _readyForIterate = true;
}

void
BucketIndexStore::store(const StoreByBucket::Index & index) {
    _where.push_back(index);
    _readyForIterate = false;
}

size_t
BucketIndexStore::getBucketCount() const noexcept {
    if (_where.empty()) return 0;

    size_t count = 0;
    document::BucketId prev = _where.front()._bucketId;
    for (const auto & lid : _where) {
        if (lid._bucketId != prev) {
            count++;
            prev = lid._bucketId;
        }
    }
    return count + 1;
}

std::unique_ptr<StoreByBucket::IndexIterator>
BucketIndexStore::createIterator(uint32_t partitionId) const {
    assert(_readyForIterate);
    return std::make_unique<LidIterator>(*this, partitionId);
}

BucketIndexStore::LidIterator::LidIterator(const BucketIndexStore & store, size_t partitionId)
    : _store(store),
      _partitionId(partitionId),
      _current(_store._where.begin())
{}

bool
BucketIndexStore::LidIterator::has_next() noexcept {
    for (;(_current != _store._where.end()) && (_store.toPartitionId(_current->_bucketId) != _partitionId); _current++);
    return (_current != _store._where.end()) && (_store.toPartitionId(_current->_bucketId) == _partitionId);
}

StoreByBucket::Index
BucketIndexStore::LidIterator::next() noexcept {
    return *_current++;
}

BucketCompacter::BucketCompacter(size_t maxSignificantBucketBits, CompressionConfig compression, LogDataStore & ds,
                                 Executor & executor, const IBucketizer & bucketizer, FileId source, FileId destination)
    : _sourceFileId(source),
      _destinationFileId(destination),
      _ds(ds),
      _bucketizer(bucketizer),
      _lock(),
      _backingMemory(Alloc::alloc(INITIAL_BACKING_BUFFER_SIZE), &_lock),
      _bucketIndexStore(maxSignificantBucketBits, NUM_PARTITIONS),
      _tmpStore(),
      _lidGuard(ds.getLidReadGuard()),
      _stat()
{
    for (auto & partition : _tmpStore) {
        partition = std::make_unique<StoreByBucket>(_bucketIndexStore, _backingMemory, executor, compression);
    }
}

BucketCompacter::~BucketCompacter() = default;

FileChunk::FileId
BucketCompacter::getDestinationId(const LockGuard & guard) const {
    return (_destinationFileId.isActive()) ? _ds.getActiveFileId(guard) : _destinationFileId;
}

void
BucketCompacter::write(LockGuard guard, uint32_t chunkId, uint32_t lid, ConstBufferRef data)
{
    guard.unlock();
    BucketId bucketId = (data.size() > 0) ? _bucketizer.getBucketOf(_bucketizer.getGuard(), lid) : BucketId();
    _tmpStore[_bucketIndexStore.toPartitionId(bucketId)]->add(bucketId, chunkId, lid, data);
}

void
BucketCompacter::close()
{
    size_t chunkCount(0);
    for (const auto & store : _tmpStore) {
        store->close();
        chunkCount += store->getChunkCount();
    }
    _bucketIndexStore.prepareForIterate();
    LOG(info, "Have read %ld lids and placed them in %ld buckets. Temporary compressed in %ld chunks.",
              _bucketIndexStore.getLidCount(), _bucketIndexStore.getBucketCount(), chunkCount);

    for (size_t partId(0); partId < _tmpStore.size(); partId++) {
        auto partIterator = _bucketIndexStore.createIterator(partId);
        _tmpStore[partId]->drain(*this, *partIterator);
    }
    // All partitions using _backingMemory should be destructed before clearing.
    _backingMemory.clear();

    size_t lidCount(0);
    for (const auto & it : _stat) {
        lidCount += it.second;
    }
    LOG(info, "Compacted %ld lids into %ld buckets", lidCount, _stat.size());
}

void
BucketCompacter::write(BucketId bucketId, uint32_t chunkId, uint32_t lid, ConstBufferRef data)
{
    _stat[bucketId.getId()]++;
    LockGuard guard(_ds.getLidGuard(lid));
    LidInfo lidInfo(_sourceFileId.getId(), chunkId, data.size());
    if (_ds.getLid(_lidGuard, lid) == lidInfo) {
        FileId fileId = getDestinationId(guard);
        _ds.write(std::move(guard), fileId, lid, data);
    }
}

}
