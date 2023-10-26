// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "storage_api_bm_feed_handler_base.h"
#include "bm_storage_message_addresses.h"
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

namespace search::bmcluster {

class IBmDistribution;

/*
 * Benchmark feed handler for feed to service layer or distributor
 * using storage api protocol over rpc.
 */
class StorageApiRpcBmFeedHandler : public StorageApiBmFeedHandlerBase
{
    class MyMessageDispatcher;
    BmStorageMessageAddresses                           _addresses;
    std::atomic<uint32_t>                               _no_address_error_count;
    storage::rpc::SharedRpcResources&                   _shared_rpc_resources;
    std::unique_ptr<MyMessageDispatcher>                _message_dispatcher;
    std::unique_ptr<storage::rpc::MessageCodecProvider> _message_codec_provider;
    std::unique_ptr<storage::rpc::StorageApiRpcService> _rpc_client;

    void send_cmd(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& tracker) override;
public:
    StorageApiRpcBmFeedHandler(storage::rpc::SharedRpcResources& shared_rpc_resources_in,
                               std::shared_ptr<const document::DocumentTypeRepo> repo,
                               const storage::rpc::StorageApiRpcService::Params& rpc_params,
                               const IBmDistribution& distribution,
                               bool distributor);
    ~StorageApiRpcBmFeedHandler();
    void attach_bucket_info_queue(PendingTracker &tracker) override;
    uint32_t get_error_count() const override;
};

}
