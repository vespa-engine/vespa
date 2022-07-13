// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/distributor/distributormessagesender.h>
#include <tests/common/message_sender_stub.h>
#include <cassert>
#include <string>
#include "dummy_cluster_context.h"

namespace storage {

class DistributorMessageSenderStub : public distributor::DistributorStripeMessageSender {
    MessageSenderStub _stub_impl;
    distributor::PendingMessageTracker* _pending_message_tracker;
    distributor::OperationSequencer* _operation_sequencer;
public:

    DistributorMessageSenderStub();
    ~DistributorMessageSenderStub() override;

    std::vector<std::shared_ptr<api::StorageCommand>>& commands() noexcept {
        return _stub_impl.commands;
    }
    std::vector<std::shared_ptr<api::StorageReply>>& replies() noexcept {
        return _stub_impl.replies;
    }
    const std::vector<std::shared_ptr<api::StorageCommand>>& commands() const noexcept {
        return _stub_impl.commands;
    }
    const std::vector<std::shared_ptr<api::StorageReply>>& replies() const noexcept {
        return _stub_impl.replies;
    };

    const std::shared_ptr<api::StorageCommand>& command(size_t idx) noexcept {
        assert(idx < commands().size());
        return commands()[idx];
    }

    const std::shared_ptr<api::StorageReply>& reply(size_t idx) noexcept {
        assert(idx < replies().size());
        return replies()[idx];
    }

    void clear() {
        _stub_impl.clear();
    }

    void sendCommand(const std::shared_ptr<api::StorageCommand>& cmd) override {
        _stub_impl.sendCommand(cmd);
    }

    void sendReply(const std::shared_ptr<api::StorageReply>& reply) override {
        _stub_impl.sendReply(reply);
    }

    std::string getLastCommand(bool verbose = true) const {
        return _stub_impl.getLastCommand(verbose);
    }

    std::string getCommands(bool includeAddress = false,
                            bool verbose = false,
                            uint32_t fromIndex = 0) const {
        return _stub_impl.getCommands(includeAddress, verbose, fromIndex);
    }

    std::string getLastReply(bool verbose = true) const {
        return _stub_impl.getLastReply(verbose);
    }

    std::string getReplies(bool includeAddress = false,
                           bool verbose = false) const {
        return _stub_impl.getReplies(includeAddress, verbose);
    }

    std::string dumpMessage(const api::StorageMessage& msg,
                            bool includeAddress,
                            bool verbose) const {
        return _stub_impl.dumpMessage(msg, includeAddress, verbose);
    }

    int getDistributorIndex() const override {
        return 0;
    }

    const ClusterContext& cluster_context() const override {
        return dummy_cluster_context;
    }

    distributor::PendingMessageTracker& getPendingMessageTracker() override {
        assert(_pending_message_tracker);
        return *_pending_message_tracker;
    }

    const distributor::PendingMessageTracker& getPendingMessageTracker() const override {
        assert(_pending_message_tracker);
        return *_pending_message_tracker;
    }

    void setPendingMessageTracker(distributor::PendingMessageTracker& tracker) {
        _pending_message_tracker = &tracker;
    }

    const distributor::OperationSequencer& operation_sequencer() const noexcept override {
        assert(_operation_sequencer);
        return *_operation_sequencer;
    }

    distributor::OperationSequencer& operation_sequencer() noexcept override {
        assert(_operation_sequencer);
        return *_operation_sequencer;
    }

    void set_operation_sequencer(distributor::OperationSequencer& op_seq) {
        _operation_sequencer = &op_seq;
    }
};

}
