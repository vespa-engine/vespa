// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storage_api_message_bus_bm_feed_handler.h"
#include "bm_message_bus.h"
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

namespace feedbm {

namespace {
    vespalib::string _Storage("storage");
}
StorageApiMessageBusBmFeedHandler::StorageApiMessageBusBmFeedHandler(BmMessageBus &message_bus, bool distributor)
    : IBmFeedHandler(),
      _name(vespalib::string("StorageApiMessageBusBmFeedHandler(") + (distributor ? "distributor" : "service-layer") + ")"),
      _distributor(distributor),
      _storage_address(std::make_unique<StorageMessageAddress>(&_Storage, distributor ? NodeType::DISTRIBUTOR : NodeType::STORAGE, 0)),
      _message_bus(message_bus),
      _route(_storage_address->to_mbus_route())
{
}

StorageApiMessageBusBmFeedHandler::~StorageApiMessageBusBmFeedHandler() = default;

void
StorageApiMessageBusBmFeedHandler::send_msg(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& pending_tracker)
{
    cmd->setSourceIndex(0);
    auto msg = std::make_unique<storage::mbusprot::StorageCommand>(cmd);
    _message_bus.send_msg(std::move(msg), _route, pending_tracker);
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
    return _message_bus.get_error_count();
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
