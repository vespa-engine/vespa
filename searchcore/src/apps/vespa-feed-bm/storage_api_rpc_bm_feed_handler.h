// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_bm_feed_handler.h"
#include <vespa/storage/storageserver/rpc/storage_api_rpc_service.h>

namespace document { class DocumentTypeRepo; }
namespace storage::api {
class StorageMessageAddress;
class StorageCommand;
}

namespace storage::rpc {
class MessageCodecProvider;
class SharedRpcResources;
}

namespace feedbm {

/*
 * Benchmark feed handler for feed to service layer or distributor
 * using storage api protocol over rpc.
 */
class StorageApiRpcBmFeedHandler : public IBmFeedHandler
{
    class MyMessageDispatcher;
    vespalib::string _name;
    bool             _distributor;
    std::unique_ptr<storage::api::StorageMessageAddress> _storage_address;
    storage::rpc::SharedRpcResources&          _shared_rpc_resources;
    std::unique_ptr<MyMessageDispatcher>       _message_dispatcher;
    std::unique_ptr<storage::rpc::MessageCodecProvider> _message_codec_provider;
    std::unique_ptr<storage::rpc::StorageApiRpcService> _rpc_client;

    void send_rpc(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& tracker);
public:
    StorageApiRpcBmFeedHandler(storage::rpc::SharedRpcResources& shared_rpc_resources_in,
                               std::shared_ptr<const document::DocumentTypeRepo> repo,
                               const storage::rpc::StorageApiRpcService::Params& rpc_params,
                               bool distributor);
    ~StorageApiRpcBmFeedHandler();
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
