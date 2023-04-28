// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Superclass for all storage commands.
 *
 * A storage command is a storage message you will get a storage reply for.
 */

#pragma once

#include "storagemessage.h"

namespace storage::api {

class StorageReply;

class StorageCommand : public StorageMessage {
    duration _timeout; /** Timeout of command in milliseconds */
        /** Sets what node this message origins from. 0xFFFF is unset. */
    uint16_t _sourceIndex;

protected:
    StorageCommand(const StorageCommand& other);
    explicit StorageCommand(const MessageType& type, Priority p = NORMAL);

public:
    DECLARE_POINTER_TYPEDEFS(StorageCommand);

    ~StorageCommand() override;

    bool sourceIndexSet() const { return (_sourceIndex != 0xffff); }
    void setSourceIndex(uint16_t sourceIndex) { _sourceIndex = sourceIndex; }
    uint16_t getSourceIndex() const { return _sourceIndex; }

    void setTimeout(duration timeout) { _timeout = timeout; }
    duration getTimeout() const { return _timeout; }

    /** Overload this to get more descriptive message output. */
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    /**
     * A way for someone to make a reply to a storage message without
     * knowing the type of the message. Should just call reply constructor
     * taking command as input.
     */
    virtual std::unique_ptr<StorageReply> makeReply() = 0;
};

}
