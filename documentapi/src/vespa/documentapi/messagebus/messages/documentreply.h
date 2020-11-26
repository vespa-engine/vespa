// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/reply.h>
#include <vespa/documentapi/messagebus/priority.h>

namespace documentapi {

/**
 * This class implements a generic document protocol reply that can be reused by document messages that require no
 * special reply implementation while still allowing applications to distinguish between types.
 */
class DocumentReply : public mbus::Reply {
private:
    uint32_t _type;
    Priority::Value _priority;

public:
    /**
     * Convenience typedef.
     */
    typedef std::unique_ptr<DocumentReply> UP;
    typedef std::shared_ptr<DocumentReply> SP;

    /**
     * Constructs a new reply of given type.
     *
     * @param type The type code to assign to this.
     */
    DocumentReply(uint32_t type);

    /**
     * Virtual destructor required for inheritance.
     */
    ~DocumentReply() override;

    /**
     * Returns the priority tag for this message. This is an optional tag added for VDS that is not interpreted by the
     * document protocol.
     *
     * @return The priority.
     */
    Priority::Value getPriority() const { return _priority; }
    uint8_t priority() const override { return (uint8_t)_priority; }

    /**
     * Sets the priority tag for this message.
     *
     * @param priority The priority to set.
     */
    void setPriority(Priority::Value p) { _priority = p; }
    const mbus::string& getProtocol() const override;
    uint32_t getType() const override { return _type; }
};

}
