// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storage_api_chain_bm_feed_handler.h"
#include "i_bm_distribution.h"
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

namespace search::bmcluster {

StorageApiChainBmFeedHandler::StorageApiChainBmFeedHandler(std::vector<std::shared_ptr<BmStorageLinkContext>> contexts, const IBmDistribution& distribution, bool distributor)
    : IBmFeedHandler(),
      _name(vespalib::string("StorageApiChainBmFeedHandler(") + (distributor ? "distributor" : "service-layer") + ")"),
      _distributor(distributor),
      _contexts(std::move(contexts)),
      _no_link_error_count(0u),
      _distribution(distribution)
{
}

StorageApiChainBmFeedHandler::~StorageApiChainBmFeedHandler() = default;

void
StorageApiChainBmFeedHandler::send_msg(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& pending_tracker)
{
    auto bucket = cmd->getBucket();
    if (_distributor) {
        cmd->setSourceIndex(0);
    } else {
        cmd->setSourceIndex(_distribution.get_distributor_node_idx(bucket));
    }
    uint32_t node_idx = _distributor ? _distribution.get_distributor_node_idx(bucket) : _distribution.get_service_layer_node_idx(bucket);
    if (node_idx < _contexts.size() && _contexts[node_idx]) {
        auto bm_link = _contexts[node_idx]->bm_link;
        bm_link->retain(cmd->getMsgId(), pending_tracker);
        bm_link->sendDown(std::move(cmd));
    } else {
        ++_no_link_error_count;
    }
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
    uint32_t error_count = 0;
    for (auto &context : _contexts) {
        if (context) {
            error_count += context->bm_link->get_error_count();
        }
    }
    error_count += _no_link_error_count;
    return error_count;
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
