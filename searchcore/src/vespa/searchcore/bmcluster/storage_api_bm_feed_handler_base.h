// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_bm_feed_handler.h"

namespace storage::api { class StorageCommand; }

namespace search::bmcluster {

class IBmDistribution;

/*
 * Base class for benchmark feed handlers that feed to service layer
 * or distributor using storage api protocol.
 */
class StorageApiBmFeedHandlerBase : public IBmFeedHandler
{
protected:
    vespalib::string       _name;
    const IBmDistribution& _distribution;
    bool                   _distributor;

    virtual void send_cmd(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& tracker) = 0;
    uint32_t route_cmd(storage::api::StorageCommand& cmd);
public:
    StorageApiBmFeedHandlerBase(const vespalib::string& base_name, const IBmDistribution &distribution, bool distributor);
    ~StorageApiBmFeedHandlerBase();
    void put(const document::Bucket& bucket, std::unique_ptr<document::Document> document, uint64_t timestamp, PendingTracker& tracker) override;
    void update(const document::Bucket& bucket, std::unique_ptr<document::DocumentUpdate> document_update, uint64_t timestamp, PendingTracker& tracker) override;
    void remove(const document::Bucket& bucket, const document::DocumentId& document_id,  uint64_t timestamp, PendingTracker& tracker) override;
    void get(const document::Bucket& bucket, vespalib::stringref field_set_string, const document::DocumentId& document_id, PendingTracker& tracker) override;
    const vespalib::string &get_name() const override;
    bool manages_timestamp() const override;
};

}
