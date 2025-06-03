// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "stats_tracking_sender.h"
#include <vespa/storageapi/messageapi/storagecommand.h>

namespace storage::distributor {

StatsTrackingSender::StatsTrackingSender(DistributorMessageSender& fwd_sender)
    : _fwd_sender(fwd_sender),
      _stats_tracker(),
      _stats_mutex()
{
}

StatsTrackingSender::~StatsTrackingSender() = default;

void StatsTrackingSender::sendCommand(const std::shared_ptr<api::StorageCommand>& cmd) {
    if (cmd->getAddress()) [[likely]] {
        std::lock_guard lock(_stats_mutex);
        _stats_tracker.stats_for(cmd->getAddress()->getIndex()).observe_outgoing_request();
    }
    _fwd_sender.sendCommand(cmd);
}

uint64_t
StatsTrackingSender::sendToNode(const lib::NodeType& nodeType, uint16_t node,
                                const std::shared_ptr<api::StorageCommand>& cmd,
                                bool useDocumentAPI)
{
    {
        std::lock_guard lock(_stats_mutex);
        _stats_tracker.stats_for(node).observe_outgoing_request();
    }
    return _fwd_sender.sendToNode(nodeType, node, cmd, useDocumentAPI);
}

ContentNodeMessageStatsTracker::NodeStats
StatsTrackingSender::node_stats() const {
    std::lock_guard lock(_stats_mutex);
    return _stats_tracker.node_stats();
}

void
StatsTrackingSender::observe_incoming_response_result(uint16_t from_node,
                                                      api::MessageType::Id msg_type_id,
                                                      api::ReturnCode::Result result)
{
    std::lock_guard lock(_stats_mutex);
    _stats_tracker.stats_for(from_node).observe_incoming_response_result(msg_type_id, result);
}

}
