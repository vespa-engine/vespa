// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/message.h>
#include <vespa/storageapi/mbusprot/storagemessage.h>
#include <vespa/storageapi/mbusprot/storageprotocol.h>
#include <vespa/storageapi/messageapi/storagecommand.h>

namespace storage {
namespace mbusprot {

class StorageCommand : public mbus::Message, public StorageMessage {
public:
    typedef std::unique_ptr<StorageCommand> UP;

    StorageCommand(const storage::api::StorageCommand::SP&);

    const mbus::string & getProtocol() const override { return StorageProtocol::NAME; }

    uint32_t getType() const override { return _cmd->getType().getId(); }

    const api::StorageCommand::SP& getCommand() { return _cmd; }
    api::StorageCommand::CSP getCommand() const { return _cmd; }
    virtual api::StorageMessage::SP getInternalMessage() override { return _cmd; }
    virtual api::StorageMessage::CSP getInternalMessage() const override { return _cmd; }

    virtual uint8_t priority() const override {
        return ((getInternalMessage()->getPriority()) / 255) * 16;
    }

private:
    api::StorageCommand::SP _cmd;
};

} // mbusprot
} // storage

