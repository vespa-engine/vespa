// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "splitjoinhandler.h"
#include "persistenceutil.h"
#include "bucketownershipnotifier.h"
#include "splitbitdetector.h"
#include "messages.h"
#include <vespa/storage/common/bucketmessages.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/storageapi/message/bucket.h>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.splitjoinhandler");

namespace storage {

SplitJoinHandler::SplitJoinHandler(PersistenceUtil & env, spi::PersistenceProvider & spi,
                                   BucketOwnershipNotifier & notifier, bool enableMultibitSplitOptimalization)
    : _env(env),
      _spi(spi),
      _bucketOwnershipNotifier(notifier),
      _enableMultibitSplitOptimalization(enableMultibitSplitOptimalization)
{
}

MessageTracker::UP
SplitJoinHandler::handleSplitBucket(api::SplitBucketCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.splitBuckets);
    NotificationGuard notifyGuard(_bucketOwnershipNotifier);

    // Calculate the various bucket ids involved.
    if (cmd.getBucketId().getUsedBits() >= 58) {
        tracker->fail(api::ReturnCode::ILLEGAL_PARAMETERS,
                      "Can't split anymore since maximum split bits is already reached");
        return tracker;
    }
    if (cmd.getMaxSplitBits() <= cmd.getBucketId().getUsedBits()) {
        tracker->fail(api::ReturnCode::ILLEGAL_PARAMETERS,
                      "Max lit bits must be set higher than the number of bits used in the bucket to split");
        return tracker;
    }

    spi::Bucket spiBucket(cmd.getBucket());
    SplitBitDetector::Result targetInfo;
    if (_enableMultibitSplitOptimalization) {
        targetInfo = SplitBitDetector::detectSplit(_spi, spiBucket, cmd.getMaxSplitBits(),
                                                   tracker->context(), cmd.getMinDocCount(), cmd.getMinByteSize());
    }
    if (targetInfo.empty() || !_enableMultibitSplitOptimalization) {
        document::BucketId src(cmd.getBucketId());
        document::BucketId target1(src.getUsedBits() + 1, src.getId());
        document::BucketId target2(src.getUsedBits() + 1, src.getId() | (uint64_t(1) << src.getUsedBits()));
        targetInfo = SplitBitDetector::Result(target1, target2, false);
    }
    if (targetInfo.failed()) {
        tracker->fail(api::ReturnCode::INTERNAL_FAILURE, targetInfo.getReason());
        return tracker;
    }
    // If we get here, we're splitting data in two.
    // (Possibly in special case where a target will be unused)
    assert(targetInfo.success());
    document::Bucket target1(spiBucket.getBucketSpace(), targetInfo.getTarget1());
    document::Bucket target2(spiBucket.getBucketSpace(), targetInfo.getTarget2());

    LOG(debug, "split(%s -> %s, %s)", cmd.getBucketId().toString().c_str(),
        target1.getBucketId().toString().c_str(), target2.getBucketId().toString().c_str());

    PersistenceUtil::LockResult lock1(_env.lockAndGetDisk(target1));
    PersistenceUtil::LockResult lock2(_env.lockAndGetDisk(target2));

    spi::Result result = _spi.split(spiBucket, spi::Bucket(target1),
                                    spi::Bucket(target2), tracker->context());
    if (result.hasError()) {
        tracker->fail(PersistenceUtil::convertErrorCode(result), result.getErrorMessage());
        return tracker;
    }
    // After split we need to take all bucket db locks to update them.
    // Ensure to take them in rising order.
    StorBucketDatabase::WrappedEntry sourceEntry(_env.getBucketDatabase(spiBucket.getBucket().getBucketSpace()).get(
            cmd.getBucketId(), "PersistenceThread::handleSplitBucket-source"));
    auto reply = std::make_shared<api::SplitBucketReply>(cmd);
    api::SplitBucketReply & splitReply = *reply;
    tracker->setReply(std::move(reply));

    typedef std::pair<StorBucketDatabase::WrappedEntry, FileStorHandler::RemapInfo> TargetInfo;
    std::vector<TargetInfo> targets;
    for (uint32_t i = 0; i < 2; i++) {
        const document::Bucket &target(i == 0 ? target1 : target2);
        assert(target.getBucketId().getRawId() != 0);
        targets.emplace_back(_env.getBucketDatabase(target.getBucketSpace()).get(
                target.getBucketId(), "PersistenceThread::handleSplitBucket - Target",
                StorBucketDatabase::CREATE_IF_NONEXISTING),
                             FileStorHandler::RemapInfo(target));
        targets.back().first->setBucketInfo(_env.getBucketInfo(target));
    }
    if (LOG_WOULD_LOG(spam)) {
        api::BucketInfo targ1(targets[0].first->getBucketInfo());
        api::BucketInfo targ2(targets[1].first->getBucketInfo());
        LOG(spam, "split(%s - %u -> %s - %u, %s - %u)",
            cmd.getBucketId().toString().c_str(),
            targ1.getMetaCount() + targ2.getMetaCount(),
            target1.getBucketId().toString().c_str(),
            targ1.getMetaCount(),
            target2.getBucketId().toString().c_str(),
            targ2.getMetaCount());
    }
    FileStorHandler::RemapInfo source(cmd.getBucket());
    _env._fileStorHandler.remapQueueAfterSplit(source, targets[0].second, targets[1].second);
    bool ownershipChanged(!_bucketOwnershipNotifier.distributorOwns(cmd.getSourceIndex(), cmd.getBucket()));
    // Now release all the bucketdb locks.
    for (auto & target : targets) {
        if (ownershipChanged) {
            notifyGuard.notifyAlways(target.second.bucket, target.first->getBucketInfo());
        }
        // The entries vector has the source bucket in element zero, so indexing
        // that with i+1
        if (target.second.foundInQueue || target.first->getMetaCount() > 0) {
            if (target.first->getMetaCount() == 0) {
                // Fake that the bucket has content so it is not deleted.
                target.first->info.setMetaCount(1);
                // Must make sure target bucket exists when we have pending ops
                // to an empty target bucket, since the provider will have
                // implicitly erased it by this point.
                spi::Bucket createTarget(target.second.bucket);
                LOG(debug, "Split target %s was empty, but re-creating it since there are remapped operations queued to it",
                    createTarget.toString().c_str());
                _spi.createBucket(createTarget, tracker->context());
            }
            splitReply.getSplitInfo().emplace_back(target.second.bucket.getBucketId(),
                                                   target.first->getBucketInfo());
            target.first.write();
        } else {
            target.first.remove();
        }
    }
    if (sourceEntry.exist()) {
        if (ownershipChanged) {
            notifyGuard.notifyAlways(cmd.getBucket(), sourceEntry->getBucketInfo());
        }
        // Delete the old entry.
        sourceEntry.remove();
    }
    return tracker;
}

MessageTracker::UP
SplitJoinHandler::handleSetBucketState(api::SetBucketStateCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.setBucketStates);
    NotificationGuard notifyGuard(_bucketOwnershipNotifier);

    LOG(debug, "handleSetBucketState(): %s", cmd.toString().c_str());
    spi::Bucket bucket(cmd.getBucket());
    bool shouldBeActive(cmd.getState() == api::SetBucketStateCommand::ACTIVE);
    spi::BucketInfo::ActiveState newState(shouldBeActive ? spi::BucketInfo::ACTIVE : spi::BucketInfo::NOT_ACTIVE);

    spi::Result result(_spi.setActiveState(bucket, newState));
    if (tracker->checkForError(result)) {
        StorBucketDatabase::WrappedEntry
                entry = _env.getBucketDatabase(bucket.getBucket().getBucketSpace()).get(cmd.getBucketId(), "handleSetBucketState");
        if (entry.exist()) {
            entry->info.setActive(newState == spi::BucketInfo::ACTIVE);
            notifyGuard.notifyIfOwnershipChanged(cmd.getBucket(), cmd.getSourceIndex(), entry->info);
            entry.write();
        } else {
            LOG(warning, "Got OK setCurrentState result from provider for %s, "
                         "but bucket has disappeared from service layer database",
                cmd.getBucketId().toString().c_str());
        }

        tracker->setReply(std::make_shared<api::SetBucketStateReply>(cmd));
    }

    return tracker;
}

MessageTracker::UP
SplitJoinHandler::handleRecheckBucketInfo(RecheckBucketInfoCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.recheckBucketInfo);
    document::Bucket bucket(cmd.getBucket());
    api::BucketInfo info(_env.getBucketInfo(bucket));
    NotificationGuard notifyGuard(_bucketOwnershipNotifier);
    {
        // Update bucket database
        StorBucketDatabase::WrappedEntry entry(
                _env.getBucketDatabase(bucket.getBucketSpace()).get(bucket.getBucketId(), "handleRecheckBucketInfo"));

        if (entry.exist()) {
            api::BucketInfo prevInfo(entry->getBucketInfo());

            if (!(prevInfo == info)) {
                notifyGuard.notifyAlways(bucket, info);
                entry->info = info;
                entry.write();
            }
        }
        // else: there is a race condition where concurrent execution of
        // DeleteBucket in the FileStorManager and this function can cause it
        // to look like the provider has a bucket we do not know about, simply
        // because this function was executed before the actual
        // DeleteBucketCommand in the persistence thread (see ticket 6143025).
    }
    return tracker;
}

MessageTracker::UP
SplitJoinHandler::handleJoinBuckets(api::JoinBucketsCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.joinBuckets);
    if (!validateJoinCommand(cmd, *tracker)) {
        return tracker;
    }
    document::Bucket destBucket = cmd.getBucket();
    // To avoid a potential deadlock all operations locking multiple
    // buckets must lock their buckets in the same order (sort order of
    // bucket id, lowest countbits, lowest location first).
    // Sort buckets to join in order to ensure we lock in correct order
    std::sort(cmd.getSourceBuckets().begin(), cmd.getSourceBuckets().end());
    {
        // Create empty bucket for target.
        StorBucketDatabase::WrappedEntry entry =
                _env.getBucketDatabase(destBucket.getBucketSpace()).get(destBucket.getBucketId(), "join",
                                                                        StorBucketDatabase::CREATE_IF_NONEXISTING);
        entry.write();
    }

    document::Bucket firstBucket(destBucket.getBucketSpace(), cmd.getSourceBuckets()[0]);
    document::Bucket secondBucket(destBucket.getBucketSpace(), cmd.getSourceBuckets()[1]);

    PersistenceUtil::LockResult lock1(_env.lockAndGetDisk(firstBucket));
    PersistenceUtil::LockResult lock2;
    if (firstBucket != secondBucket) {
        lock2 = _env.lockAndGetDisk(secondBucket);
    }

    spi::Result result =
            _spi.join(spi::Bucket(firstBucket),
                      spi::Bucket(secondBucket),
                      spi::Bucket(destBucket),
                      tracker->context());
    if (!tracker->checkForError(result)) {
        return tracker;
    }
    uint64_t lastModified = 0;
    for (uint32_t i = 0; i < cmd.getSourceBuckets().size(); i++) {
        document::Bucket srcBucket(destBucket.getBucketSpace(), cmd.getSourceBuckets()[i]);
        FileStorHandler::RemapInfo target(cmd.getBucket());
        _env._fileStorHandler.remapQueueAfterJoin(FileStorHandler::RemapInfo(srcBucket), target);
        // Remove source from bucket db.
        StorBucketDatabase::WrappedEntry entry =
                _env.getBucketDatabase(srcBucket.getBucketSpace()).get(srcBucket.getBucketId(), "join-remove-source");
        if (entry.exist()) {
            lastModified = std::max(lastModified, entry->info.getLastModified());
            entry.remove();
        }
    }
    {
        StorBucketDatabase::WrappedEntry entry =
                _env.getBucketDatabase(destBucket.getBucketSpace()).get(destBucket.getBucketId(), "join",
                                                                        StorBucketDatabase::CREATE_IF_NONEXISTING);
        if (entry->info.getLastModified() == 0) {
            entry->info.setLastModified(std::max(lastModified, entry->info.getLastModified()));
        }
        entry.write();
    }
    return tracker;
}

bool
SplitJoinHandler::validateJoinCommand(const api::JoinBucketsCommand& cmd, MessageTracker& tracker)
{
    if (cmd.getSourceBuckets().size() != 2) {
        tracker.fail(api::ReturnCode::ILLEGAL_PARAMETERS,
                     "Join needs exactly two buckets to be joined together" + cmd.getBucketId().toString());
        return false;
    }
    // Verify that source and target buckets look sane.
    for (uint32_t i = 0; i < cmd.getSourceBuckets().size(); i++) {
        if (cmd.getSourceBuckets()[i] == cmd.getBucketId()) {
            tracker.fail(api::ReturnCode::ILLEGAL_PARAMETERS,
                         "Join had both source and target bucket " + cmd.getBucketId().toString());
            return false;
        }
        if (!cmd.getBucketId().contains(cmd.getSourceBuckets()[i])) {
            tracker.fail(api::ReturnCode::ILLEGAL_PARAMETERS,
                         "Source bucket " + cmd.getSourceBuckets()[i].toString()
                         + " is not contained in target " + cmd.getBucketId().toString());
            return false;
        }
    }
    return true;
}

}
