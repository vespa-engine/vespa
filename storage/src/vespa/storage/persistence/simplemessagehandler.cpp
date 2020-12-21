// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplemessagehandler.h"
#include "persistenceutil.h"
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/document/fieldset/fieldsetrepo.h>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.simplemessagehandler");

using vespalib::make_string_short::fmt;
using to_str = vespalib::string;

namespace storage {

namespace {

spi::ReadConsistency
api_read_consistency_to_spi(api::InternalReadConsistency consistency) noexcept {
    switch (consistency) {
        case api::InternalReadConsistency::Strong:
            return spi::ReadConsistency::STRONG;
        case api::InternalReadConsistency::Weak:
            return spi::ReadConsistency::WEAK;
        default:
            abort();
    }
}

document::FieldSet::SP
getFieldSet(const document::FieldSetRepo & repo, vespalib::stringref name, MessageTracker & tracker) {
    try {
        return repo.getFieldSet(name);
    } catch (document::FieldNotFoundException & e) {
        tracker.fail(storage::api::ReturnCode::ILLEGAL_PARAMETERS,
                     fmt("Field %s in fieldset %s not found in document", e.getFieldName().c_str(), to_str(name).c_str()));
    } catch (const vespalib::Exception & e) {
        tracker.fail(storage::api::ReturnCode::ILLEGAL_PARAMETERS,
                     fmt("Failed parsing fieldset %s with : %s", to_str(name).c_str(), e.getMessage().c_str()));
    }
    return document::FieldSet::SP();
}

bool
bucketStatesAreSemanticallyEqual(const api::BucketInfo& a, const api::BucketInfo& b) {
    // Don't check document sizes, as background moving of documents in Proton
    // may trigger a change in size without any mutations taking place. This will
    // only take place when a document being moved was fed _prior_ to the change
    // where Proton starts reporting actual document sizes, and will eventually
    // converge to a stable value. But for now, ignore it to prevent false positive
    // error logs and non-deleted buckets.
    return ((a.getChecksum() == b.getChecksum()) && (a.getDocumentCount() == b.getDocumentCount()));
}
}
SimpleMessageHandler::SimpleMessageHandler(const PersistenceUtil& env, spi::PersistenceProvider& spi)
    : _env(env),
      _spi(spi)
{
}

MessageTracker::UP
SimpleMessageHandler::handleGet(api::GetCommand& cmd, MessageTracker::UP tracker) const
{
    auto& metrics = _env._metrics.get;
    tracker->setMetric(metrics);
    metrics.request_size.addValue(cmd.getApproxByteSize());

    auto fieldSet = getFieldSet(_env.getFieldSetRepo(), cmd.getFieldSet(), *tracker);
    if ( ! fieldSet) { return tracker; }

    tracker->context().setReadConsistency(api_read_consistency_to_spi(cmd.internal_read_consistency()));
    spi::GetResult result = _spi.get(_env.getBucket(cmd.getDocumentId(), cmd.getBucket()),
                                     *fieldSet, cmd.getDocumentId(), tracker->context());

    if (tracker->checkForError(result)) {
        if (!result.hasDocument() && (document::FieldSet::Type::NONE != fieldSet->getType())) {
            metrics.notFound.inc();
        }
        tracker->setReply(std::make_shared<api::GetReply>(cmd, result.getDocumentPtr(), result.getTimestamp(),
                                                          false, result.is_tombstone()));
    }

    return tracker;
}

MessageTracker::UP
SimpleMessageHandler::handleRevert(api::RevertCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.revert);
    spi::Bucket b = spi::Bucket(cmd.getBucket());
    const std::vector<api::Timestamp> & tokens = cmd.getRevertTokens();
    for (const api::Timestamp & token : tokens) {
        spi::Result result = _spi.removeEntry(b, spi::Timestamp(token), tracker->context());
    }
    return tracker;
}

MessageTracker::UP
SimpleMessageHandler::handleCreateBucket(api::CreateBucketCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.createBuckets);
    LOG(debug, "CreateBucket(%s)", cmd.getBucketId().toString().c_str());
    if (_env._fileStorHandler.isMerging(cmd.getBucket())) {
        LOG(warning, "Bucket %s was merging at create time. Unexpected.", cmd.getBucketId().toString().c_str());
    }
    spi::Bucket spiBucket(cmd.getBucket());
    _spi.createBucket(spiBucket, tracker->context());
    if (cmd.getActive()) {
        _spi.setActiveState(spiBucket, spi::BucketInfo::ACTIVE);
    }
    return tracker;
}

bool
SimpleMessageHandler::checkProviderBucketInfoMatches(const spi::Bucket& bucket, const api::BucketInfo& info) const
{
    spi::BucketInfoResult result(_spi.getBucketInfo(bucket));
    if (result.hasError()) {
        LOG(error, "getBucketInfo(%s) failed before deleting bucket; got error '%s'",
            bucket.toString().c_str(), result.getErrorMessage().c_str());
        return false;
    }
    api::BucketInfo providerInfo(PersistenceUtil::convertBucketInfo(result.getBucketInfo()));
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
            bucket.toString().c_str(), info.toString().c_str(), providerInfo.toString().c_str());
        return false;
    }
    return true;
}

MessageTracker::UP
SimpleMessageHandler::handleDeleteBucket(api::DeleteBucketCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.deleteBuckets);
    LOG(debug, "DeletingBucket(%s)", cmd.getBucketId().toString().c_str());
    if (_env._fileStorHandler.isMerging(cmd.getBucket())) {
        _env._fileStorHandler.clearMergeStatus(cmd.getBucket(),
                                               api::ReturnCode(api::ReturnCode::ABORTED, "Bucket was deleted during the merge"));
    }
    spi::Bucket bucket(cmd.getBucket());
    if (!checkProviderBucketInfoMatches(bucket, cmd.getBucketInfo())) {
        return tracker;
    }
    _spi.deleteBucket(bucket, tracker->context());
    StorBucketDatabase& db(_env.getBucketDatabase(cmd.getBucket().getBucketSpace()));
    {
        StorBucketDatabase::WrappedEntry entry(db.get(cmd.getBucketId(), "FileStorThread::onDeleteBucket"));
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
SimpleMessageHandler::handleGetIter(GetIterCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.visit);
    spi::IterateResult result(_spi.iterate(cmd.getIteratorId(), cmd.getMaxByteSize(), tracker->context()));
    if (tracker->checkForError(result)) {
        auto reply = std::make_shared<GetIterReply>(cmd);
        reply->getEntries() = result.steal_entries();
        _env._metrics.visit.documentsPerIterate.addValue(reply->getEntries().size());
        if (result.isCompleted()) {
            reply->setCompleted();
        }
        tracker->setReply(reply);
    }
    return tracker;
}

MessageTracker::UP
SimpleMessageHandler::handleReadBucketList(ReadBucketList& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.readBucketList);

    spi::BucketIdListResult result(_spi.listBuckets(cmd.getBucketSpace()));
    if (tracker->checkForError(result)) {
        auto reply = std::make_shared<ReadBucketListReply>(cmd);
        result.getList().swap(reply->getBuckets());
        tracker->setReply(reply);
    }

    return tracker;
}

MessageTracker::UP
SimpleMessageHandler::handleReadBucketInfo(ReadBucketInfo& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.readBucketInfo);
    _env.updateBucketDatabase(cmd.getBucket(), _env.getBucketInfo(cmd.getBucket()));
    return tracker;
}

MessageTracker::UP
SimpleMessageHandler::handleCreateIterator(CreateIteratorCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.createIterator);
    auto fieldSet = getFieldSet(_env.getFieldSetRepo(), cmd.getFields(), *tracker);
    if ( ! fieldSet) { return tracker; }

    tracker->context().setReadConsistency(cmd.getReadConsistency());
    spi::CreateIteratorResult result(_spi.createIterator(
            spi::Bucket(cmd.getBucket()),
            std::move(fieldSet), cmd.getSelection(), cmd.getIncludedVersions(), tracker->context()));
    if (tracker->checkForError(result)) {
        tracker->setReply(std::make_shared<CreateIteratorReply>(cmd, spi::IteratorId(result.getIteratorId())));
    }
    return tracker;
}

} // storage
