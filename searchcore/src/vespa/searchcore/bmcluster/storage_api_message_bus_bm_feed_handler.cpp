// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storage_api_message_bus_bm_feed_handler.h"
#include "bm_message_bus.h"
#include "i_bm_distribution.h"
#include "pending_tracker.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/mbusprot/storagecommand.h>

using document::Document;
using document::DocumentId;
using document::DocumentUpdate;
using storage::api::StorageMessageAddress;
using storage::lib::NodeType;

namespace search::bmcluster {

StorageApiMessageBusBmFeedHandler::StorageApiMessageBusBmFeedHandler(BmMessageBus &message_bus, const IBmDistribution& distribution, bool distributor)
    : IBmFeedHandler(),
      _name(vespalib::string("StorageApiMessageBusBmFeedHandler(") + (distributor ? "distributor" : "service-layer") + ")"),
      _distributor(distributor),
      _message_bus(message_bus),
      _routes(distribution.get_num_nodes(), distributor),
      _no_route_error_count(0),
      _distribution(distribution)
{
}

StorageApiMessageBusBmFeedHandler::~StorageApiMessageBusBmFeedHandler() = default;

void
StorageApiMessageBusBmFeedHandler::send_msg(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& pending_tracker)
{
    auto bucket = cmd->getBucket();
    if (_distributor) {
        cmd->setSourceIndex(0);
    } else {
        cmd->setSourceIndex(_distribution.get_distributor_node_idx(bucket));
    }
    auto msg = std::make_unique<storage::mbusprot::StorageCommand>(cmd);
    uint32_t node_idx = _distributor ? _distribution.get_distributor_node_idx(bucket) : _distribution.get_service_layer_node_idx(bucket);
    if (_routes.has_route(node_idx)) {
        auto& route = _routes.get_route(node_idx);
        _message_bus.send_msg(std::move(msg), route, pending_tracker);
    } else {
        ++_no_route_error_count;
    }
}

void
StorageApiMessageBusBmFeedHandler::put(const document::Bucket& bucket, std::unique_ptr<Document> document, uint64_t timestamp, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::PutCommand>(bucket, std::move(document), timestamp);
    send_msg(std::move(cmd), tracker);
}

void
StorageApiMessageBusBmFeedHandler::update(const document::Bucket& bucket, std::unique_ptr<DocumentUpdate> document_update, uint64_t timestamp, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::UpdateCommand>(bucket, std::move(document_update), timestamp);
    send_msg(std::move(cmd), tracker);
}

void
StorageApiMessageBusBmFeedHandler::remove(const document::Bucket& bucket, const DocumentId& document_id,  uint64_t timestamp, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::RemoveCommand>(bucket, document_id, timestamp);
    send_msg(std::move(cmd), tracker);
}

void
StorageApiMessageBusBmFeedHandler::get(const document::Bucket& bucket, vespalib::stringref field_set_string, const document::DocumentId& document_id, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::GetCommand>(bucket, document_id, field_set_string);
    send_msg(std::move(cmd), tracker);
}

void
StorageApiMessageBusBmFeedHandler::attach_bucket_info_queue(PendingTracker&)
{
}

uint32_t
StorageApiMessageBusBmFeedHandler::get_error_count() const
{
    return _message_bus.get_error_count() + _no_route_error_count;
}

const vespalib::string&
StorageApiMessageBusBmFeedHandler::get_name() const
{
    return _name;
}

bool
StorageApiMessageBusBmFeedHandler::manages_timestamp() const
{
    return _distributor;
}

}
