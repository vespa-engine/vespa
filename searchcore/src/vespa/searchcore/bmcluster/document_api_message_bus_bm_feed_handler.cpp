// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_api_message_bus_bm_feed_handler.h"
#include "bm_message_bus.h"
#include "i_bm_distribution.h"
#include "pending_tracker.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/documentapi/messagebus/messages/getdocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/putdocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/removedocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/updatedocumentmessage.h>
#include <vespa/storageapi/messageapi/storagemessage.h>

using document::Document;
using document::DocumentId;
using document::DocumentUpdate;
using storage::api::StorageMessageAddress;
using storage::lib::NodeType;

namespace search::bmcluster {

DocumentApiMessageBusBmFeedHandler::DocumentApiMessageBusBmFeedHandler(BmMessageBus &message_bus, const IBmDistribution& distribution)
    : IBmFeedHandler(),
      _name(vespalib::string("DocumentApiMessageBusBmFeedHandler(distributor)")),
      _message_bus(message_bus),
      _routes(distribution.get_num_nodes(), true),
      _no_route_error_count(0u),
      _distribution(distribution)
{
}

DocumentApiMessageBusBmFeedHandler::~DocumentApiMessageBusBmFeedHandler() = default;

void
DocumentApiMessageBusBmFeedHandler::send_msg(const document::Bucket& bucket, std::unique_ptr<documentapi::DocumentMessage> msg, PendingTracker& pending_tracker)
{
    uint32_t node_idx = _distribution.get_distributor_node_idx(bucket);
    if (_routes.has_route(node_idx)) {
        auto &route = _routes.get_route(node_idx);
        _message_bus.send_msg(std::move(msg), route, pending_tracker);
    } else {
        ++_no_route_error_count;
    }
}

void
DocumentApiMessageBusBmFeedHandler::put(const document::Bucket& bucket, std::unique_ptr<Document> document, uint64_t, PendingTracker& tracker)
{
    auto msg = std::make_unique<documentapi::PutDocumentMessage>(std::move(document));
    send_msg(bucket, std::move(msg), tracker);
}

void
DocumentApiMessageBusBmFeedHandler::update(const document::Bucket& bucket, std::unique_ptr<DocumentUpdate> document_update, uint64_t, PendingTracker& tracker)
{
    auto msg = std::make_unique<documentapi::UpdateDocumentMessage>(std::move(document_update));
    send_msg(bucket, std::move(msg), tracker);
}

void
DocumentApiMessageBusBmFeedHandler::remove(const document::Bucket& bucket, const DocumentId& document_id, uint64_t, PendingTracker& tracker)
{
    auto msg = std::make_unique<documentapi::RemoveDocumentMessage>(document_id);
    send_msg(bucket, std::move(msg), tracker);
}

void
DocumentApiMessageBusBmFeedHandler::get(const document::Bucket& bucket, vespalib::stringref field_set_string, const document::DocumentId& document_id, PendingTracker& tracker)
{
    auto msg = std::make_unique<documentapi::GetDocumentMessage>(document_id, field_set_string);
    send_msg(bucket, std::move(msg), tracker);
}

void
DocumentApiMessageBusBmFeedHandler::attach_bucket_info_queue(PendingTracker&)
{
}

uint32_t
DocumentApiMessageBusBmFeedHandler::get_error_count() const
{
    return _message_bus.get_error_count() + _no_route_error_count;
}

const vespalib::string&
DocumentApiMessageBusBmFeedHandler::get_name() const
{
    return _name;
}

bool
DocumentApiMessageBusBmFeedHandler::manages_timestamp() const
{
    return true;
}

}
