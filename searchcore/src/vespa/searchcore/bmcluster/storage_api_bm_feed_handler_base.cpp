// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storage_api_bm_feed_handler_base.h"
#include "i_bm_distribution.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/storageapi/message/persistence.h>

using document::Document;
using document::DocumentId;
using document::DocumentUpdate;

namespace search::bmcluster {

StorageApiBmFeedHandlerBase::StorageApiBmFeedHandlerBase(const vespalib::string &base_name, const IBmDistribution &distribution, bool distributor)
    : _name(base_name + "(" + (distributor ? "distributor" : "service-layer") + ")"),
      _distribution(distribution),
      _distributor(distributor)
{
}

StorageApiBmFeedHandlerBase::~StorageApiBmFeedHandlerBase() = default;

uint32_t
StorageApiBmFeedHandlerBase::route_cmd(storage::api::StorageCommand& cmd)
{
    auto bucket = cmd.getBucket();
    cmd.setSourceIndex(_distributor ? 0 : _distribution.get_distributor_node_idx(bucket));
    return _distributor ? _distribution.get_distributor_node_idx(bucket) : _distribution.get_service_layer_node_idx(bucket);
}

void
StorageApiBmFeedHandlerBase::put(const document::Bucket& bucket, std::unique_ptr<Document> document, uint64_t timestamp, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::PutCommand>(bucket, std::move(document), timestamp);
    send_cmd(std::move(cmd), tracker);
}

void
StorageApiBmFeedHandlerBase::update(const document::Bucket& bucket, std::unique_ptr<DocumentUpdate> document_update, uint64_t timestamp, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::UpdateCommand>(bucket, std::move(document_update), timestamp);
    send_cmd(std::move(cmd), tracker);
}

void
StorageApiBmFeedHandlerBase::remove(const document::Bucket& bucket, const DocumentId& document_id,  uint64_t timestamp, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::RemoveCommand>(bucket, document_id, timestamp);
    send_cmd(std::move(cmd), tracker);
}

void
StorageApiBmFeedHandlerBase::get(const document::Bucket& bucket, vespalib::stringref field_set_string, const document::DocumentId& document_id, PendingTracker& tracker)
{
    auto cmd = std::make_unique<storage::api::GetCommand>(bucket, document_id, field_set_string);
    send_cmd(std::move(cmd), tracker);
}

const vespalib::string&
StorageApiBmFeedHandlerBase::get_name() const
{
    return _name;
}

bool
StorageApiBmFeedHandlerBase::manages_timestamp() const
{
    return _distributor;
}

}
