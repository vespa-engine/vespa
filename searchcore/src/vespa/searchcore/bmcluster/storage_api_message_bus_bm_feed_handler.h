// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "storage_api_bm_feed_handler_base.h"
#include "bm_message_bus_routes.h"
#include <atomic>

namespace storage::api { class StorageCommand; }

namespace search::bmcluster {

class BmMessageBus;
class IBmDistribution;

/*
 * Benchmark feed handler for feed to service layer or distributor
 * using storage api protocol over message bus.
 */
class StorageApiMessageBusBmFeedHandler : public StorageApiBmFeedHandlerBase
{
    BmMessageBus&          _message_bus;
    BmMessageBusRoutes     _routes;
    std::atomic<uint32_t>  _no_route_error_count;
    void send_cmd(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& tracker) override;
public:
    StorageApiMessageBusBmFeedHandler(BmMessageBus &message_bus, const IBmDistribution& distribution, bool distributor);
    ~StorageApiMessageBusBmFeedHandler();
    void attach_bucket_info_queue(PendingTracker &tracker) override;
    uint32_t get_error_count() const override;
};

}
