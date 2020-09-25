// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_bm_feed_handler.h"

namespace storage { class IStorageChainBuilder; }
namespace storage::api { class StorageCommand; }

namespace feedbm {

/*
 * Benchmark feed handler for feed to service layer using storage api protocol
 * directly on the storage chain.
 */
class StorageApiChainBmFeedHandler : public IBmFeedHandler
{
    void send_msg(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& tracker);
public:
    StorageApiChainBmFeedHandler();
    ~StorageApiChainBmFeedHandler();
    void put(const document::Bucket& bucket, std::unique_ptr<document::Document> document, uint64_t timestamp, PendingTracker& tracker) override;
    void update(const document::Bucket& bucket, std::unique_ptr<document::DocumentUpdate> document_update, uint64_t timestamp, PendingTracker& tracker) override;
    void remove(const document::Bucket& bucket, const document::DocumentId& document_id,  uint64_t timestamp, PendingTracker& tracker) override;

    static std::unique_ptr<storage::IStorageChainBuilder> get_storage_chain_builder();
};

}
