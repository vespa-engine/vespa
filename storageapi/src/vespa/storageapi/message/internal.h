// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @file internal.h
 *
 * Internal commands, used in storage. These are commands that don't need to
 * be serialized as they never leave a node, implemented within storage itself
 * to be able to include storage types not defined in the API.
 *
 * Historically these messages also existed so we could alter internal messages
 * without recompiling clients, but currently no clients use storage API for
 * communication anymore so this is no longer an issue.
 */
#pragma once

#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/storagereply.h>

namespace storage::api {

/**
 * @class InternalCommand
 * @ingroup message
 *
 * @brief Base class for commands local to a VDS node.
 *
 * This is the base class for internal server commands. They can not be
 * serialized, so any attempt of sending such a command away from a storage
 * node will fail.
 */
class InternalCommand : public StorageCommand {
    uint32_t _type;

public:
    InternalCommand(uint32_t type);
    ~InternalCommand() override;

    uint32_t getType() const { return _type; }

    bool callHandler(MessageHandler& h, const std::shared_ptr<StorageMessage> & m) const override {
        return h.onInternal(std::static_pointer_cast<InternalCommand>(m));
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

/**
 * @class InternalReply
 * @ingroup message
 *
 * @brief Response of an internal command.
 */
class InternalReply : public StorageReply {
    uint32_t _type;

public:
    InternalReply(uint32_t type, const InternalCommand& cmd);
    ~InternalReply() override;

    uint32_t getType() const { return _type; }

    bool callHandler(MessageHandler& h, const std::shared_ptr<StorageMessage> & m) const override {
        return h.onInternalReply(std::static_pointer_cast<InternalReply>(m));
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

}

