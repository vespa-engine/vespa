// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storage_api_chain_bm_feed_handler.h"
#include "pending_tracker.h"
#include "storage_reply_error_checker.h"
#include "bm_storage_link_context.h"
#include "bm_storage_link.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vdslib/state/cluster_state_bundle.h>
#include <cassert>

using document::Document;
using document::DocumentId;
using document::DocumentUpdate;

namespace feedbm {

namespace {

std::shared_ptr<storage::api::StorageCommand> make_set_cluster_state_cmd() {
    storage::lib::ClusterStateBundle bundle(storage::lib::ClusterState("version:2 distributor:1 storage:1"));
    auto cmd = std::make_shared<storage::api::SetSystemStateCommand>(bundle);
    cmd->setPriority(storage::api::StorageMessage::VERYHIGH);
    return cmd;
}

}

StorageApiChainBmFeedHandler::StorageApiChainBmFeedHandler(std::shared_ptr<BmStorageLinkContext> context, bool distributor)
    : IBmFeedHandler(),
      _name(vespalib::string("StorageApiChainBmFeedHandler(") + (distributor ? "distributor" : "service-layer") + ")"),
      _distributor(distributor),
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

void
StorageApiChainBmFeedHandler::get(const document::Bucket& bucket, vespalib::stringref field_set_string, const document::DocumentId& document_id, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::GetCommand>(bucket, document_id, field_set_string);
    send_msg(std::move(cmd), tracker);
}

void
StorageApiChainBmFeedHandler::attach_bucket_info_queue(PendingTracker&)
{
}

uint32_t
StorageApiChainBmFeedHandler::get_error_count() const
{
    return _context->bm_link->get_error_count();
}

const vespalib::string&
StorageApiChainBmFeedHandler::get_name() const
{
    return _name;
}

bool
StorageApiChainBmFeedHandler::manages_timestamp() const
{
    return _distributor;
}

}
