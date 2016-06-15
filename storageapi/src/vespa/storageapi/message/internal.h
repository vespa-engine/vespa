// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

namespace storage {
namespace api {

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
    InternalCommand(uint32_t type)
        : StorageCommand(MessageType::INTERNAL), _type(type) {}

    uint32_t getType() const { return _type; }

    virtual bool callHandler(MessageHandler& h,
                             const std::shared_ptr<StorageMessage> & m) const
    {
        return h.onInternal(std::static_pointer_cast<InternalCommand>(m));
    }

    /**
     * Enforcing that subclasses implement print such that we can see what kind
     * of message it is when debugging.
     */
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const = 0;
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
    InternalReply(uint32_t type, const InternalCommand& cmd)
        : StorageReply(cmd),
          _type(type)
    {
    }

    uint32_t getType() const { return _type; }

    virtual bool callHandler(MessageHandler& h,
                             const std::shared_ptr<StorageMessage> & m) const
    {
        return h.onInternalReply(std::static_pointer_cast<InternalReply>(m));
    }

    /**
     * Enforcing that subclasses implement print such that we can see what kind
     * of message it is when debugging.
     */
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const = 0;
};

inline void
InternalCommand::print(std::ostream& out, bool verbose,
                       const std::string& indent) const
{
    out << "InternalCommand(" << _type << ")";
    if (verbose) {
        out << " : ";
        StorageCommand::print(out, verbose, indent);
    }
}

inline void
InternalReply::print(std::ostream& out, bool verbose,
                     const std::string& indent) const
{
    out << "InternalReply(" << _type << ")";
    if (verbose) {
        out << " : ";
        StorageReply::print(out, verbose, indent);
    }
}

} // api
} // storage

