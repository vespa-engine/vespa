// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "intermediate_message_sender.h"
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/storagereply.h>

namespace storage::distributor {

IntermediateMessageSender::IntermediateMessageSender(SentMessageMap& mm,
                                                     std::shared_ptr<Operation> cb,
                                                     DistributorStripeMessageSender& fwd) noexcept
    : msgMap(mm),
      callback(std::move(cb)),
      forward(fwd)
{
}

IntermediateMessageSender::~IntermediateMessageSender() = default;

void IntermediateMessageSender::sendCommand(const std::shared_ptr<api::StorageCommand>& cmd) {
    msgMap.insert(cmd->getMsgId(), callback);
    forward.sendCommand(cmd);
};

void IntermediateMessageSender::sendReply(const std::shared_ptr<api::StorageReply>& reply) {
    _reply = reply;
}

int IntermediateMessageSender::getDistributorIndex() const {
    return forward.getDistributorIndex();
}

const ClusterContext& IntermediateMessageSender::cluster_context() const {
    return forward.cluster_context();
}

PendingMessageTracker& IntermediateMessageSender::getPendingMessageTracker() {
    return forward.getPendingMessageTracker();
}

const PendingMessageTracker& IntermediateMessageSender::getPendingMessageTracker() const {
    return forward.getPendingMessageTracker();
}

const OperationSequencer& IntermediateMessageSender::operation_sequencer() const noexcept {
    return forward.operation_sequencer();
}

OperationSequencer& IntermediateMessageSender::operation_sequencer() noexcept {
    return forward.operation_sequencer();
}

}
