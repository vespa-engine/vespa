// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storage_api_rpc_bm_feed_handler.h"
#include "pending_tracker.h"
#include "storage_reply_error_checker.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/documentapi/loadtypes/loadtypeset.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storage/storageserver/message_dispatcher.h>
#include <vespa/storage/storageserver/rpc/caching_rpc_target_resolver.h>
#include <vespa/storage/storageserver/rpc/message_codec_provider.h>
#include <vespa/storage/storageserver/rpc/shared_rpc_resources.h>
#include <vespa/storage/storageserver/rpc/slime_cluster_state_bundle_codec.h>
#include <vespa/storage/storageserver/rpc/storage_api_rpc_service.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/slobrok/sbmirror.h>
#include <cassert>

using document::Document;
using document::DocumentId;
using document::DocumentUpdate;
using document::DocumentTypeRepo;
using storage::api::StorageMessageAddress;
using storage::rpc::SharedRpcResources;

namespace feedbm {

namespace {

FRT_RPCRequest *
make_set_cluster_state_request() {
    storage::lib::ClusterStateBundle bundle(storage::lib::ClusterState("version:2 distributor:1 storage:1"));
    storage::rpc::SlimeClusterStateBundleCodec codec;
    auto encoded_bundle = codec.encode(bundle);
    auto *req = new FRT_RPCRequest();
    auto* params = req->GetParams();
    params->AddInt8(static_cast<uint8_t>(encoded_bundle._compression_type));
    params->AddInt32(encoded_bundle._uncompressed_length);
    params->AddData(std::move(*encoded_bundle._buffer));
    req->SetMethodName("setdistributionstates");
    return req;
}

void
set_cluster_up(SharedRpcResources &shared_rpc_resources, storage::api::StorageMessageAddress &storage_address) {
    auto req = make_set_cluster_state_request();
    auto target_resolver = std::make_unique<storage::rpc::CachingRpcTargetResolver>(shared_rpc_resources.slobrok_mirror(), shared_rpc_resources.target_factory());
    auto target = target_resolver->resolve_rpc_target(storage_address);
    target->_target->get()->InvokeSync(req, 10.0); // 10 seconds timeout
    assert(!req->IsError());
    req->SubRef();
}

}

class StorageApiRpcBmFeedHandler::MyMessageDispatcher : public storage::MessageDispatcher,
                                 public StorageReplyErrorChecker
{
    std::mutex _mutex;
    vespalib::hash_map<uint64_t, PendingTracker *> _pending;
public:
    MyMessageDispatcher()
        : storage::MessageDispatcher(),
          StorageReplyErrorChecker(),
          _mutex(),
          _pending()
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
    void retain(uint64_t msg_id, PendingTracker &tracker) {
        tracker.retain();
        std::lock_guard lock(_mutex);
        _pending.insert(std::make_pair(msg_id, &tracker));
    }
    void release(uint64_t msg_id) {
        PendingTracker *tracker = nullptr;
        {
            std::lock_guard lock(_mutex);
            auto itr = _pending.find(msg_id);
            assert(itr != _pending.end());
            tracker = itr->second;
            _pending.erase(itr);
        }
        tracker->release();
    }
};

StorageApiRpcBmFeedHandler::MyMessageDispatcher::~MyMessageDispatcher()
{
    std::lock_guard lock(_mutex);
    assert(_pending.empty());
}

StorageApiRpcBmFeedHandler::StorageApiRpcBmFeedHandler(SharedRpcResources& shared_rpc_resources_in, std::shared_ptr<const DocumentTypeRepo> repo)
    : IBmFeedHandler(),
      _storage_address(std::make_unique<StorageMessageAddress>("storage", storage::lib::NodeType::STORAGE, 0)),
      _shared_rpc_resources(shared_rpc_resources_in),
      _message_dispatcher(std::make_unique<MyMessageDispatcher>()),
      _message_codec_provider(std::make_unique<storage::rpc::MessageCodecProvider>(repo, std::make_shared<documentapi::LoadTypeSet>())),
      _rpc_client(std::make_unique<storage::rpc::StorageApiRpcService>(*_message_dispatcher, _shared_rpc_resources, *_message_codec_provider, storage::rpc::StorageApiRpcService::Params()))
{
    set_cluster_up(_shared_rpc_resources, *_storage_address);
}

StorageApiRpcBmFeedHandler::~StorageApiRpcBmFeedHandler() = default;

void
StorageApiRpcBmFeedHandler::send_rpc(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& pending_tracker)
{
    cmd->setSourceIndex(0);
    cmd->setAddress(*_storage_address);
    _message_dispatcher->retain(cmd->getMsgId(), pending_tracker);
    _rpc_client->send_rpc_v1_request(std::move(cmd));
}

void
StorageApiRpcBmFeedHandler::put(const document::Bucket& bucket, std::unique_ptr<Document> document, uint64_t timestamp, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::PutCommand>(bucket, std::move(document), timestamp);
    send_rpc(std::move(cmd), tracker);
}

void
StorageApiRpcBmFeedHandler::update(const document::Bucket& bucket, std::unique_ptr<DocumentUpdate> document_update, uint64_t timestamp, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::UpdateCommand>(bucket, std::move(document_update), timestamp);
    send_rpc(std::move(cmd), tracker);
}

void
StorageApiRpcBmFeedHandler::remove(const document::Bucket& bucket, const DocumentId& document_id,  uint64_t timestamp, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::RemoveCommand>(bucket, document_id, timestamp);
    send_rpc(std::move(cmd), tracker);
}

uint32_t
StorageApiRpcBmFeedHandler::get_error_count() const
{
    return _message_dispatcher->get_error_count();
}

}
