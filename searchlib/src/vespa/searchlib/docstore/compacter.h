// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "filechunk.h"
#include "storebybucket.h"
#include <vespa/vespalib/data/memorydatastore.h>

namespace search { class LogDataStore; }

namespace search::docstore {

/**
 * A simple write through implementation of the IWriteData interface.
 */
class Compacter : public IWriteData
{
public:
    Compacter(LogDataStore & ds) : _ds(ds) { }
    void write(LockGuard guard, uint32_t chunkId, uint32_t lid, ConstBufferRef data) override;
    void close() override { }

private:
    LogDataStore & _ds;
};

/**
 * This will split the incoming data into buckets.
 * The buckets data will then be written out in bucket order.
 * The buckets will be ordered, and the objects inside the buckets will be further ordered.
 * All data are kept compressed to minimize memory usage.
 **/
class BucketCompacter : public IWriteData, public StoreByBucket::IWrite
{
    using CompressionConfig = vespalib::compression::CompressionConfig;
    using Executor = vespalib::Executor;
public:
    using FileId = FileChunk::FileId;
    BucketCompacter(size_t maxSignificantBucketBits, CompressionConfig compression, LogDataStore & ds,
                    Executor & executor, const IBucketizer & bucketizer, FileId source, FileId destination);
    void write(LockGuard guard, uint32_t chunkId, uint32_t lid, ConstBufferRef data) override;
    void write(BucketId bucketId, uint32_t chunkId, uint32_t lid, ConstBufferRef data) override;
    void close() override;
private:
    static constexpr size_t NUM_PARTITIONS = 256;
    using GenerationHandler = vespalib::GenerationHandler;
    using Partitions = std::array<std::unique_ptr<StoreByBucket>, NUM_PARTITIONS>;
    FileId getDestinationId(const LockGuard & guard) const;
    size_t                     _unSignificantBucketBits;
    FileId                     _sourceFileId;
    FileId                     _destinationFileId;
    LogDataStore             & _ds;
    const IBucketizer        & _bucketizer;
    std::mutex                 _lock;
    vespalib::MemoryDataStore  _backingMemory;
    Partitions                 _tmpStore;
    GenerationHandler::Guard   _lidGuard;
    vespalib::hash_map<uint64_t, uint32_t> _stat;
};

}
