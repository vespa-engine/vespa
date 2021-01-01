// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_bm_feed_handler.h"
#include <vespa/messagebus/routing/route.h>

namespace document { class DocumentTypeRepo; }
namespace documentapi { class DocumentMessage; };
namespace storage::api {
class StorageCommand;
class StorageMessageAddress;
}

namespace feedbm {

class BmMessageBus;

/*
 * Benchmark feed handler for feed to service layer or distributor
 * using storage api protocol over message bus.
 */
class StorageApiMessageBusBmFeedHandler : public IBmFeedHandler
{
    vespalib::string _name;
    bool             _distributor;
    std::unique_ptr<storage::api::StorageMessageAddress> _storage_address;
    BmMessageBus&                                        _message_bus;
    mbus::Route _route;
    void send_msg(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& tracker);
public:
    StorageApiMessageBusBmFeedHandler(BmMessageBus &message_bus, bool distributor);
    ~StorageApiMessageBusBmFeedHandler();
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
