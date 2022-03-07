// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "processallhandler.h"
#include "bucketprocessor.h"
#include "persistenceutil.h"
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP(".persistence.processall");

namespace storage {

ProcessAllHandler::ProcessAllHandler(const PersistenceUtil& env, spi::PersistenceProvider& spi)
    : _env(env),
      _spi(spi)
{
}

namespace {

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
                << ", size: " << e.getSize();
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
ProcessAllHandler::handleStatBucket(api::StatBucketCommand& cmd, MessageTracker::UP tracker) const
{
    tracker->setMetric(_env._metrics.statBucket);
    std::ostringstream ost;
    ost << "Persistence bucket " << cmd.getBucketId() << "\n";

    spi::Bucket bucket(cmd.getBucket());
    StatEntryProcessor processor(ost);
    BucketProcessor::iterateAll(_spi, bucket, cmd.getDocumentSelection(),
                                std::make_shared<document::DocIdOnly>(),
                                processor, spi::ALL_VERSIONS,tracker->context());

    tracker->setReply(std::make_shared<api::StatBucketReply>(cmd, ost.str()));
    return tracker;
}

} // storage
