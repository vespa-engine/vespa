// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/distributormessagesender.h>
#include <vespa/storage/distributor/sentmessagemap.h>
#include <memory>

namespace storage::api { class StorageReply; }

namespace storage::distributor {

struct IntermediateMessageSender final : DistributorStripeMessageSender {
    SentMessageMap&                    msgMap;
    std::shared_ptr<Operation>         callback;
    DistributorStripeMessageSender&    forward;
    std::shared_ptr<api::StorageReply> _reply;

    IntermediateMessageSender(SentMessageMap& mm,
                              std::shared_ptr<Operation> cb,
                              DistributorStripeMessageSender& fwd) noexcept;
    ~IntermediateMessageSender() override;

    void sendCommand(const std::shared_ptr<api::StorageCommand>& cmd) override;
    void sendReply(const std::shared_ptr<api::StorageReply>& reply) override;
    int getDistributorIndex() const override;
    const ClusterContext& cluster_context() const override;
    PendingMessageTracker& getPendingMessageTracker() override;
    const PendingMessageTracker& getPendingMessageTracker() const override;
    const OperationSequencer& operation_sequencer() const noexcept override;
    OperationSequencer& operation_sequencer() noexcept override;
};

}
