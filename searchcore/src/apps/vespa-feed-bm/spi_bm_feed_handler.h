// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_bm_feed_handler.h"
#include <atomic>

namespace storage::spi { struct PersistenceProvider; }

namespace feedbm {

/*
 * Benchmark feed handler for feed directly to persistence provider
 */
class SpiBmFeedHandler : public IBmFeedHandler
{
    vespalib::string _name;
    storage::spi::PersistenceProvider& _provider;
    std::atomic<uint32_t> _errors;
public:
    SpiBmFeedHandler(storage::spi::PersistenceProvider& provider);
    ~SpiBmFeedHandler();
    void put(const document::Bucket& bucket, std::unique_ptr<document::Document> document, uint64_t timestamp, PendingTracker& tracker) override;
    void update(const document::Bucket& bucket, std::unique_ptr<document::DocumentUpdate> document_update, uint64_t timestamp, PendingTracker& tracker) override;
    void remove(const document::Bucket& bucket, const document::DocumentId& document_id,  uint64_t timestamp, PendingTracker& tracker) override;
    void create_bucket(const document::Bucket& bucket);
    uint32_t get_error_count() const override;
    const vespalib::string &get_name() const override;
    bool manages_buckets() const override;
    bool manages_timestamp() const override;
};

}
