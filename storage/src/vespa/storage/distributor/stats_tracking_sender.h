// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "content_node_message_stats_tracker.h"
#include "distributormessagesender.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <mutex>

namespace storage::distributor {

/**
 * Message sender which counts all sent outgoing commands (_not_ outgoing replies).
 *
 * Thread safe for statistics updates and reads.
 */
class StatsTrackingSender final : public DistributorMessageSender {
    DistributorMessageSender&      _fwd_sender;
    ContentNodeMessageStatsTracker _stats_tracker;
    mutable std::mutex             _stats_mutex; // read by host info reporter thread
public:
    explicit StatsTrackingSender(DistributorMessageSender& fwd_sender);
    ~StatsTrackingSender() override;

    [[nodiscard]] ContentNodeMessageStatsTracker::NodeStats node_stats() const;
    // TODO find a less leaky abstraction...
    void observe_incoming_response_result(uint16_t from_node, api::MessageType::Id msg_type_id, api::ReturnCode::Result result);

    // Tracks sends
    void sendCommand(const std::shared_ptr<api::StorageCommand>& cmd) override;
    void sendReply(const std::shared_ptr<api::StorageReply>& reply) override {
        _fwd_sender.sendReply(reply);
    }
    void sendReplyDirectly(const std::shared_ptr<api::StorageReply>& reply) override {
        _fwd_sender.sendReplyDirectly(reply);
    }
    // Tracks sends
    uint64_t sendToNode(const lib::NodeType& nodeType, uint16_t node,
                        const std::shared_ptr<api::StorageCommand>& cmd,
                        bool useDocumentAPI) override;
    int getDistributorIndex() const override {
        return _fwd_sender.getDistributorIndex();
    }
    const ClusterContext& cluster_context() const override {
        return _fwd_sender.cluster_context();
    }
};

}
