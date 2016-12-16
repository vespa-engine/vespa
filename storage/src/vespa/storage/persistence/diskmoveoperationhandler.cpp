// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "diskmoveoperationhandler.h"

#include <vespa/log/log.h>
LOG_SETUP(".persistence.diskmoveoperationhandler");

namespace storage {

DiskMoveOperationHandler::DiskMoveOperationHandler(PersistenceUtil& env,
                                                   spi::PersistenceProvider& provider)
    : _env(env),
      _provider(provider)
{
}

MessageTracker::UP
DiskMoveOperationHandler::handleBucketDiskMove(BucketDiskMoveCommand& cmd,
                                               spi::Context& context)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.movedBuckets,
                                       _env._component.getClock()));

    document::BucketId bucket(cmd.getBucketId());
    uint32_t targetDisk(cmd.getDstDisk());
    uint32_t deviceIndex(_env._partition);

    if (cmd.getSrcDisk() != deviceIndex) {
        tracker->fail(api::ReturnCode::INTERNAL_FAILURE,
                     "Tried to move bucket from source disk where it was not located");
        return tracker;
    }
    if (targetDisk == deviceIndex) {
        tracker->fail(api::ReturnCode::INTERNAL_FAILURE,
                     "Tried to move bucket from and to the same disk");
        return tracker;
    }
    if (!_env._fileStorHandler.enabled(targetDisk)) {
        tracker->fail(api::ReturnCode::ABORTED, "Target disk is not available");
        return tracker;
    }

    LOG(debug, "Moving bucket %s from disk %u to disk %u.",
        bucket.toString().c_str(),
        deviceIndex, targetDisk);

    spi::Bucket from(bucket, spi::PartitionId(deviceIndex));
    spi::Bucket to(bucket, spi::PartitionId(targetDisk));

    spi::Result result(
            _provider.move(from, spi::PartitionId(targetDisk), context));
    if (result.hasError()) {
        tracker->fail(api::ReturnCode::INTERNAL_FAILURE,
                      result.getErrorMessage());
        return tracker;
    }

    api::BucketInfo bInfo = _env.getBucketInfo(to, targetDisk);
    uint32_t sourceFileSize = bInfo.getUsedFileSize();

    {
        // Grab bucket lock in bucket database, and update it
        // If entry doesn't exist, that means it has just been deleted by
        // delete bucket command. If so, it'll be deleted when delete bucket
        // is executed. moving queue should move delete command to correct disk
        StorBucketDatabase::WrappedEntry entry(
                _env.getBucketDatabase().get(
                    bucket, "FileStorThread::onBucketDiskMove",
                    StorBucketDatabase::LOCK_IF_NONEXISTING_AND_NOT_CREATING));

        // Move queued operations in bucket to new thread. Hold bucket lock
        // while doing it, so filestor manager can't put in other operations
        // first, such that operations change order.
        _env._fileStorHandler.remapQueueAfterDiskMove(bucket, deviceIndex, targetDisk);

        if (entry.exist()) {
            entry->setBucketInfo(bInfo);
            entry->disk = targetDisk;
            entry.write();
        }
    }

    // Answer message, setting extra info such as filesize
    tracker->setReply(std::shared_ptr<BucketDiskMoveReply>(
                             new BucketDiskMoveReply(
                                     cmd,
                                     bInfo,
                                     sourceFileSize,
                                     sourceFileSize)));

    return tracker;
}

} // storage
