// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storage_api_message_bus_bm_feed_handler.h"
#include "bm_message_bus.h"
#include "i_bm_distribution.h"
#include <vespa/storageapi/mbusprot/storagecommand.h>

namespace search::bmcluster {

StorageApiMessageBusBmFeedHandler::StorageApiMessageBusBmFeedHandler(BmMessageBus &message_bus, const IBmDistribution& distribution, bool distributor)
    : StorageApiBmFeedHandlerBase("StorageApiMessageBusBmFeedHandler", distribution, distributor),
      _message_bus(message_bus),
      _routes(distribution.get_num_nodes(), distributor),
      _no_route_error_count(0)
{
}

StorageApiMessageBusBmFeedHandler::~StorageApiMessageBusBmFeedHandler() = default;

void
StorageApiMessageBusBmFeedHandler::send_cmd(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& tracker)
{
    uint32_t node_idx = route_cmd(*cmd);
    if (_routes.has_route(node_idx)) {
        auto msg = std::make_unique<storage::mbusprot::StorageCommand>(cmd);
        auto& route = _routes.get_route(node_idx);
        _message_bus.send_msg(std::move(msg), route, tracker);
    } else {
        ++_no_route_error_count;
    }
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

}
