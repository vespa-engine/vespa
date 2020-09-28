// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storage_api_chain_bm_feed_handler.h"
#include "pending_tracker.h"
#include "storage_reply_error_checker.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storage/common/storage_chain_builder.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <cassert>

using document::Document;
using document::DocumentId;
using document::DocumentUpdate;
using storage::StorageLink;

namespace feedbm {

namespace {

std::shared_ptr<storage::api::StorageCommand> make_set_cluster_state_cmd() {
    storage::lib::ClusterStateBundle bundle(storage::lib::ClusterState("version:2 distributor:1 storage:1"));
    auto cmd = std::make_shared<storage::api::SetSystemStateCommand>(bundle);
    cmd->setPriority(storage::api::StorageMessage::VERYHIGH);
    return cmd;
}

}

class BmStorageLink : public StorageLink,
                      public StorageReplyErrorChecker
{
    std::mutex _mutex;
    vespalib::hash_map<uint64_t, PendingTracker *> _pending;
public:
    BmStorageLink();
    ~BmStorageLink() override;
    bool onDown(const std::shared_ptr<storage::api::StorageMessage>& msg) override;
    bool onUp(const std::shared_ptr<storage::api::StorageMessage>& msg) override;
    void retain(uint64_t msg_id, PendingTracker &tracker) {
        tracker.retain();
        std::lock_guard lock(_mutex);
        _pending.insert(std::make_pair(msg_id, &tracker));
    }
    bool release(uint64_t msg_id) {
        PendingTracker *tracker = nullptr;
        {
            std::lock_guard lock(_mutex);
            auto itr = _pending.find(msg_id);
            if (itr == _pending.end()) {
                return false;
            }
            tracker = itr->second;
            _pending.erase(itr);
        }
        tracker->release();
        return true;
    }
};

BmStorageLink::BmStorageLink()
    : storage::StorageLink("vespa-bm-feed"),
      StorageReplyErrorChecker(),
      _mutex(),
      _pending()
{
}

BmStorageLink::~BmStorageLink()
{
    std::lock_guard lock(_mutex);
    assert(_pending.empty());
}

bool
BmStorageLink::onDown(const std::shared_ptr<storage::api::StorageMessage>& msg)
{
    (void) msg;
    return false;
}

bool
BmStorageLink::onUp(const std::shared_ptr<storage::api::StorageMessage>& msg)
{
    check_error(*msg);
    return release(msg->getMsgId());
}

struct StorageApiChainBmFeedHandler::Context {
    BmStorageLink* bm_link;
    Context()
        : bm_link(nullptr)
    {
    }
    ~Context() = default;
};

class MyStorageChainBuilder : public storage::StorageChainBuilder
{
    using Parent = storage::StorageChainBuilder;
    std::shared_ptr<StorageApiChainBmFeedHandler::Context> _context;
public:
    MyStorageChainBuilder(std::shared_ptr<StorageApiChainBmFeedHandler::Context> context);
    ~MyStorageChainBuilder() override;
    void add(std::unique_ptr<StorageLink> link) override;
};

MyStorageChainBuilder::MyStorageChainBuilder(std::shared_ptr<StorageApiChainBmFeedHandler::Context> context)
    : storage::StorageChainBuilder(),
      _context(std::move(context))
{
}

MyStorageChainBuilder::~MyStorageChainBuilder() = default;

void
MyStorageChainBuilder::add(std::unique_ptr<StorageLink> link)
{
    vespalib::string name = link->getName();
    Parent::add(std::move(link));
    if (name == "Communication manager") {
        auto my_link = std::make_unique<BmStorageLink>();
        _context->bm_link = my_link.get();
        Parent::add(std::move(my_link));
    }
}

StorageApiChainBmFeedHandler::StorageApiChainBmFeedHandler(std::shared_ptr<Context> context)
    : IBmFeedHandler(),
      _context(std::move(context))
{
    auto cmd = make_set_cluster_state_cmd();
    PendingTracker tracker(1);
    send_msg(std::move(cmd), tracker);
    tracker.drain();
}

StorageApiChainBmFeedHandler::~StorageApiChainBmFeedHandler() = default;

void
StorageApiChainBmFeedHandler::send_msg(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& pending_tracker)
{
    cmd->setSourceIndex(0);
    auto bm_link = _context->bm_link;
    bm_link->retain(cmd->getMsgId(), pending_tracker);
    bm_link->sendDown(std::move(cmd));
}

void
StorageApiChainBmFeedHandler::put(const document::Bucket& bucket, std::unique_ptr<Document> document, uint64_t timestamp, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::PutCommand>(bucket, std::move(document), timestamp);
    send_msg(std::move(cmd), tracker);
}

void
StorageApiChainBmFeedHandler::update(const document::Bucket& bucket, std::unique_ptr<DocumentUpdate> document_update, uint64_t timestamp, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::UpdateCommand>(bucket, std::move(document_update), timestamp);
    send_msg(std::move(cmd), tracker);
}

void
StorageApiChainBmFeedHandler::remove(const document::Bucket& bucket, const DocumentId& document_id,  uint64_t timestamp, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::RemoveCommand>(bucket, document_id, timestamp);
    send_msg(std::move(cmd), tracker);
}

std::shared_ptr<StorageApiChainBmFeedHandler::Context>
StorageApiChainBmFeedHandler::get_context()
{
    return std::make_shared<Context>();
}

std::unique_ptr<storage::IStorageChainBuilder>
StorageApiChainBmFeedHandler::get_storage_chain_builder(std::shared_ptr<Context> context)
{
    return std::make_unique<MyStorageChainBuilder>(std::move(context));
}

uint32_t
StorageApiChainBmFeedHandler::get_error_count() const
{
    return _context->bm_link->get_error_count();
}

}
