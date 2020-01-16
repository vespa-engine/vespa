// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "processallhandler.h"
#include "bucketprocessor.h"
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.processall");

namespace storage {

ProcessAllHandler::ProcessAllHandler(PersistenceUtil& env,
                                     spi::PersistenceProvider& spi)
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

    UnrevertableRemoveEntryProcessor(
            spi::PersistenceProvider& provider,
            const spi::Bucket& bucket,
            spi::Context& context)
        : _provider(provider),
          _bucket(bucket),
          _context(context) {}

    void process(spi::DocEntry& entry) override {
        spi::RemoveResult removeResult = _provider.remove(
                _bucket,
                entry.getTimestamp(),
                *entry.getDocumentId(),
                _context);

        if (removeResult.getErrorCode() != spi::Result::ErrorType::NONE) {
            std::ostringstream ss;
            ss << "Failed to do remove for removelocation: "
               << removeResult.getErrorMessage();
            throw std::runtime_error(ss.str());
        }
    }
};

class StatEntryProcessor : public BucketProcessor::EntryProcessor {
public:
    std::ostream& ost;
    StatEntryProcessor(std::ostream& o)
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
ProcessAllHandler::handleRemoveLocation(api::RemoveLocationCommand& cmd,
                                        spi::Context& context)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.removeLocation[cmd.getLoadType()],
                                       _env._component.getClock()));

    LOG(debug, "RemoveLocation(%s): using selection '%s'",
        cmd.getBucketId().toString().c_str(),
        cmd.getDocumentSelection().c_str());

    spi::Bucket bucket(cmd.getBucket(), spi::PartitionId(_env._partition));
    UnrevertableRemoveEntryProcessor processor(_spi, bucket, context);
    BucketProcessor::iterateAll(_spi,
                                bucket,
                                cmd.getDocumentSelection(),
                                processor,
                                spi::NEWEST_DOCUMENT_ONLY,
                                context);
    spi::Result result = _spi.flush(bucket, context);
    uint32_t code = _env.convertErrorCode(result);
    if (code != 0) {
        tracker->fail(code, result.getErrorMessage());
    }

    return tracker;
}

MessageTracker::UP
ProcessAllHandler::handleStatBucket(api::StatBucketCommand& cmd,
                                    spi::Context& context)
{
    MessageTracker::UP tracker(new MessageTracker(
                                       _env._metrics.statBucket[cmd.getLoadType()],
                                       _env._component.getClock()));
    std::ostringstream ost;

    ost << "Persistence bucket " << cmd.getBucketId()
        << ", partition " << _env._partition << "\n";

    spi::Bucket bucket(cmd.getBucket(), spi::PartitionId(_env._partition));
    StatEntryProcessor processor(ost);
    BucketProcessor::iterateAll(_spi,
               bucket,
               cmd.getDocumentSelection(),
               processor,
               spi::ALL_VERSIONS,
               context);

    api::StatBucketReply::UP reply(new api::StatBucketReply(cmd, ost.str()));
    tracker->setReply(api::StorageReply::SP(reply.release()));
    return tracker;
}

} // storage
