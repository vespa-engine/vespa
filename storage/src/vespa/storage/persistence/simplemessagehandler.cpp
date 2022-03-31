// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplemessagehandler.h"
#include "persistenceutil.h"
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/persistence/spi/docentry.h>
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
        spi::Result result = _spi.removeEntry(b, spi::Timestamp(token));
    }
    return tracker;
}

MessageTracker::UP
SimpleMessageHandler::handleGetIter(GetIterCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.visit);
    spi::IterateResult result(_spi.iterate(cmd.getIteratorId(), cmd.getMaxByteSize()));
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
