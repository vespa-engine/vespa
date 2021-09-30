// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "processallhandler.h"
#include "bucketprocessor.h"
#include "persistenceutil.h"
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.processall");

namespace storage {

ProcessAllHandler::ProcessAllHandler(const PersistenceUtil& env, spi::PersistenceProvider& spi)
    : _env(env),
      _spi(spi)
{
}

namespace {

class UnrevertableRemoveEntryProcessor : public BucketProcessor::EntryProcessor {
public:
    spi::PersistenceProvider& _provider;
    const spi::Bucket& _bucket;
    spi::Context& _context;
    uint32_t _n_removed;

    UnrevertableRemoveEntryProcessor(
            spi::PersistenceProvider& provider,
            const spi::Bucket& bucket,
            spi::Context& context)
        : _provider(provider),
          _bucket(bucket),
          _context(context),
          _n_removed(0)
    {}

    void process(spi::DocEntry& entry) override {
        spi::RemoveResult removeResult = _provider.remove(_bucket, entry.getTimestamp(), *entry.getDocumentId(),_context);
        if (removeResult.getErrorCode() != spi::Result::ErrorType::NONE) {
            throw std::runtime_error(vespalib::make_string("Failed to do remove for removelocation: %s", removeResult.getErrorMessage().c_str()));
        }
        ++_n_removed;
    }
};

class StatEntryProcessor : public BucketProcessor::EntryProcessor {
public:
    std::ostream& ost;
    explicit StatEntryProcessor(std::ostream& o)
        : ost(o) {};

    void process(spi::DocEntry& e) override {
        ost << "  Timestamp: " << e.getTimestamp() << ", ";
        if (e.getDocument() != nullptr) {
            ost << "Doc(" << e.getDocument()->getId() << ")"
                << ", " << e.getDocument()->getId().getGlobalId().toString()
                << ", size: " << e.getPersistedDocumentSize();
        } else if (e.getDocumentId() != nullptr) {
            ost << *e.getDocumentId()
                << ", " << e.getDocumentId()->getGlobalId().toString();
        } else {
            ost << "metadata only";
        }
        if (e.isRemove()) {
            ost << " (remove)";
        }
        ost << "\n";
    }
};

}

MessageTracker::UP
ProcessAllHandler::handleRemoveLocation(api::RemoveLocationCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.removeLocation);

    LOG(debug, "RemoveLocation(%s): using selection '%s'",
        cmd.getBucketId().toString().c_str(),
        cmd.getDocumentSelection().c_str());

    spi::Bucket bucket(cmd.getBucket());
    UnrevertableRemoveEntryProcessor processor(_spi, bucket, tracker->context());
    BucketProcessor::iterateAll(_spi, bucket, cmd.getDocumentSelection(),
                                std::make_shared<document::AllFields>(),
                                processor, spi::NEWEST_DOCUMENT_ONLY,tracker->context());

    tracker->setReply(std::make_shared<api::RemoveLocationReply>(cmd, processor._n_removed));

    return tracker;
}

MessageTracker::UP
ProcessAllHandler::handleStatBucket(api::StatBucketCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.statBucket);
    std::ostringstream ost;
    ost << "Persistence bucket " << cmd.getBucketId() << "\n";

    spi::Bucket bucket(cmd.getBucket());
    StatEntryProcessor processor(ost);
    BucketProcessor::iterateAll(_spi, bucket, cmd.getDocumentSelection(),
                                std::make_shared<document::AllFields>(),
                                processor, spi::ALL_VERSIONS,tracker->context());

    tracker->setReply(std::make_shared<api::StatBucketReply>(cmd, ost.str()));
    return tracker;
}

} // storage
