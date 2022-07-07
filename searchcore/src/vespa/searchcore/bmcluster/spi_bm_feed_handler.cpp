// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "spi_bm_feed_handler.h"
#include "i_bm_distribution.h"
#include "pending_tracker.h"
#include "bucket_info_queue.h"
#include <vespa/document/fieldset/fieldsetrepo.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/persistence/spi/persistenceprovider.h>

using document::Document;
using document::DocumentId;
using document::DocumentUpdate;
using storage::spi::Bucket;
using storage::spi::PersistenceProvider;
using storage::spi::Timestamp;

namespace search::bmcluster {

namespace {

storage::spi::Context context(storage::spi::Priority(0), 0);

void get_bucket_info_loop(PendingTracker &tracker)
{
    auto bucket_info_queue = tracker.get_bucket_info_queue();
    if (bucket_info_queue != nullptr) {
        bucket_info_queue->get_bucket_info_loop();
    }
}

class MyOperationComplete : public storage::spi::OperationComplete
{
    PersistenceProvider* _provider;
    std::atomic<uint32_t> &_errors;
    Bucket _bucket;
    PendingTracker& _tracker;
public:
    MyOperationComplete(PersistenceProvider* provider, std::atomic<uint32_t> &errors, const Bucket& bucket, PendingTracker& tracker);
    ~MyOperationComplete() override;
    void onComplete(std::unique_ptr<storage::spi::Result> result) noexcept override;
    void addResultHandler(const storage::spi::ResultHandler* resultHandler) override;
};

MyOperationComplete::MyOperationComplete(PersistenceProvider* provider, std::atomic<uint32_t> &errors, const Bucket& bucket, PendingTracker& tracker)
    : _provider(provider),
      _errors(errors),
      _bucket(bucket),
      _tracker(tracker)
{
    _tracker.retain();
}

MyOperationComplete::~MyOperationComplete()
{
    _tracker.release();
}

void
MyOperationComplete::onComplete(std::unique_ptr<storage::spi::Result> result) noexcept
{
    if (result->hasError()) {
        ++_errors;
    } else {
        auto bucket_info_queue = _tracker.get_bucket_info_queue();
        if (bucket_info_queue != nullptr) {
            bucket_info_queue->put_bucket(_bucket, _provider);
        }
    }
}

void
MyOperationComplete::addResultHandler(const storage::spi::ResultHandler * resultHandler)
{
    (void) resultHandler;
}

}

SpiBmFeedHandler::SpiBmFeedHandler(std::vector<PersistenceProvider* >providers, const document::FieldSetRepo &field_set_repo, const IBmDistribution& distribution, bool skip_get_spi_bucket_info)
    : IBmFeedHandler(),
      _name(vespalib::string("SpiBmFeedHandler(") + (skip_get_spi_bucket_info ? "skip-get-spi-bucket-info" : "get-spi-bucket-info") + ")"),
      _providers(std::move(providers)),
      _field_set_repo(field_set_repo),
      _errors(0u),
      _skip_get_spi_bucket_info(skip_get_spi_bucket_info),
      _distribution(distribution)
{
}

SpiBmFeedHandler::~SpiBmFeedHandler() = default;

PersistenceProvider*
SpiBmFeedHandler::get_provider(const document::Bucket& bucket)
{
    uint32_t node_idx = _distribution.get_service_layer_node_idx(bucket);
    if (node_idx >= _providers.size()) {
        return nullptr;
    }
    return _providers[node_idx];
}

void
SpiBmFeedHandler::put(const document::Bucket& bucket, std::unique_ptr<Document> document, uint64_t timestamp, PendingTracker& tracker)
{
    get_bucket_info_loop(tracker);
    auto provider = get_provider(bucket);
    if (provider) {
        Bucket spi_bucket(bucket);
        provider->putAsync(spi_bucket, Timestamp(timestamp), std::move(document), std::make_unique<MyOperationComplete>(provider, _errors, spi_bucket, tracker));
    } else {
        ++_errors;
    }
}

void
SpiBmFeedHandler::update(const document::Bucket& bucket, std::unique_ptr<DocumentUpdate> document_update, uint64_t timestamp, PendingTracker& tracker)
{
    get_bucket_info_loop(tracker);
    auto provider = get_provider(bucket);
    if (provider) {
        Bucket spi_bucket(bucket);
        provider->updateAsync(spi_bucket, Timestamp(timestamp), std::move(document_update), std::make_unique<MyOperationComplete>(provider, _errors, spi_bucket, tracker));
    } else {
        ++_errors;
    }
}

void
SpiBmFeedHandler::remove(const document::Bucket& bucket, const DocumentId& document_id,  uint64_t timestamp, PendingTracker& tracker)
{
    get_bucket_info_loop(tracker);
    auto provider = get_provider(bucket);
    if (provider) {
        Bucket spi_bucket(bucket);
        std::vector<storage::spi::IdAndTimestamp> ids;
        ids.emplace_back(document_id, Timestamp(timestamp));
        provider->removeAsync(spi_bucket, std::move(ids), std::make_unique<MyOperationComplete>(provider, _errors, spi_bucket, tracker));
    } else {
        ++_errors;
    }
}

void
SpiBmFeedHandler::get(const document::Bucket& bucket, vespalib::stringref field_set_string, const document::DocumentId& document_id, PendingTracker& tracker)
{
    get_bucket_info_loop(tracker);
    auto provider = get_provider(bucket);
    if (provider) {
        Bucket spi_bucket(bucket);
        auto field_set = _field_set_repo.getFieldSet(field_set_string);
        auto result = provider->get(spi_bucket, *field_set, document_id, context);
        if (result.hasError()) {
            ++_errors;
        }
    } else {
        ++_errors;
    }
}

void
SpiBmFeedHandler::attach_bucket_info_queue(PendingTracker& tracker)
{
    if (!_skip_get_spi_bucket_info) {
        tracker.attach_bucket_info_queue(_errors);
    }
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
SpiBmFeedHandler::manages_timestamp() const
{
    return false;
}

}
