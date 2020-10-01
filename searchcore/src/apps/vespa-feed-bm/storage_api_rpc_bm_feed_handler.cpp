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
#include <vespa/storage/storageserver/rpc/message_codec_provider.h>
#include <vespa/storage/storageserver/rpc/shared_rpc_resources.h>
#include <vespa/storage/storageserver/rpc/storage_api_rpc_service.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <cassert>

using document::Document;
using document::DocumentId;
using document::DocumentUpdate;
using document::DocumentTypeRepo;
using storage::api::StorageMessageAddress;
using storage::rpc::SharedRpcResources;
using storage::rpc::StorageApiRpcService;
using storage::lib::NodeType;

namespace feedbm {

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

StorageApiRpcBmFeedHandler::StorageApiRpcBmFeedHandler(SharedRpcResources& shared_rpc_resources_in,
                                                       std::shared_ptr<const DocumentTypeRepo> repo,
                                                       const StorageApiRpcService::Params& rpc_params,
                                                       bool distributor)
    : IBmFeedHandler(),
      _name(vespalib::string("StorageApiRpcBmFeedHandler(") + (distributor ? "distributor" : "servicelayer") + ")"),
      _distributor(distributor),
      _storage_address(std::make_unique<StorageMessageAddress>("storage", distributor ? NodeType::DISTRIBUTOR : NodeType::STORAGE, 0)),
      _shared_rpc_resources(shared_rpc_resources_in),
      _message_dispatcher(std::make_unique<MyMessageDispatcher>()),
      _message_codec_provider(std::make_unique<storage::rpc::MessageCodecProvider>(repo, std::make_shared<documentapi::LoadTypeSet>())),
      _rpc_client(std::make_unique<storage::rpc::StorageApiRpcService>(*_message_dispatcher, _shared_rpc_resources, *_message_codec_provider, rpc_params))
{
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

const vespalib::string&
StorageApiRpcBmFeedHandler::get_name() const
{
    return _name;
}

bool
StorageApiRpcBmFeedHandler::manages_buckets() const
{
    return _distributor;
}

bool
StorageApiRpcBmFeedHandler::manages_timestamp() const
{
    return _distributor;
}

}
