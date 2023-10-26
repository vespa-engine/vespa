// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "storage_api_bm_feed_handler_base.h"
#include <vector>
#include <atomic>

namespace storage::api { class StorageCommand; }

namespace search::bmcluster {

struct BmStorageLinkContext;
class IBmDistribution;

/*
 * Benchmark feed handler for feed to service layer or distributor
 * using storage api protocol directly on the storage chain.
 */
class StorageApiChainBmFeedHandler : public StorageApiBmFeedHandlerBase
{
private:
    std::vector<std::shared_ptr<BmStorageLinkContext>> _contexts;
    std::atomic<uint32_t>                              _no_link_error_count;

    void send_cmd(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& tracker) override;
public:
    StorageApiChainBmFeedHandler(std::vector<std::shared_ptr<BmStorageLinkContext>> contexts, const IBmDistribution& distribution, bool distributor);
    ~StorageApiChainBmFeedHandler();
    void attach_bucket_info_queue(PendingTracker &tracker) override;
    uint32_t get_error_count() const override;
};

}
