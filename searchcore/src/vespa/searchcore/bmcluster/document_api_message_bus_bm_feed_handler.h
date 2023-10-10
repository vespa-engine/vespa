// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_bm_feed_handler.h"
#include "bm_message_bus_routes.h"
#include <atomic>

namespace documentapi { class DocumentMessage; };

namespace search::bmcluster {

class BmMessageBus;
class IBmDistribution;

/*
 * Benchmark feed handler for feed to distributor using document api protocol
 * over message bus.
 */
class DocumentApiMessageBusBmFeedHandler : public IBmFeedHandler
{
    vespalib::string       _name;
    BmMessageBus&          _message_bus;
    BmMessageBusRoutes     _routes;
    std::atomic<uint32_t>  _no_route_error_count;
    const IBmDistribution& _distribution;
    void send_msg(const document::Bucket& bucket, std::unique_ptr<documentapi::DocumentMessage> msg, PendingTracker& tracker);
public:
    DocumentApiMessageBusBmFeedHandler(BmMessageBus &message_bus, const IBmDistribution& distribution);
    ~DocumentApiMessageBusBmFeedHandler();
    void put(const document::Bucket& bucket, std::unique_ptr<document::Document> document, uint64_t timestamp, PendingTracker& tracker) override;
    void update(const document::Bucket& bucket, std::unique_ptr<document::DocumentUpdate> document_update, uint64_t timestamp, PendingTracker& tracker) override;
    void remove(const document::Bucket& bucket, const document::DocumentId& document_id,  uint64_t timestamp, PendingTracker& tracker) override;
    void get(const document::Bucket& bucket, vespalib::stringref field_set_string, const document::DocumentId& document_id, PendingTracker& tracker) override;
    void attach_bucket_info_queue(PendingTracker &tracker) override;
    uint32_t get_error_count() const override;
    const vespalib::string &get_name() const override;
    bool manages_timestamp() const override;
};

}
