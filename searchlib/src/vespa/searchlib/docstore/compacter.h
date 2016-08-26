// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "filechunk.h"
#include "storebybucket.h"

namespace search {

class LogDataStore;

namespace docstore {

class Compacter : public IWriteData
{
public:
    Compacter(LogDataStore & ds) : _ds(ds) { }
    void write(LockGuard guard, uint32_t chunkId, uint32_t lid, const void *buffer, size_t sz) override;
    void close() override { }
private:
    LogDataStore & _ds;
};

class BucketCompacter : public IWriteData, public StoreByBucket::IWrite
{
public:
    using FileId = FileChunk::FileId;
    BucketCompacter(LogDataStore & ds, const IBucketizer & bucketizer, FileId source, FileId destination);
    void write(LockGuard guard, uint32_t chunkId, uint32_t lid, const void *buffer, size_t sz) override ;
    void write(BucketId bucketId, uint32_t chunkId, uint32_t lid, const void *buffer, size_t sz) override;
    void close() override;
private:
    using GenerationHandler = vespalib::GenerationHandler;
    FileId getDestinationId(const LockGuard & guard) const;
    FileId                     _sourceFileId;
    FileId                     _destinationFileId;
    LogDataStore             & _ds;
    const IBucketizer        & _bucketizer;
    uint64_t                   _writeCount;
    std::vector<StoreByBucket> _tmpStore;
    GenerationHandler::Guard   _lidGuard;
    GenerationHandler::Guard   _bucketizerGuard;
    vespalib::hash_map<uint64_t, uint32_t> _stat;
};

}
}
