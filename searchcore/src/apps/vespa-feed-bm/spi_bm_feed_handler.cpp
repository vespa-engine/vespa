// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "spi_bm_feed_handler.h"
#include "pending_tracker.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/metrics/loadtype.h>
#include <vespa/persistence/spi/persistenceprovider.h>

using document::Document;
using document::DocumentId;
using document::DocumentUpdate;
using storage::spi::Bucket;
using storage::spi::PartitionId;
using storage::spi::PersistenceProvider;
using storage::spi::Timestamp;

namespace feedbm {

namespace {

storage::spi::LoadType default_load_type(0, "default");
storage::spi::Context context(default_load_type, storage::spi::Priority(0), storage::spi::Trace::TraceLevel(0));

class MyOperationComplete : public storage::spi::OperationComplete
{
    PendingTracker& _tracker;
public:
    MyOperationComplete(PendingTracker& tracker);
    ~MyOperationComplete();
    void onComplete(std::unique_ptr<storage::spi::Result> result) override;
    void addResultHandler(const storage::spi::ResultHandler* resultHandler) override;
};

MyOperationComplete::MyOperationComplete(PendingTracker& tracker)
    : _tracker(tracker)
{
    _tracker.retain();
}

MyOperationComplete::~MyOperationComplete()
{
    _tracker.release();
}

void
MyOperationComplete::onComplete(std::unique_ptr<storage::spi::Result> result)
{
    (void) result;
}

void
MyOperationComplete::addResultHandler(const storage::spi::ResultHandler * resultHandler)
{
    (void) resultHandler;
}

}

SpiBmFeedHandler::SpiBmFeedHandler(PersistenceProvider& provider)
    : IBmFeedHandler(),
      _provider(provider)
{
}

SpiBmFeedHandler::~SpiBmFeedHandler() = default;

void
SpiBmFeedHandler::put(const document::Bucket& bucket, std::unique_ptr<Document> document, uint64_t timestamp, PendingTracker& tracker)
{
    _provider.putAsync(Bucket(bucket, PartitionId(0)), Timestamp(timestamp), std::move(document), context, std::make_unique<MyOperationComplete>(tracker));
}

void
SpiBmFeedHandler::update(const document::Bucket& bucket, std::unique_ptr<DocumentUpdate> document_update, uint64_t timestamp, PendingTracker& tracker)
{
    _provider.updateAsync(Bucket(bucket, PartitionId(0)), Timestamp(timestamp), std::move(document_update), context, std::make_unique<MyOperationComplete>(tracker));
}

void
SpiBmFeedHandler::remove(const document::Bucket& bucket, const DocumentId& document_id,  uint64_t timestamp, PendingTracker& tracker)
{
    _provider.removeAsync(Bucket(bucket, PartitionId(0)), Timestamp(timestamp), document_id, context, std::make_unique<MyOperationComplete>(tracker));

}

void
SpiBmFeedHandler::create_bucket(const document::Bucket& bucket)
{
    _provider.createBucket(Bucket(bucket, PartitionId(0)), context);
}

}
