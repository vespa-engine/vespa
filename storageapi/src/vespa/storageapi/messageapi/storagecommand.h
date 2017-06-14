// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::api::StorageCommand
 * @ingroup messageapi
 *
 * @brief Superclass for all storage commands.
 *
 * A storage command is a storage message you will get a storage reply for.
 *
 * @version $Id$
 */

#pragma once

#include "storagemessage.h"

namespace storage {
namespace api {

class StorageReply;

class StorageCommand : public StorageMessage {
    uint32_t _timeout; /** Timeout of command in milliseconds */
        /** Sets what node this message origins from. 0xFFFF is unset. */
    uint16_t _sourceIndex;

protected:
    explicit StorageCommand(const StorageCommand& other);
    explicit StorageCommand(const MessageType& type, Priority p = NORMAL);

public:
    DECLARE_POINTER_TYPEDEFS(StorageCommand);

    virtual ~StorageCommand();

    bool sourceIndexSet() const { return (_sourceIndex != 0xffff); }
    void setSourceIndex(uint16_t sourceIndex) { _sourceIndex = sourceIndex; }
    uint16_t getSourceIndex() const { return _sourceIndex; }

    /** Set timeout in milliseconds. */
    void setTimeout(uint32_t milliseconds) { _timeout = milliseconds; }
    /** Get timeout in milliseconds. */
    uint32_t getTimeout() const { return _timeout; }

    /** Used to set a new id so the message can be resent. */
    void setNewId() { StorageMessage::setNewMsgId(); }

    /** Overload this to get more descriptive message output. */
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    /**
     * A way for someone to make a reply to a storage message without
     * knowing the type of the message. Should just call reply constructor
     * taking command as input.
     */
    virtual std::unique_ptr<StorageReply> makeReply() = 0;

    /**
     * Distributors need a way to create copies of messages to send to forward
     * to different nodes. Only messages sent through the distributor needs to
     * have an actual implementation of this.
     */
    virtual StorageCommand::UP createCopyToForward(
            const document::BucketId& bucket, uint64_t timestamp) const;

};

} // api
} // storage

