// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "ireplyhandler.h"
#include "ithrottlepolicy.h"
#include <chrono>

namespace mbus {

/**
 * To facilitate several configuration parameters to the {@link MessageBus#createSourceSession(ReplyHandler,
 * SourceSessionParams)}, all parameters are held by this class. This class has reasonable default values for each
 * parameter.
 *
 * @author Simon Thoresen Hult
 */
class SourceSessionParams {
private:
    IReplyHandler      *_replyHandler;
    IThrottlePolicy::SP _throttlePolicy;
    seconds             _timeout;

public:
    /**
     * This constructor will set default values for all parameters.
     */
    SourceSessionParams();

    /**
     * Returns the policy to use for throttling output.
     *
     * @return The policy.
     */
    IThrottlePolicy::SP getThrottlePolicy() const;

    /**
     * Sets the policy to use for throttling output.
     *
     * @param throttlePolicy The policy to set.
     * @return This, to allow chaining.
     */
    SourceSessionParams &setThrottlePolicy(IThrottlePolicy::SP throttlePolicy);

    /**
     * Returns the total timeout parameter.
     *
     * @return The total timeout parameter.
     */
    seconds getTimeout() const;

    /**
     * Returns the number of seconds a message can spend trying to succeed.
     *
     * @return The timeout in seconds.
     */
    SourceSessionParams &setTimeout(seconds timeout);

    /**
     * Returns whether or not a reply handler has been assigned to this.
     *
     * @return True if a handler is set.
     */
    bool hasReplyHandler() const;

    /**
     * Returns the handler to receive incoming replies. If you call this method without first assigning a
     * reply handler to this object, you wil de-ref null.
     *
     * @return The handler.
     */
    IReplyHandler &getReplyHandler() const;

    /**
     * Sets the handler to receive incoming replies.
     *
     * @param handler The handler to set.
     * @return This, to allow chaining.
     */
    SourceSessionParams &setReplyHandler(IReplyHandler &handler);
};

} // namespace mbus

