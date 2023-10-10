// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.routing;

/**
 * When a {@link com.yahoo.messagebus.Reply} containing errors is returned to a {@link com.yahoo.messagebus.MessageBus},
 * an object implementing this interface is consulted on whether or not to resend the corresponding {@link
 * com.yahoo.messagebus.Message}. The policy is passed to the message bus at creation time using the {@link
 * com.yahoo.messagebus.MessageBusParams#setRetryPolicy(RetryPolicy)} method.
 *
 * @author Simon Thoresen Hult
 */
public interface RetryPolicy {

    /**
     * Returns whether or not a {@link com.yahoo.messagebus.Reply} containing an {@link com.yahoo.messagebus.Error} with
     * the given error code can be retried. This method is invoked once for each error in a reply.
     *
     * @param errorCode The code to check.
     * @return True if the message can be resent.
     */
    public boolean canRetry(int errorCode);

    /**
     * Returns the number of seconds to delay resending a message.
     *
     * @param retry The retry attempt.
     * @return The delay in seconds.
     */
    public double getRetryDelay(int retry);
}
