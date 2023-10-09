// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        partition = std::make_unique<StoreByBucket>(_backingMemory, executor, compression);
    }
}

FileChunk::FileId
BucketCompacter::getDestinationId(const LockGuard & guard) const {
    return (_destinationFileId.isActive()) ? _ds.getActiveFileId(guard) : _destinationFileId;
}

void
BucketCompacter::write(LockGuard guard, uint32_t chunkId, uint32_t lid, ConstBufferRef data)
{
    guard.unlock();
    BucketId bucketId = (data.size() > 0) ? _bucketizer.getBucketOf(_bucketizer.getGuard(), lid) : BucketId();
    uint64_t sortableBucketId = bucketId.toKey();
    _tmpStore[(sortableBucketId >> _unSignificantBucketBits) % _tmpStore.size()]->add(bucketId, chunkId, lid, data);
}

void
BucketCompacter::close()
{
    size_t lidCount1(0);
    size_t bucketCount(0);
    size_t chunkCount(0);
    for (const auto & store : _tmpStore) {
        store->close();
        lidCount1 += store->getLidCount();
        bucketCount += store->getBucketCount();
        chunkCount += store->getChunkCount();
    }
    LOG(info, "Have read %ld lids and placed them in %ld buckets. Temporary compressed in %ld chunks.",
              lidCount1, bucketCount, chunkCount);

    for (auto & store_ref : _tmpStore) {
        auto store = std::move(store_ref);
        store->drain(*this);
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
