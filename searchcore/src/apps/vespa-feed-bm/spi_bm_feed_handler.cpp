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
    std::atomic<uint32_t> &_errors;
    PendingTracker& _tracker;
public:
    MyOperationComplete(std::atomic<uint32_t> &errors, PendingTracker& tracker);
    ~MyOperationComplete();
    void onComplete(std::unique_ptr<storage::spi::Result> result) override;
    void addResultHandler(const storage::spi::ResultHandler* resultHandler) override;
};

MyOperationComplete::MyOperationComplete(std::atomic<uint32_t> &errors, PendingTracker& tracker)
    : _errors(errors),
      _tracker(tracker)
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
    if (result->hasError()) {
        ++_errors;
    }
}

void
MyOperationComplete::addResultHandler(const storage::spi::ResultHandler * resultHandler)
{
    (void) resultHandler;
}

}

SpiBmFeedHandler::SpiBmFeedHandler(PersistenceProvider& provider)
    : IBmFeedHandler(),
      _name("SpiBmFeedHandler"),
      _provider(provider),
      _errors(0u)
{
}

SpiBmFeedHandler::~SpiBmFeedHandler() = default;

void
SpiBmFeedHandler::put(const document::Bucket& bucket, std::unique_ptr<Document> document, uint64_t timestamp, PendingTracker& tracker)
{
    _provider.putAsync(Bucket(bucket, PartitionId(0)), Timestamp(timestamp), std::move(document), context, std::make_unique<MyOperationComplete>(_errors, tracker));
}

void
SpiBmFeedHandler::update(const document::Bucket& bucket, std::unique_ptr<DocumentUpdate> document_update, uint64_t timestamp, PendingTracker& tracker)
{
    _provider.updateAsync(Bucket(bucket, PartitionId(0)), Timestamp(timestamp), std::move(document_update), context, std::make_unique<MyOperationComplete>(_errors, tracker));
}

void
SpiBmFeedHandler::remove(const document::Bucket& bucket, const DocumentId& document_id,  uint64_t timestamp, PendingTracker& tracker)
{
    _provider.removeAsync(Bucket(bucket, PartitionId(0)), Timestamp(timestamp), document_id, context, std::make_unique<MyOperationComplete>(_errors, tracker));

}

void
SpiBmFeedHandler::create_bucket(const document::Bucket& bucket)
{
    _provider.createBucket(Bucket(bucket, PartitionId(0)), context);
}

uint32_t
SpiBmFeedHandler::get_error_count() const
{
    return _errors;
}

const vespalib::string&
SpiBmFeedHandler::get_name() const
{
    return _name;
}

bool
SpiBmFeedHandler::manages_buckets() const
{
    return false;
}

bool
SpiBmFeedHandler::manages_timestamp() const
{
    return false;
}

}
