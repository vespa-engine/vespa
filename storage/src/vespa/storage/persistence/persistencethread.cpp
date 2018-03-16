// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistencethread.h"
#include "splitbitdetector.h"
#include "bucketownershipnotifier.h"
#include "testandsethelper.h"
#include <vespa/storageapi/message/multioperation.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storage/common/bucketoperationlogger.h>
#include <vespa/document/fieldset/fieldsetrepo.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vespalib/util/exceptions.h>
#include <algorithm>

#include <vespa/log/bufferedlogger.h>
LOG_SETUP(".persistence.thread");

namespace storage {

PersistenceThread::PersistenceThread(ServiceLayerComponentRegister& compReg,
                                     const config::ConfigUri & configUri,
                                     spi::PersistenceProvider& provider,
                                     FileStorHandler& filestorHandler,
                                     FileStorThreadMetrics& metrics,
                                     uint16_t deviceIndex)
    : _env(configUri, compReg, filestorHandler, metrics, deviceIndex, provider),
      _warnOnSlowOperations(5000),
      _spi(provider),
      _processAllHandler(_env, provider),
      _mergeHandler(_spi, _env),
      _diskMoveHandler(_env, _spi),
      _context(documentapi::LoadType::DEFAULT, 0, 0),
      _bucketOwnershipNotifier(),
      _flushMonitor(),
      _closed(false)
{
    std::ostringstream threadName;
    threadName << "Disk " << _env._partition << " thread " << (void*) this;
    _component.reset(new ServiceLayerComponent(compReg, threadName.str()));
    _bucketOwnershipNotifier.reset(new BucketOwnershipNotifier(*_component, filestorHandler));
    framework::MilliSecTime maxProcessingTime(60 * 1000);
    framework::MilliSecTime waitTime(1000);
    _thread = _component->startThread(*this, maxProcessingTime, waitTime);
}

PersistenceThread::~PersistenceThread()
{
    LOG(debug, "Shutting down persistence thread. Waiting for current operation to finish.");
    _thread->interrupt();
    LOG(debug, "Waiting for thread to terminate.");
    _thread->join();
    LOG(debug, "Persistence thread done with destruction");
}

spi::Bucket
PersistenceThread::getBucket(const DocumentId& id, const document::Bucket &bucket) const
{
    BucketId docBucket(_env._bucketFactory.getBucketId(id));
    docBucket.setUsedBits(bucket.getBucketId().getUsedBits());
    if (bucket.getBucketId() != docBucket) {
        docBucket = _env._bucketFactory.getBucketId(id);
        throw vespalib::IllegalStateException("Document " + id.toString()
                + " (bucket " + docBucket.toString() + ") does not belong in "
                + "bucket " + bucket.getBucketId().toString() + ".", VESPA_STRLOC);
    }

    return spi::Bucket(bucket, spi::PartitionId(_env._partition));
}

bool
PersistenceThread::checkForError(const spi::Result& response, MessageTracker& tracker)
{
    uint32_t code = _env.convertErrorCode(response);

    if (code != 0) {
        tracker.fail(code, response.getErrorMessage());
        return false;
    }

    return true;
}


bool PersistenceThread::tasConditionExists(const api::TestAndSetCommand & cmd) {
    return cmd.getCondition().isPresent();
}

bool PersistenceThread::tasConditionMatches(const api::TestAndSetCommand & cmd, MessageTracker & tracker) {
    try {
        TestAndSetHelper helper(*this, cmd);

        auto code = helper.retrieveAndMatch();
        if (code.failed()) {
            tracker.fail(code.getResult(), code.getMessage());
            return false;
        }
    } catch (const TestAndSetException & e) {
        auto code = e.getCode();
        tracker.fail(code.getResult(), code.getMessage());
        return false;
    }

    return true;
}

MessageTracker::UP
PersistenceThread::handlePut(api::PutCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.put[cmd.getLoadType()],
                                       _env._component.getClock()));

    if (tasConditionExists(cmd) && !tasConditionMatches(cmd, *tracker)) {
        return tracker;
    }

    spi::Result response =
        _spi.put(getBucket(cmd.getDocumentId(), cmd.getBucket()),
                 spi::Timestamp(cmd.getTimestamp()),
                 cmd.getDocument(),
                 _context);
    checkForError(response, *tracker);
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleRemove(api::RemoveCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.remove[cmd.getLoadType()],
                                       _env._component.getClock()));

    if (tasConditionExists(cmd) && !tasConditionMatches(cmd, *tracker)) {
        return tracker;
    }

    spi::RemoveResult response =
        _spi.removeIfFound(getBucket(cmd.getDocumentId(), cmd.getBucket()),
                    spi::Timestamp(cmd.getTimestamp()),
                    cmd.getDocumentId(), _context);
    if (checkForError(response, *tracker)) {
        tracker->setReply(std::make_shared<api::RemoveReply>(cmd, response.wasFound() ? cmd.getTimestamp() : 0));
    }
    if (!response.wasFound()) {
        ++_env._metrics.remove[cmd.getLoadType()].notFound;
    }
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleUpdate(api::UpdateCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.update[cmd.getLoadType()],
                                       _env._component.getClock()));

    if (tasConditionExists(cmd) && !tasConditionMatches(cmd, *tracker)) {
        return tracker;
    }
    
    spi::UpdateResult response =
        _spi.update(getBucket(cmd.getUpdate()->getId(), cmd.getBucket()),
                    spi::Timestamp(cmd.getTimestamp()),
                    cmd.getUpdate(), _context);
    if (checkForError(response, *tracker)) {
        auto reply = std::make_shared<api::UpdateReply>(cmd);
        reply->setOldTimestamp(response.getExistingTimestamp());
        tracker->setReply(std::move(reply));
    }
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleGet(api::GetCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.get[cmd.getLoadType()],
                                       _env._component.getClock()));

    document::FieldSetRepo repo;
    document::FieldSet::UP fieldSet = repo.parse(*_env._component.getTypeRepo(),
                                                 cmd.getFieldSet());
    spi::GetResult result =
        _spi.get(getBucket(cmd.getDocumentId(), cmd.getBucket()),
                 *fieldSet,
                 cmd.getDocumentId(),
                 _context);

    if (checkForError(result, *tracker)) {
        if (!result.hasDocument()) {
            ++_env._metrics.get[cmd.getLoadType()].notFound;
        }

        api::GetReply::UP reply(
                new api::GetReply(cmd,
                                  Document::SP(result.getDocumentPtr()),
                                  result.getTimestamp()));

        tracker->setReply(api::StorageReply::SP(reply.release()));
    }

    return tracker;
}

MessageTracker::UP
PersistenceThread::handleRepairBucket(RepairBucketCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.repairs,
                                       _env._component.getClock()));
    NotificationGuard notifyGuard(*_bucketOwnershipNotifier);
    LOG(debug, "Repair(%s): %s",
        cmd.getBucketId().toString().c_str(),
        (cmd.verifyBody() ? "Verifying body" : "Not verifying body"));
    api::BucketInfo before = _env.getBucketInfo(cmd.getBucket());
    spi::Result result =
        _spi.maintain(spi::Bucket(cmd.getBucket(),
                                  spi::PartitionId(_env._partition)),
                      cmd.verifyBody() ?
                      spi::HIGH : spi::LOW);
    if (checkForError(result, *tracker)) {
        api::BucketInfo after = _env.getBucketInfo(cmd.getBucket());

        RepairBucketReply::UP reply(new RepairBucketReply(cmd, after));
        reply->setAltered(!(after == before));
        if (reply->bucketAltered()) {
            notifyGuard.notifyAlways(cmd.getBucket(), after);
            ++_env._metrics.repairFixed;
        }

        _env.updateBucketDatabase(cmd.getBucket(), after);
        tracker->setReply(api::StorageReply::SP(reply.release()));
    }
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleMultiOperation(api::MultiOperationCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.multiOp[cmd.getLoadType()],
                                       _env._component.getClock()));
    spi::Bucket b = spi::Bucket(cmd.getBucket(), spi::PartitionId(_env._partition));
    long puts = 0;
    long removes = 0;
    long updates = 0;
    long updatesNotFound = 0;
    long removesNotFound = 0;
    for (vdslib::DocumentList::const_iterator it =
             cmd.getOperations().begin();
         it != cmd.getOperations().end(); ++it)
    {
        document::DocumentId docId = it->getDocumentId();
        if (it->isRemoveEntry()) {
            ++removes;
            spi::RemoveResult result = _spi.removeIfFound(
                    b,
                    spi::Timestamp(it->getTimestamp()),
                    docId, _context);
            if (!checkForError(result, *tracker)) {
                return tracker;
            }
            if (!result.wasFound()) {
                LOG(debug, "Cannot remove %s; document not found",
                    docId.toString().c_str());
                ++removesNotFound;
            }
        } else if (it->isUpdateEntry()) {
            ++updates;
            document::DocumentUpdate::SP docUpdate = it->getUpdate();
            spi::UpdateResult result =
                _spi.update(b, spi::Timestamp(it->getTimestamp()), docUpdate,
                            _context);
            if (!checkForError(result, *tracker)) {
                return tracker;
            }
            if (result.getExistingTimestamp() == 0) {
                ++updatesNotFound;
            }
        } else {
            ++puts;
            document::Document::SP doc = it->getDocument();
            spi::Result result = _spi.put(b, spi::Timestamp(it->getTimestamp()),
                                          doc, _context);
            if (!checkForError(result, *tracker)) {
                return tracker;
            }
        }
    }
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleRevert(api::RevertCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.revert[cmd.getLoadType()],
                                       _env._component.getClock()));
    spi::Bucket b = spi::Bucket(cmd.getBucket(), spi::PartitionId(_env._partition));
    const std::vector<api::Timestamp> & tokens = cmd.getRevertTokens();
    for (const api::Timestamp & token : tokens) {
        spi::Result result = _spi.removeEntry(b, spi::Timestamp(token), _context);
    }
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleCreateBucket(api::CreateBucketCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.createBuckets,
                                       _env._component.getClock()));
    LOG(debug, "CreateBucket(%s)", cmd.getBucketId().toString().c_str());
    if (_env._fileStorHandler.isMerging(cmd.getBucket())) {
        LOG(warning, "Bucket %s was merging at create time. Unexpected.",
            cmd.getBucketId().toString().c_str());
        DUMP_LOGGED_BUCKET_OPERATIONS(cmd.getBucketId());
    }
    spi::Bucket spiBucket(cmd.getBucket(), spi::PartitionId(_env._partition));
    _spi.createBucket(spiBucket, _context);
    if (cmd.getActive()) {
        _spi.setActiveState(spiBucket, spi::BucketInfo::ACTIVE);
    }
    return tracker;
}

namespace {

bool bucketStatesAreSemanticallyEqual(const api::BucketInfo& a, const api::BucketInfo& b) {
    // Don't check document sizes, as background moving of documents in Proton
    // may trigger a change in size without any mutations taking place. This will
    // only take place when a document being moved was fed _prior_ to the change
    // where Proton starts reporting actual document sizes, and will eventually
    // converge to a stable value. But for now, ignore it to prevent false positive
    // error logs and non-deleted buckets.
    return ((a.getChecksum() == b.getChecksum())
            && (a.getDocumentCount() == b.getDocumentCount()));
}

}

bool
PersistenceThread::checkProviderBucketInfoMatches(const spi::Bucket& bucket,
                                                  const api::BucketInfo& info) const
{
    spi::BucketInfoResult result(_spi.getBucketInfo(bucket));
    if (result.hasError()) {
        LOG(error,
            "getBucketInfo(%s) failed before deleting bucket; got error '%s'",
            bucket.toString().c_str(),
            result.getErrorMessage().c_str());
        return false;
    }
    api::BucketInfo providerInfo(
            _env.convertBucketInfo(result.getBucketInfo()));
    // Don't check meta fields or active/ready fields since these are not
    // that important and ready may change under the hood in a race with
    // getModifiedBuckets(). If bucket is empty it means it has already
    // been deleted by a racing split/join.
    if (!bucketStatesAreSemanticallyEqual(info, providerInfo) && !providerInfo.empty()) {
        LOG(error,
            "Service layer bucket database and provider out of sync before "
            "deleting bucket %s! Service layer db had %s while provider says "
            "bucket has %s. Deletion has been rejected to ensure data is not "
            "lost, but bucket may remain out of sync until service has been "
            "restarted.",
            bucket.toString().c_str(),
            info.toString().c_str(),
            providerInfo.toString().c_str());
        return false;
    }
    return true;
}

MessageTracker::UP
PersistenceThread::handleDeleteBucket(api::DeleteBucketCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.deleteBuckets,
                                       _env._component.getClock()));
    LOG(debug, "DeletingBucket(%s)", cmd.getBucketId().toString().c_str());
    LOG_BUCKET_OPERATION(cmd.getBucketId(), "deleteBucket()");
    if (_env._fileStorHandler.isMerging(cmd.getBucket())) {
        _env._fileStorHandler.clearMergeStatus(cmd.getBucket(),
                api::ReturnCode(api::ReturnCode::ABORTED,
                                "Bucket was deleted during the merge"));
    }
    spi::Bucket bucket(cmd.getBucket(), spi::PartitionId(_env._partition));
    if (!checkProviderBucketInfoMatches(bucket, cmd.getBucketInfo())) {
           return tracker;
    }
    _spi.deleteBucket(bucket, _context);
    StorBucketDatabase& db(_env.getBucketDatabase(cmd.getBucket().getBucketSpace()));
    {
        StorBucketDatabase::WrappedEntry entry(db.get(
                    cmd.getBucketId(), "FileStorThread::onDeleteBucket"));
        if (entry.exist() && entry->getMetaCount() > 0) {
            LOG(debug, "onDeleteBucket(%s): Bucket DB entry existed. Likely "
                       "active operation when delete bucket was queued. "
                       "Updating bucket database to keep it in sync with file. "
                       "Cannot delete bucket from bucket database at this "
                       "point, as it can have been intentionally recreated "
                       "after delete bucket had been sent",
                cmd.getBucketId().toString().c_str());
            api::BucketInfo info(0, 0, 0);
            // Only set document counts/size; retain ready/active state.
            info.setReady(entry->getBucketInfo().isReady());
            info.setActive(entry->getBucketInfo().isActive());

            entry->setBucketInfo(info);
            entry.write();
        }
    }
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleGetIter(GetIterCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.visit[cmd.getLoadType()],
                                       _env._component.getClock()));
    spi::IterateResult result(_spi.iterate(cmd.getIteratorId(),
                                           cmd.getMaxByteSize(), _context));
    if (checkForError(result, *tracker)) {
        GetIterReply::SP reply(new GetIterReply(cmd));
        reply->getEntries() = result.steal_entries();
        _env._metrics.visit[cmd.getLoadType()].
            documentsPerIterate.addValue(reply->getEntries().size());
        if (result.isCompleted()) {
            reply->setCompleted();
        }
        tracker->setReply(reply);
    }
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleReadBucketList(ReadBucketList& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.readBucketList,
                                       _env._component.getClock()));

    spi::BucketIdListResult result(_spi.listBuckets(cmd.getBucketSpace(), cmd.getPartition()));
    if (checkForError(result, *tracker)) {
        ReadBucketListReply::SP reply(new ReadBucketListReply(cmd));
        result.getList().swap(reply->getBuckets());
        tracker->setReply(reply);
    }

    return tracker;
}

MessageTracker::UP
PersistenceThread::handleReadBucketInfo(ReadBucketInfo& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.readBucketInfo,
                                       _env._component.getClock()));

    _env.updateBucketDatabase(cmd.getBucket(),
                              _env.getBucketInfo(cmd.getBucket()));
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleCreateIterator(CreateIteratorCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.createIterator,
                                       _env._component.getClock()));
    document::FieldSetRepo repo;
    document::FieldSet::UP fieldSet = repo.parse(*_env._component.getTypeRepo(),
                                                 cmd.getFields());
    // _context is reset per command, so it's safe to modify it like this.
    _context.setReadConsistency(cmd.getReadConsistency());
    spi::CreateIteratorResult result(_spi.createIterator(
        spi::Bucket(cmd.getBucket(), spi::PartitionId(_env._partition)),
            *fieldSet,
            cmd.getSelection(),
            cmd.getIncludedVersions(),
            _context));
    if (checkForError(result, *tracker)) {
        tracker->setReply(std::make_shared<CreateIteratorReply>(cmd, spi::IteratorId(result.getIteratorId())));
    }
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleSplitBucket(api::SplitBucketCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.splitBuckets,
                                       _env._component.getClock()));
    NotificationGuard notifyGuard(*_bucketOwnershipNotifier);

    // Calculate the various bucket ids involved.
    if (cmd.getBucketId().getUsedBits() >= 58) {
        tracker->fail(
                api::ReturnCode::ILLEGAL_PARAMETERS,
                "Can't split anymore since maximum split bits "
                "is already reached");
        return tracker;
    }
    if (cmd.getMaxSplitBits() <= cmd.getBucketId().getUsedBits()) {
        tracker->fail(api::ReturnCode::ILLEGAL_PARAMETERS,
                     "Max lit bits must be set higher "
                     "than the number of bits used in the bucket to split");
        return tracker;
    }

    spi::Bucket spiBucket(cmd.getBucket(), spi::PartitionId(_env._partition));
    SplitBitDetector::Result targetInfo;
    if (_env._config.enableMultibitSplitOptimalization) {
        targetInfo = SplitBitDetector::detectSplit(
                _spi, spiBucket, cmd.getMaxSplitBits(),
                _context, cmd.getMinDocCount(), cmd.getMinByteSize());
    }
    if (targetInfo.empty() || !_env._config.enableMultibitSplitOptimalization) {
        document::BucketId src(cmd.getBucketId());
        document::BucketId target1(src.getUsedBits() + 1, src.getId());
        document::BucketId target2(src.getUsedBits() + 1, src.getId()
                                    | (uint64_t(1) << src.getUsedBits()));
        targetInfo = SplitBitDetector::Result(target1, target2, false);
    }
    if (targetInfo.failed()) {
        tracker->fail(api::ReturnCode::INTERNAL_FAILURE,
                      targetInfo.getReason());
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

#ifdef ENABLE_BUCKET_OPERATION_LOGGING
    {
        vespalib::string desc(
                vespalib::make_string(
                        "split(%s -> %s, %s)",
                        cmd.getBucketId().toString().c_str(),
                        target1.getBucketId().toString().c_str(),
                        target2.getBucketId().toString().c_str()));
        LOG_BUCKET_OPERATION(cmd.getBucketId(), desc);
        LOG_BUCKET_OPERATION(target1.getBucketId(), desc);
        if (target2.getRawId() != 0) {
            LOG_BUCKET_OPERATION(target2.getBucketId(), desc);
        }
    }
#endif
    spi::Result result = _spi.split(
            spiBucket,
            spi::Bucket(target1, spi::PartitionId(lock1.disk)),
            spi::Bucket(target2, spi::PartitionId(lock2.disk)), _context);
    if (result.hasError()) {
        tracker->fail(_env.convertErrorCode(result),
                      result.getErrorMessage());
        return tracker;
    }
        // After split we need to take all bucket db locks to update them.
        // Ensure to take them in rising order.
    StorBucketDatabase::WrappedEntry sourceEntry(_env.getBucketDatabase(spiBucket.getBucket().getBucketSpace()).get(
            cmd.getBucketId(), "PersistenceThread::handleSplitBucket-source"));
    auto reply = std::make_shared<api::SplitBucketReply>(cmd);
    api::SplitBucketReply & splitReply = *reply;
    tracker->setReply(std::move(reply));

    typedef std::pair<StorBucketDatabase::WrappedEntry,
                      FileStorHandler::RemapInfo> TargetInfo;
    std::vector<TargetInfo> targets;
    for (uint32_t i = 0; i < 2; i++) {
        const document::Bucket &target(i == 0 ? target1 : target2);
        uint16_t disk(i == 0 ? lock1.disk : lock2.disk);
        assert(target.getBucketId().getRawId() != 0);
        targets.push_back(TargetInfo(
                _env.getBucketDatabase(target.getBucketSpace()).get(
                    target.getBucketId(), "PersistenceThread::handleSplitBucket - Target",
                    StorBucketDatabase::CREATE_IF_NONEXISTING),
                FileStorHandler::RemapInfo(target, disk)));
        targets.back().first->setBucketInfo(
            _env.getBucketInfo(target, disk));
        targets.back().first->disk = disk;
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
    FileStorHandler::RemapInfo source(cmd.getBucket(), _env._partition);
    _env._fileStorHandler.remapQueueAfterSplit(
            source, targets[0].second, targets[1].second);
    bool ownershipChanged(
            !_bucketOwnershipNotifier->distributorOwns(
                    cmd.getSourceIndex(), cmd.getBucket()));
    // Now release all the bucketdb locks.
    for (uint32_t i = 0; i < targets.size(); i++) {
        if (ownershipChanged) {
            notifyGuard.notifyAlways(targets[i].second.bucket,
                                     targets[i].first->getBucketInfo());
        }
        // The entries vector has the source bucket in element zero, so indexing
        // that with i+1
        if (targets[i].second.foundInQueue
            || targets[i].first->getMetaCount() > 0)
        {
            if (targets[i].first->getMetaCount() == 0) {
                // Fake that the bucket has content so it is not deleted.
                targets[i].first->info.setMetaCount(1);
                // Must make sure target bucket exists when we have pending ops
                // to an empty target bucket, since the provider will have
                // implicitly erased it by this point.
                spi::Bucket createTarget(
                        spi::Bucket(targets[i].second.bucket,
                            spi::PartitionId(targets[i].second.diskIndex)));
                LOG(debug,
                    "Split target %s was empty, but re-creating it since "
                    "there are remapped operations queued to it",
                    createTarget.toString().c_str());
                _spi.createBucket(createTarget, _context);
            }
            splitReply.getSplitInfo().push_back(
                    api::SplitBucketReply::Entry(
                        targets[i].second.bucket.getBucketId(),
                        targets[i].first->getBucketInfo()));
            targets[i].first.write();
        } else {
            targets[i].first.remove();
        }
    }
    if (sourceEntry.exist()) {
        if (ownershipChanged) {
            notifyGuard.notifyAlways(cmd.getBucket(),
                                     sourceEntry->getBucketInfo());
        }
        // Delete the old entry.
        sourceEntry.remove();
    }
    return tracker;
}

bool
PersistenceThread::validateJoinCommand(
        const api::JoinBucketsCommand& cmd,
        MessageTracker& tracker) const
{
    if (cmd.getSourceBuckets().size() != 2) {
        tracker.fail(ReturnCode::ILLEGAL_PARAMETERS,
                     "Join needs exactly two buckets to be joined together"
                     + cmd.getBucketId().toString());
        return false;
    }
    // Verify that source and target buckets look sane.
    for (uint32_t i = 0; i < cmd.getSourceBuckets().size(); i++) {
        if (cmd.getSourceBuckets()[i] == cmd.getBucketId()) {
            tracker.fail(ReturnCode::ILLEGAL_PARAMETERS,
                         "Join had both source and target bucket "
                            + cmd.getBucketId().toString());
            return false;
        }
        if (!cmd.getBucketId().contains(cmd.getSourceBuckets()[i])) {
            tracker.fail(ReturnCode::ILLEGAL_PARAMETERS,
                         "Source bucket " +
                         cmd.getSourceBuckets()[i].toString()
                         + " is not contained in target "
                         + cmd.getBucketId().toString());
            return false;
        }
    }
    return true;
}

MessageTracker::UP
PersistenceThread::handleJoinBuckets(api::JoinBucketsCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.joinBuckets,
                                       _env._component.getClock()));
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
        _env.getBucketDatabase(destBucket.getBucketSpace()).get(
                destBucket.getBucketId(),
                "join",
                StorBucketDatabase::CREATE_IF_NONEXISTING);

        entry->disk = _env._partition;
        entry.write();
    }

    document::Bucket firstBucket(destBucket.getBucketSpace(), cmd.getSourceBuckets()[0]);
    document::Bucket secondBucket(destBucket.getBucketSpace(), cmd.getSourceBuckets()[1]);

    PersistenceUtil::LockResult lock1(_env.lockAndGetDisk(firstBucket));
    PersistenceUtil::LockResult lock2;
    if (firstBucket != secondBucket) {
        lock2 = _env.lockAndGetDisk(secondBucket);
    }

#ifdef ENABLE_BUCKET_OPERATION_LOGGING
    {
        vespalib::string desc(
                vespalib::make_string(
                        "join(%s, %s -> %s)",
                        firstBucket.getBucketId().toString().c_str(),
                        secondBucket.getBucketId().toString().c_str(),
                        cmd.getBucketId().toString().c_str()));
        LOG_BUCKET_OPERATION(cmd.getBucketId(), desc);
        LOG_BUCKET_OPERATION(firstBucket.getBucketId(), desc);
        if (firstBucket != secondBucket) {
            LOG_BUCKET_OPERATION(secondBucket.getBucketId(), desc);
        }
    }
#endif
    spi::Result result =
        _spi.join(spi::Bucket(firstBucket, spi::PartitionId(lock1.disk)),
                  spi::Bucket(secondBucket, spi::PartitionId(lock2.disk)),
                  spi::Bucket(destBucket, spi::PartitionId(_env._partition)),
                  _context);
    if (!checkForError(result, *tracker)) {
        return tracker;
    }
    result = _spi.flush(spi::Bucket(destBucket, spi::PartitionId(_env._partition)), _context);
    if (!checkForError(result, *tracker)) {
        return tracker;
    }
    uint64_t lastModified = 0;
    for (uint32_t i = 0; i < cmd.getSourceBuckets().size(); i++) {
        document::Bucket srcBucket(destBucket.getBucketSpace(), cmd.getSourceBuckets()[i]);
        uint16_t disk = (i == 0) ? lock1.disk : lock2.disk;
        FileStorHandler::RemapInfo target(cmd.getBucket(),
                                          _env._partition);
        _env._fileStorHandler.remapQueueAfterJoin(
                FileStorHandler::RemapInfo(srcBucket, disk),
                target);
        // Remove source from bucket db.
        StorBucketDatabase::WrappedEntry entry(
                _env.getBucketDatabase(srcBucket.getBucketSpace()).get(
                        srcBucket.getBucketId(), "join-remove-source"));
        if (entry.exist()) {
            lastModified = std::max(lastModified,
                                    entry->info.getLastModified());
            entry.remove();
        }
    }
    {
        StorBucketDatabase::WrappedEntry entry =
            _env.getBucketDatabase(destBucket.getBucketSpace()).get(
                    destBucket.getBucketId(),
                    "join",
                    StorBucketDatabase::CREATE_IF_NONEXISTING);
        if (entry->info.getLastModified() == 0) {
            entry->info.setLastModified(
                    std::max(lastModified, entry->info.getLastModified()));
        }
        entry.write();
    }
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleSetBucketState(api::SetBucketStateCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.setBucketStates,
                                       _env._component.getClock()));
    NotificationGuard notifyGuard(*_bucketOwnershipNotifier);

    LOG(debug, "handleSetBucketState(): %s", cmd.toString().c_str());
    spi::Bucket bucket(cmd.getBucket(), spi::PartitionId(_env._partition));
    bool shouldBeActive(cmd.getState() == api::SetBucketStateCommand::ACTIVE);
    spi::BucketInfo::ActiveState newState(
            shouldBeActive
            ? spi::BucketInfo::ACTIVE
            : spi::BucketInfo::NOT_ACTIVE);

    spi::Result result(_spi.setActiveState(bucket, newState));
    if (checkForError(result, *tracker)) {
        StorBucketDatabase::WrappedEntry entry(_env.getBucketDatabase(bucket.getBucket().getBucketSpace()).get(
                cmd.getBucketId(), "handleSetBucketState"));
        if (entry.exist()) {
            entry->info.setActive(newState == spi::BucketInfo::ACTIVE);
            notifyGuard.notifyIfOwnershipChanged(cmd.getBucket(),
                    cmd.getSourceIndex(),
                    entry->info);
            entry.write();
        } else {
            LOG(warning, "Got OK setCurrentState result from provider for %s, "
                "but bucket has disappeared from service layer database",
                cmd.getBucketId().toString().c_str());
        }

        tracker->setReply(api::StorageReply::SP(
                new api::SetBucketStateReply(cmd)));
    }

    return tracker;
}

MessageTracker::UP
PersistenceThread::handleInternalBucketJoin(InternalBucketJoinCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.internalJoin,
                                       _env._component.getClock()));
    document::Bucket destBucket = cmd.getBucket();
    {
        // Create empty bucket for target.
        StorBucketDatabase::WrappedEntry entry =
            _env.getBucketDatabase(destBucket.getBucketSpace()).get(
                    destBucket.getBucketId(),
                    "join",
                    StorBucketDatabase::CREATE_IF_NONEXISTING);

        entry->disk = _env._partition;
        entry.write();
    }
    spi::Result result =
        _spi.join(spi::Bucket(destBucket, spi::PartitionId(cmd.getDiskOfInstanceToJoin())),
                  spi::Bucket(destBucket, spi::PartitionId(cmd.getDiskOfInstanceToJoin())),
                  spi::Bucket(destBucket, spi::PartitionId(cmd.getDiskOfInstanceToKeep())),
                  _context);
    if (checkForError(result, *tracker)) {
        tracker->setReply(
                api::StorageReply::SP(
                        new InternalBucketJoinReply(cmd,
                                _env.getBucketInfo(cmd.getBucket()))));
    }
    return tracker;
}

MessageTracker::UP
PersistenceThread::handleRecheckBucketInfo(RecheckBucketInfoCommand& cmd)
{
    MessageTracker::UP tracker(new MessageTracker(
            _env._metrics.recheckBucketInfo, _env._component.getClock()));
    document::Bucket bucket(cmd.getBucket());
    api::BucketInfo info(_env.getBucketInfo(bucket));
    NotificationGuard notifyGuard(*_bucketOwnershipNotifier);
    {
        // Update bucket database
        StorBucketDatabase::WrappedEntry entry(
                _component->getBucketDatabase(bucket.getBucketSpace()).get(
                        bucket.getBucketId(),
                        "handleRecheckBucketInfo"));

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
PersistenceThread::handleCommandSplitByType(api::StorageCommand& msg)
{
    switch (msg.getType().getId()) {
    case api::MessageType::GET_ID:
        return handleGet(static_cast<api::GetCommand&>(msg));
    case api::MessageType::PUT_ID:
        return handlePut(static_cast<api::PutCommand&>(msg));
    case api::MessageType::REMOVE_ID:
        return handleRemove(static_cast<api::RemoveCommand&>(msg));
    case api::MessageType::UPDATE_ID:
        return handleUpdate(static_cast<api::UpdateCommand&>(msg));
    case api::MessageType::MULTIOPERATION_ID:
        return handleMultiOperation(
                static_cast<api::MultiOperationCommand&>(msg));
    case api::MessageType::REVERT_ID:
        return handleRevert(static_cast<api::RevertCommand&>(msg));
    case api::MessageType::CREATEBUCKET_ID:
        return handleCreateBucket(static_cast<api::CreateBucketCommand&>(msg));
    case api::MessageType::DELETEBUCKET_ID:
        return handleDeleteBucket(static_cast<api::DeleteBucketCommand&>(msg));
    case api::MessageType::JOINBUCKETS_ID:
        return handleJoinBuckets(static_cast<api::JoinBucketsCommand&>(msg));
    case api::MessageType::SPLITBUCKET_ID:
        return handleSplitBucket(static_cast<api::SplitBucketCommand&>(msg));
       // Depends on iterators
    case api::MessageType::STATBUCKET_ID:
        return _processAllHandler.handleStatBucket(
                static_cast<api::StatBucketCommand&>(msg), _context);
    case api::MessageType::REMOVELOCATION_ID:
        return _processAllHandler.handleRemoveLocation(
                static_cast<api::RemoveLocationCommand&>(msg), _context);
    case api::MessageType::MERGEBUCKET_ID:
        return _mergeHandler.handleMergeBucket(
                static_cast<api::MergeBucketCommand&>(msg), _context);
    case api::MessageType::GETBUCKETDIFF_ID:
        return _mergeHandler.handleGetBucketDiff(
                static_cast<api::GetBucketDiffCommand&>(msg), _context);
    case api::MessageType::APPLYBUCKETDIFF_ID:
        return _mergeHandler.handleApplyBucketDiff(
                static_cast<api::ApplyBucketDiffCommand&>(msg), _context);
    case api::MessageType::SETBUCKETSTATE_ID:
        return handleSetBucketState(
                static_cast<api::SetBucketStateCommand&>(msg));
    case api::MessageType::INTERNAL_ID:
        switch(static_cast<api::InternalCommand&>(msg).getType()) {
        case GetIterCommand::ID:
            return handleGetIter(static_cast<GetIterCommand&>(msg));
        case CreateIteratorCommand::ID:
            return handleCreateIterator(
                    static_cast<CreateIteratorCommand&>(msg));
        case ReadBucketList::ID:
            return handleReadBucketList(static_cast<ReadBucketList&>(msg));
        case ReadBucketInfo::ID:
            return handleReadBucketInfo(static_cast<ReadBucketInfo&>(msg));
        case RepairBucketCommand::ID:
            return handleRepairBucket(static_cast<RepairBucketCommand&>(msg));
        case BucketDiskMoveCommand::ID:
            return _diskMoveHandler.handleBucketDiskMove(
                    static_cast<BucketDiskMoveCommand&>(msg), _context);
        case InternalBucketJoinCommand::ID:
            return handleInternalBucketJoin(
                    static_cast<InternalBucketJoinCommand&>(msg));
        case RecheckBucketInfoCommand::ID:
            return handleRecheckBucketInfo(
                    static_cast<RecheckBucketInfoCommand&>(msg));
        default:
            LOG(warning,
                "Persistence thread received unhandled internal command %s",
                msg.toString().c_str());
            break;
        }
    default:
        break;
    }
    return MessageTracker::UP();
}

MessageTracker::UP
PersistenceThread::handleCommand(api::StorageCommand& msg)
{
    _context = spi::Context(msg.getLoadType(), msg.getPriority(),
                            msg.getTrace().getLevel());
    MessageTracker::UP mtracker(handleCommandSplitByType(msg));
    if (mtracker.get() != 0) {
        if (mtracker->getReply().get() != 0) {
            mtracker->getReply()->getTrace().getRoot().addChild(
                    _context.getTrace().getRoot());
        } else {
            msg.getTrace().getRoot().addChild(_context.getTrace().getRoot());
        }
    }
    return mtracker;
}

void
PersistenceThread::handleReply(api::StorageReply& reply)
{
    switch (reply.getType().getId()) {
    case api::MessageType::GETBUCKETDIFF_REPLY_ID:
        _mergeHandler.handleGetBucketDiffReply(
                static_cast<api::GetBucketDiffReply&>(reply),
                _env._fileStorHandler);
        break;
    case api::MessageType::APPLYBUCKETDIFF_REPLY_ID:
        _mergeHandler.handleApplyBucketDiffReply(
                static_cast<api::ApplyBucketDiffReply&>(reply),
                _env._fileStorHandler);
        break;
    default:
        break;
    }
}

MessageTracker::UP
PersistenceThread::processMessage(api::StorageMessage& msg)
{
    MBUS_TRACE(msg.getTrace(), 5,
               "PersistenceThread: Processing message in persistence layer");

    ++_env._metrics.operations;
    if (msg.getType().isReply()) {
        try{
            _env._pauseHandler.setPriority(msg.getPriority());
            LOG(debug, "Handling reply: %s", msg.toString().c_str());
            LOG(spam, "Message content: %s", msg.toString(true).c_str());
            handleReply(static_cast<api::StorageReply&>(msg));
        } catch (std::exception& e) {
            // It's a reply, so nothing we can do.
            LOG(debug, "Caught exception for %s: %s",
                msg.toString().c_str(),
                e.what());
        }
    } else {
        api::StorageCommand& initiatingCommand =
            static_cast<api::StorageCommand&>(msg);

        try {
            int64_t startTime(
                    _component->getClock().getTimeInMillis().getTime());

            LOG(debug, "Handling command: %s", msg.toString().c_str());
            LOG(spam, "Message content: %s", msg.toString(true).c_str());
            std::unique_ptr<MessageTracker> tracker(
                    handleCommand(initiatingCommand));
            if (!tracker.get()) {
                LOG(debug, "Received unsupported command %s",
                    msg.getType().getName().c_str());
            } else {
                tracker->generateReply(initiatingCommand);
                if ((tracker->getReply().get()
                     && tracker->getReply()->getResult().failed())
                    || tracker->getResult().failed())
                {
                    ++_env._metrics.failedOperations;
                }
            }

            int64_t stopTime(
                    _component->getClock().getTimeInMillis().getTime());
            if (stopTime - startTime >= _warnOnSlowOperations) {
                LOGBT(warning, msg.getType().toString(),
                      "Slow processing of message %s on disk %u. "
                      "Processing time: %" PRId64 " ms (>=%d ms)",
                      msg.toString().c_str(), _env._partition,
                      stopTime - startTime, _warnOnSlowOperations);
            } else {
                LOGBT(spam, msg.getType().toString(),
                      "Processing time of message %s on disk %u: %" PRId64 " ms",
                      msg.toString(true).c_str(), _env._partition,
                      stopTime - startTime);
            }

            return tracker;
        } catch (std::exception& e) {
            LOG(debug, "Caught exception for %s: %s",
                msg.toString().c_str(),
                e.what());
            api::StorageReply::SP reply(initiatingCommand.makeReply().release());
            reply->setResult(api::ReturnCode(
                    api::ReturnCode::INTERNAL_FAILURE, e.what()));
            _env._fileStorHandler.sendReply(reply);
        }
    }

    return MessageTracker::UP();
}

namespace {


bool isBatchable(const api::StorageMessage& msg)
{
    return (msg.getType().getId() == api::MessageType::PUT_ID ||
            msg.getType().getId() == api::MessageType::REMOVE_ID ||
            msg.getType().getId() == api::MessageType::UPDATE_ID ||
            msg.getType().getId() == api::MessageType::MULTIOPERATION_ID ||
            msg.getType().getId() == api::MessageType::REVERT_ID);
}

bool hasBucketInfo(const api::StorageMessage& msg)
{
    return (isBatchable(msg) ||
            (msg.getType().getId() == api::MessageType::REMOVELOCATION_ID ||
             msg.getType().getId() == api::MessageType::JOINBUCKETS_ID));
}

}

void
PersistenceThread::flushAllReplies(
        const document::Bucket& bucket,
        std::vector<std::unique_ptr<MessageTracker> >& replies)
{
    if (replies.empty()) {
        return;
    }

    try {
        if (replies.size() > 1) {
            _env._metrics.batchingSize.addValue(replies.size());
        }
#ifdef ENABLE_BUCKET_OPERATION_LOGGING
        {
            size_t nputs = 0, nremoves = 0, nother = 0;
            for (size_t i = 0; i < replies.size(); ++i) {
                if (dynamic_cast<api::PutReply*>(replies[i]->getReply().get()))
                {
                    ++nputs;
                } else if (dynamic_cast<api::RemoveReply*>(
                                replies[i]->getReply().get()))
                {
                    ++nremoves;
                } else {
                    ++nother;
                }
            }
            LOG_BUCKET_OPERATION(
                    bucket.getBucketId(),
                    vespalib::make_string(
                            "flushing %zu operations (%zu puts, %zu removes, "
                            "%zu other)",
                            replies.size(), nputs, nremoves, nother));
        }
#endif
        spi::Bucket b(bucket, spi::PartitionId(_env._partition));
        spi::Result result = _spi.flush(b, _context);
        uint32_t errorCode = _env.convertErrorCode(result);
        if (errorCode != 0) {
            for (uint32_t i = 0; i < replies.size(); ++i) {
                replies[i]->getReply()->setResult(
                        api::ReturnCode(
                                (api::ReturnCode::Result)errorCode,
                                result.getErrorMessage()));
            }
        }
    } catch (std::exception& e) {
        for (uint32_t i = 0; i < replies.size(); ++i) {
            replies[i]->getReply()->setResult(api::ReturnCode(
                    api::ReturnCode::INTERNAL_FAILURE, e.what()));
        }
    }

    for (uint32_t i = 0; i < replies.size(); ++i) {
        LOG(spam,
            "Sending reply up (batched): %s %zu",
            replies[i]->getReply()->toString().c_str(),
            replies[i]->getReply()->getMsgId());
        _env._fileStorHandler.sendReply(replies[i]->getReply());
    }

    replies.clear();
}

void PersistenceThread::processMessages(FileStorHandler::LockedMessage & lock)
{
    std::vector<MessageTracker::UP> trackers;
    document::Bucket bucket = lock.first->getBucket();

    while (lock.second.get() != 0) {
        LOG(debug, "Inside while loop %d, nodeIndex %d, ptr=%p",
            _env._partition, _env._nodeIndex, lock.second.get());
        std::shared_ptr<api::StorageMessage> msg(lock.second);
        bool batchable = isBatchable(*msg);

        // If the next operation wasn't batchable, we should flush
        // everything that came before.
        if (!batchable) {
            flushAllReplies(bucket, trackers);
        }

        std::unique_ptr<MessageTracker> tracker = processMessage(*msg);
        if (!tracker.get() || !tracker->getReply().get()) {
            // Was a reply
            break;
        }

        if (hasBucketInfo(*msg)) {
            if (tracker->getReply()->getResult().success()) {
                _env.setBucketInfo(*tracker, bucket);
            }
        }
        if (batchable) {
            LOG(spam, "Adding reply %s to batch for bucket %s",
                tracker->getReply()->toString().c_str(),
                bucket.getBucketId().toString().c_str());

            trackers.push_back(std::move(tracker));

            if (trackers.back()->getReply()->getResult().success()) {
                _env._fileStorHandler.getNextMessage(_env._partition, lock);
            } else {
                break;
            }
        } else {
            LOG(spam,
                "Sending reply up: %s %zu",
                tracker->getReply()->toString().c_str(),
                tracker->getReply()->getMsgId());

            _env._fileStorHandler.sendReply(tracker->getReply());
            break;
        }
    }

    flushAllReplies(bucket, trackers);
}

void
PersistenceThread::run(framework::ThreadHandle& thread)
{
    LOG(debug, "Started persistence thread with pid %d", getpid());

    while (!thread.interrupted()
           && !_env._fileStorHandler.closed(_env._partition))
    {
        thread.registerTick();

        FileStorHandler::LockedMessage lock(_env._fileStorHandler.getNextMessage(_env._partition));

        if (lock.first.get()) {
            processMessages(lock);
        }

        vespalib::MonitorGuard flushMonitorGuard(_flushMonitor);
        flushMonitorGuard.broadcast();
    }
    LOG(debug, "Closing down persistence thread %d", getpid());
    vespalib::MonitorGuard flushMonitorGuard(_flushMonitor);
    _closed = true;
    flushMonitorGuard.broadcast();
}

void
PersistenceThread::flush()
{
    vespalib::MonitorGuard flushMonitorGuard(_flushMonitor);
    if (!_closed) {
        flushMonitorGuard.wait();
    }
}

} // storage
