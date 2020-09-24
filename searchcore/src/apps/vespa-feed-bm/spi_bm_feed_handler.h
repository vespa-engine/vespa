// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_bm_feed_handler.h"

namespace storage::spi { struct PersistenceProvider; }

namespace feedbm {

/*
 * Benchmark feed handler for feed directly to persistence provider
 */
class SpiBmFeedHandler : public IBmFeedHandler
{
    storage::spi::PersistenceProvider& _provider;
public:
    SpiBmFeedHandler(storage::spi::PersistenceProvider& provider);
    ~SpiBmFeedHandler();
    void put(const document::Bucket& bucket, std::unique_ptr<document::Document> document, uint64_t timestamp, PendingTracker& tracker) override;
    void update(const document::Bucket& bucket, std::unique_ptr<document::DocumentUpdate> document_update, uint64_t timestamp, PendingTracker& tracker) override;
    void remove(const document::Bucket& bucket, const document::DocumentId& document_id,  uint64_t timestamp, PendingTracker& tracker) override;
    void create_bucket(const document::Bucket& bucket);
};

}
