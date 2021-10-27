// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/common/messagesender.h>
#include <vector>

namespace storage {

struct MessageSenderStub : MessageSender {
    std::vector<std::shared_ptr<api::StorageCommand>> commands;
    std::vector<std::shared_ptr<api::StorageReply>> replies;

    MessageSenderStub();
    ~MessageSenderStub() override;

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

    static std::string dumpMessage(const api::StorageMessage& msg,
                                   bool includeAddress,
                                   bool verbose);
};


}
