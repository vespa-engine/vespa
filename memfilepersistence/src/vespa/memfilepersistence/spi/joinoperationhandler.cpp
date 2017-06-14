// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "joinoperationhandler.h"
#include "cacheevictionguard.h"
#include <vespa/memfilepersistence/mapper/memfilemapper.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.memfile.handler.join");

namespace storage {
namespace memfile {

JoinOperationHandler::JoinOperationHandler(Environment& env)
    : OperationHandler(env),
      _env(env)
{
}

void
JoinOperationHandler::copySlots(MemFile& sourceFile, MemFile& targetFile)
{
    sourceFile.ensureBodyBlockCached();
    LOG(spam,
        "Moving data from %s to %s",
        sourceFile.toString().c_str(),
        targetFile.toString().c_str());

    std::vector<const MemSlot*> slotsToCopy;
    slotsToCopy.reserve(sourceFile.getSlotCount());

    for (uint32_t j = 0; j < sourceFile.getSlotCount(); j++) {
        const MemSlot* slot(&sourceFile[j]);

        if (!targetFile.getSlotAtTime(slot->getTimestamp())) {
            slotsToCopy.push_back(slot);
        }
    }
    targetFile.copySlotsFrom(sourceFile, slotsToCopy);
    LOG(spam, "Moved data from %s to %s",
        sourceFile.toString().c_str(), targetFile.toString().c_str());
}

spi::Result
JoinOperationHandler::join(
        const spi::Bucket& source1,
        const spi::Bucket& source2,
        const spi::Bucket& target)
{
    if ((source1.getBucketId() == source2.getBucketId())
        && (target.getBucketId() == source1.getBucketId()))
    {
        return singleJoin(source1, target);
    }

    MemFileCacheEvictionGuard targetFile(
            getMemFile(target.getBucketId(), target.getPartition(), false));

    std::vector<spi::Bucket> sources;
    sources.push_back(source1);
    if (source1.getBucketId() != source2.getBucketId()) {
        sources.push_back(source2);
    }

    for (uint32_t i = 0; i < sources.size(); i++) {
        MemFileCacheEvictionGuard sourceFile(
                getMemFile(sources[i].getBucketId(),
                           sources[i].getPartition(),
                           false));

        if (targetFile->empty()) {
            LOG(spam, "Renaming %s to %s",
                sourceFile->toString().c_str(), targetFile->toString().c_str());
            // It is assumed that if this fails, the nature of the exception is
            // such that it will cause the disk to automatically be marked as
            // down and for the process to restart, meaning we should not get
            // out of sync between the service and persistence layers.
            sourceFile.get().move(targetFile.get());
        } else {
            copySlots(*sourceFile, *targetFile);
            targetFile->flushToDisk();
            sourceFile.get().deleteFile();
        }
        sourceFile.unguard();
    }
    targetFile.unguard();

    return spi::Result();
}

void
JoinOperationHandler::clearBucketFromCache(const spi::Bucket& bucket)
{
    getMemFile(bucket.getBucketId(), bucket.getPartition(), false)
            .eraseFromCache();
}

/*
 * Moving same bucket between partitions, potentially joining data
 * if target file already exists.
 */
spi::Result
JoinOperationHandler::singleJoin(
        const spi::Bucket& source,
        const spi::Bucket& target)
{
    assert(source.getBucketId() == target.getBucketId());
    assert(source.getPartition() != target.getPartition());
    // Internal joins sidestep the cache completely, so we have to ensure
    // the bucket is cleared from it before commencing. Otherwise, it's
    // possible that the cached file offsets will not reflect what's actually
    // stored on disk, leading to potential data corruption! The bucket shall
    // not have been taken out of the cache before this point.
    clearBucketFromCache(target);

    Directory& toJoinDir = _env.getDirectory(source.getPartition());
    FileSpecification toJoinSpec(
            source.getBucketId(), toJoinDir,
            _env.calculatePathInDir(source.getBucketId(), toJoinDir));

    MemFile toJoin(toJoinSpec, _env);

    Directory& toKeepDir = _env.getDirectory(target.getPartition());
    FileSpecification toKeepSpec(
            source.getBucketId(), toKeepDir,
            _env.calculatePathInDir(source.getBucketId(), toKeepDir));
    assert(toJoinDir != toKeepDir);

    const double maxFillRate(
            _env.acquireConfigReadLock().memFilePersistenceConfig()
                ->diskFullFactorMove);
    if (source.getPartition() != target.getPartition() &&
        toKeepDir.isFull(0, maxFillRate))
    {
        std::string failure =
            vespalib::make_string("Not moving bucket %s to directory %s because it's "
                                  "fill rate is %G (>%G)",
                                  source.getBucketId().toString().c_str(),
                                  toKeepDir.toString().c_str(),
                                  toKeepDir.getPartition().getMonitor()->getFillRate(),
                                  maxFillRate);

        LOG(debug, "%s", failure.c_str());

        return spi::Result(spi::Result::TRANSIENT_ERROR, failure);
    }

    MemFile toKeep(toKeepSpec, _env);

    copySlots(toJoin, toKeep);
    toKeep.flushToDisk();

    // Delete original file.
    _env._memFileMapper.deleteFile(toJoin, _env);

    return spi::Result();
}

} // memfile
} // storage
