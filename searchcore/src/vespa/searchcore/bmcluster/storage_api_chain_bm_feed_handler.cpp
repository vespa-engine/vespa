// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storage_api_chain_bm_feed_handler.h"
#include "i_bm_distribution.h"
#include "pending_tracker.h"
#include "bm_storage_link_context.h"
#include "bm_storage_link.h"
#include <vespa/storageapi/messageapi/storagecommand.h>

namespace search::bmcluster {

StorageApiChainBmFeedHandler::StorageApiChainBmFeedHandler(std::vector<std::shared_ptr<BmStorageLinkContext>> contexts, const IBmDistribution& distribution, bool distributor)
    : StorageApiBmFeedHandlerBase("StorageApiChainBmFeedHandler", distribution, distributor),
      _contexts(std::move(contexts)),
      _no_link_error_count(0u)
{
}

StorageApiChainBmFeedHandler::~StorageApiChainBmFeedHandler() = default;

void
StorageApiChainBmFeedHandler::send_cmd(std::shared_ptr<storage::api::StorageCommand> cmd, PendingTracker& tracker)
{
    uint32_t node_idx = route_cmd(*cmd);
    if (node_idx < _contexts.size() && _contexts[node_idx]) {
        auto bm_link = _contexts[node_idx]->bm_link;
        bm_link->retain(cmd->getMsgId(), tracker);
        bm_link->sendDown(std::move(cmd));
    } else {
        ++_no_link_error_count;
    }
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

}
