// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "compacter.h"
#include "logdatastore.h"
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/array.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docstore.compacter");

namespace search::docstore {

using vespalib::alloc::Alloc;

namespace {
    static constexpr size_t INITIAL_BACKING_BUFFER_SIZE = 64_Mi;
}

void
Compacter::write(LockGuard guard, uint32_t chunkId, uint32_t lid, ConstBufferRef data) {
    (void) chunkId;
    FileChunk::FileId fileId = _ds.getActiveFileId(guard);
    _ds.write(std::move(guard), fileId, lid, data);
}

BucketCompacter::BucketCompacter(size_t maxSignificantBucketBits, CompressionConfig compression, LogDataStore & ds,
                                 Executor & executor, const IBucketizer & bucketizer, FileId source, FileId destination) :
    _unSignificantBucketBits((maxSignificantBucketBits > 8) ? (maxSignificantBucketBits - 8) : 0),
    _sourceFileId(source),
    _destinationFileId(destination),
    _ds(ds),
    _bucketizer(bucketizer),
    _lock(),
    _backingMemory(Alloc::alloc(INITIAL_BACKING_BUFFER_SIZE), &_lock),
    _tmpStore(),
    _lidGuard(ds.getLidReadGuard()),
    _stat()
{
    for (auto & partition : _tmpStore) {
        partition = std::make_unique<StoreByBucket>(*this, _backingMemory, executor, compression);
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
    _tmpStore[toPartitionId(bucketId)]->add(bucketId, chunkId, lid, data);
}

void
BucketCompacter::store(const StoreByBucket::Index & index) {
    _where.push_back(index);
}

size_t
BucketCompacter::getBucketCount() const noexcept {
    if (_where.empty()) return 0;

    size_t count = 0;
    BucketId prev = _where.front()._bucketId;
    for (const auto & lid : _where) {
        if (lid._bucketId != prev) {
            count++;
            prev = lid._bucketId;
        }
    }
    return count + 1;
}

BucketCompacter::LidIterator::LidIterator(const BucketCompacter & bc, size_t partitionId)
    : _bc(bc),
      _partitionId(partitionId),
      _current(_bc._where.begin())
{}

bool
BucketCompacter::LidIterator::has_next() noexcept {
    for (;(_current != _bc._where.end()) && (_bc.toPartitionId(_current->_bucketId) != _partitionId); _current++);
    return (_current != _bc._where.end()) && (_bc.toPartitionId(_current->_bucketId) == _partitionId);
}

StoreByBucket::Index
BucketCompacter::LidIterator::next() noexcept {
    return *_current++;
}

void
BucketCompacter::close()
{
    size_t chunkCount(0);
    for (const auto & store : _tmpStore) {
        store->close();
        chunkCount += store->getChunkCount();
    }
    std::sort(_where.begin(), _where.end());
    LOG(info, "Have read %ld lids and placed them in %ld buckets. Temporary compressed in %ld chunks.",
              _where.size(), getBucketCount(), chunkCount);

    for (size_t partId(0); partId < _tmpStore.size(); partId++) {
        LidIterator partIterator(*this, partId);
        _tmpStore[partId]->drain(*this, partIterator);
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
