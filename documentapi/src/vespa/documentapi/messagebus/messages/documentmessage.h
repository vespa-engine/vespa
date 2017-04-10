// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentreply.h"
#include <vespa/messagebus/message.h>
#include <vespa/messagebus/reply.h>
#include <vespa/documentapi/loadtypes/loadtype.h>
#include <vespa/documentapi/messagebus/priority.h>

namespace documentapi {

class DocumentMessage : public mbus::Message {
private:
    Priority::Value _priority;
    LoadType _loadType;
    uint32_t _approxSize; // Not sent on wire; set by deserializer or by caller.

protected:
    /**
     * This method is used by {@link #createReply()} to ensure that all document messages return document type
     * replies. This method may NOT return null as that will cause an assertion error.
     *
     * @return A document reply that corresponds to this message.
     */
    virtual DocumentReply::UP doCreateReply() const = 0;

public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<DocumentMessage> UP;
    typedef std::shared_ptr<DocumentMessage> SP;

    /**
     * Constructs a new document message with no content.
     */
    DocumentMessage();

    /**
     * Virtual destructor required for inheritance.
     */
    virtual ~DocumentMessage() { }

    /**
     * Creates and returns a reply to this message. This method uses the internal {@link #doCreateReply()} to
     * guarantee that the reply is a {@link DocumentReply}, and casts it to a message bus type reply for
     * convenience.
     *
     * @return The created reply.
     */
    mbus::Reply::UP createReply() const;

    /**
     * Returns the priority of this message.
     *
     * @return The priority.
     */
    Priority::Value getPriority() const { return _priority; };

    uint8_t priority() const override { return (uint8_t)_priority; }

    /**
     * Sets the priority tag for this message.
     *
     * @param priority The priority to set.
     */
    void setPriority(Priority::Value p) { _priority = p; };

    /**
     * @return Returns the load type for this message.
     */
    const LoadType& getLoadType() const { return _loadType; }

    /**
     * Sets the load type for this message.
     */
    void setLoadType(const LoadType& loadType) { _loadType = loadType; }

    uint32_t getApproxSize() const override;

    void setApproxSize(uint32_t approxSize) {
        _approxSize = approxSize;
    }

    // Implements mbus::Message.
    const mbus::string& getProtocol() const override;
};

}

