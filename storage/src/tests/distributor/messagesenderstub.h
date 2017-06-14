// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storage/distributor/distributormessagesender.h>
#include <cassert>

namespace storage {

struct MessageSenderStub : distributor::DistributorMessageSender
{
    std::vector<std::shared_ptr<api::StorageCommand> > commands;
    std::vector<std::shared_ptr<api::StorageReply> > replies;

    MessageSenderStub();
    ~MessageSenderStub();

    void clear() {
        commands.clear();
        replies.clear();
    }

    void sendCommand(const std::shared_ptr<api::StorageCommand>& cmd) override {
        commands.push_back(cmd);
    }

    void sendReply(const std::shared_ptr<api::StorageReply>& reply) override {
        replies.push_back(reply);
    }

    std::string getLastCommand(bool verbose = true) const;

    std::string getCommands(bool includeAddress = false,
                            bool verbose = false,
                            uint32_t fromIndex = 0) const;

    std::string getLastReply(bool verbose = true) const;

    std::string getReplies(bool includeAddress = false,
                           bool verbose = false) const;

    std::string dumpMessage(const api::StorageMessage& msg,
                            bool includeAddress,
                            bool verbose) const;

    int getDistributorIndex() const override {
        return 0;
    }

    const std::string& getClusterName() const override {
        return _clusterName;
    }

    const distributor::PendingMessageTracker& getPendingMessageTracker() const override {
        assert(_pendingMessageTracker);
        return *_pendingMessageTracker;
    }

    void setPendingMessageTracker(distributor::PendingMessageTracker& tracker) {
        _pendingMessageTracker = &tracker;
    }
private:
    std::string _clusterName;
    distributor::PendingMessageTracker* _pendingMessageTracker;
};

}
