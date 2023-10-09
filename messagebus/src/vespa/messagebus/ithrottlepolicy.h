// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "reply.h"

namespace mbus {

/**
 * An implementation of this interface is used by {@link SourceSession} to throttle output. Every message
 * entering {@link SourceSession#send(Message)} needs to be accepted by this interface's {@link
 * #canSend(Message, int)} method.  All messages accepted are passed through the {@link
 * #processMessage(Message)} method, and the corresponding replies are passed through the {@link
 * #processReply(Reply)} method.
 */
class IThrottlePolicy {
public:
    /**
     * Convenience typedefs.
     */
    using UP = std::unique_ptr<IThrottlePolicy>;
    using SP = std::shared_ptr<IThrottlePolicy>;

    /**
     * Virtual destructor required for inheritance.
     */
    virtual ~IThrottlePolicy() { /* empty */ }

    /**
     * Returns whether or not the given message can be sent according to the current state of this policy.
     *
     * @param msg          The message to evaluate.
     * @param pendingCount The current number of pending messages.
     * @return True to send the message.
     */
    virtual bool canSend(const Message &msg, uint32_t pendingCount) = 0;

    /**
     * This method is called once for every message that was accepted by {@link #canSend(Message, int)} and sent.
     *
     * @param msg The message beint sent.
     */
    virtual void processMessage(Message &msg) = 0;

    /**
     * This method is called once for every reply that is received.
     *
     * @param reply The reply received.
     */
    virtual void processReply(Reply &reply) = 0;
};

} // namespace mbus

