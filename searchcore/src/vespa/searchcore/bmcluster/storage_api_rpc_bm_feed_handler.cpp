// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storage_api_rpc_bm_feed_handler.h"
#include "i_bm_distribution.h"
#include "pending_tracker.h"
#include "pending_tracker_hash.h"
#include "storage_reply_error_checker.h"
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storage/storageserver/message_dispatcher.h>
#include <vespa/storage/storageserver/rpc/message_codec_provider.h>
#include <vespa/storage/storageserver/rpc/shared_rpc_resources.h>
#include <vespa/storage/storageserver/rpc/storage_api_rpc_service.h>

using document::DocumentTypeRepo;
using storage::rpc::SharedRpcResources;
using storage::rpc::StorageApiRpcService;

namespace search::bmcluster {

class StorageApiRpcBmFeedHandler::MyMessageDispatcher : public storage::MessageDispatcher,
                                 public StorageReplyErrorChecker
{
    PendingTrackerHash _pending_hash;
public:
    MyMessageDispatcher()
        : storage::MessageDispatcher(),
          StorageReplyErrorChecker(),
          _pending_hash()
    {
    }
    ~MyMessageDispatcher() override;
    void dispatch_sync(std::shared_ptr<storage::api::StorageMessage> msg) override {
        check_error(*msg);
        release(msg->getMsgId());
    }
    void dispatch_async(std::shared_ptr<storage::api::StorageMessage> msg) override {
        check_error(*msg);
        release(msg->getMsgId());
    }
    void retain(uint64_t msg_id, PendingTracker &tracker) { _pending_hash.retain(msg_id, tracker); }
    void release(uint64_t msg_id) {
        auto tracker = _pending_hash.release(msg_id);
        if (tracker != nullptr) {
            tracker->release();
        } else {
            ++_errors;
        }
    }
};

StorageApiRpcBmFeedHandler::MyMessageDispatcher::~MyMessageDispatcher()
{
}

StorageApiRpcBmFeedHandler::StorageApiRpcBmFeedHandler(SharedRpcResources& shared_rpc_resources_in,
                                                       std::shared_ptr<const DocumentTypeRepo> repo,
                                                       const StorageApiRpcService::Params& rpc_params,
                                                       const IBmDistribution& distribution,
                                                       bool distributor)
    : StorageApiBmFeedHandlerBase("StorageApiRpcBmFeedHandler", distribution, distributor),
      _addresses(distribution.get_num_nodes(), distributor),
      _no_address_error_count(0u),
      _shared_rpc_resources(shared_rpc_resources_in),
      _message_dispatcher(std::make_unique<MyMessageDispatcher>()),
      _message_codec_provider(std::make_unique<storage::rpc::MessageCodecProvider>(repo)),
      _rpc_client(std::make_unique<storage::rpc::StorageApiRpcService>(*_message_dispatcher, _shared_rpc_resources, *_message_codec_provider, rpc_params))
{
}

StorageApiRpcBmFeedHandler::~StorageApiRpcBmFeedHandler() = default;

void
StorageApiRpcBmFeedHandler::send_cmd(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& tracker)
{
    uint32_t node_idx = route_cmd(*cmd);
    if (_addresses.has_address(node_idx)) {
        cmd->setAddress(_addresses.get_address(node_idx));
        _message_dispatcher->retain(cmd->getMsgId(), tracker);
        _rpc_client->send_rpc_v1_request(std::move(cmd));
    } else {
        ++_no_address_error_count;
    }
}

void
StorageApiRpcBmFeedHandler::attach_bucket_info_queue(PendingTracker&)
{
}

uint32_t
StorageApiRpcBmFeedHandler::get_error_count() const
{
    return _message_dispatcher->get_error_count() + _no_address_error_count;
}

}
