// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplemessagehandler.h"
#include "persistenceutil.h"
#include "testandsethelper.h"
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
SimpleMessageHandler::SimpleMessageHandler(const PersistenceUtil& env,
                                           spi::PersistenceProvider& spi,
                                           const document::BucketIdFactory& bucket_id_factory)
    : _env(env),
      _spi(spi),
      _bucket_id_factory(bucket_id_factory)
{
}

MessageTracker::UP
SimpleMessageHandler::handle_conditional_get(api::GetCommand& cmd, MessageTracker::UP tracker) const
{
    if (cmd.getFieldSet() == document::NoFields::NAME) {
        TestAndSetHelper tas_helper(_env, _spi, _bucket_id_factory, cmd.condition(),
                                    cmd.getBucket(), cmd.getDocumentId(), nullptr);
        auto result = tas_helper.fetch_and_match_raw(tracker->context());
        tracker->setReply(std::make_shared<api::GetReply>(cmd, nullptr, result.timestamp, false,
                                                          result.is_tombstone(), result.is_match()));
    } else {
        tracker->fail(api::ReturnCode::ILLEGAL_PARAMETERS, "Conditional Get operations must be metadata-only");
    }
    return tracker;
}

MessageTracker::UP
SimpleMessageHandler::handleGet(api::GetCommand& cmd, MessageTracker::UP tracker) const
{
    auto& metrics = _env._metrics.get;
    tracker->setMetric(metrics);
    metrics.request_size.addValue(cmd.getApproxByteSize());

    if (cmd.has_condition()) {
        return handle_conditional_get(cmd, std::move(tracker));
    }

    auto fieldSet = getFieldSet(_env.getFieldSetRepo(), cmd.getFieldSet(), *tracker);
    if (!fieldSet) {
        return tracker;
    }

    tracker->context().setReadConsistency(api_read_consistency_to_spi(cmd.internal_read_consistency()));
    spi::GetResult result = _spi.get(_env.getBucket(cmd.getDocumentId(), cmd.getBucket()),
                                     *fieldSet, cmd.getDocumentId(), tracker->context());

    if (tracker->checkForError(result)) {
        if (!result.hasDocument() && (document::FieldSet::Type::NONE != fieldSet->getType())) {
            metrics.notFound.inc();
        }
        tracker->setReply(std::make_shared<api::GetReply>(cmd, result.getDocumentPtr(), result.getTimestamp(),
                                                          false, result.is_tombstone(), false));
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
