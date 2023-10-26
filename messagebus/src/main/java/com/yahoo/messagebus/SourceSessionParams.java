// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

/**
 * To facilitate several configuration parameters to the {@link MessageBus#createSourceSession(ReplyHandler,
 * SourceSessionParams)}, all parameters are held by this class. This class has reasonable default values for each
 * parameter.
 *
 * @author Simon Thoresen Hult
 */
public class SourceSessionParams {

    private ReplyHandler replyHandler = null;
    private ThrottlePolicy throttlePolicy = new DynamicThrottlePolicy();
    private double timeout = 180.0;

    /**
     * Instantiates a parameter object with default values.
     */
    public SourceSessionParams() {
        // empty
    }

    /**
     * Implements the copy constructor.
     *
     * @param params the object to copy
     */
    public SourceSessionParams(SourceSessionParams params) {
        throttlePolicy = params.throttlePolicy;
        timeout = params.timeout;
        replyHandler = params.replyHandler;
    }

    /** Returns the policy to use for throttling output. */
    public ThrottlePolicy getThrottlePolicy() {
        return throttlePolicy;
    }

    /**
     * Sets the policy to use for throttling output.
     *
     * @param throttlePolicy The policy to set.
     * @return this, to allow chaining
     */
    public SourceSessionParams setThrottlePolicy(ThrottlePolicy throttlePolicy) {
        this.throttlePolicy = throttlePolicy;
        return this;
    }

    /**
     * Returns the number of seconds a message can spend trying to succeed.
     *
     * @return the timeout in seconds
     */
    public double getTimeout() {
        return timeout;
    }

    /**
     * Sets the number of seconds a message can be attempted sent until it times out. This is the maximum allowed time
     * for any message bus operation.
     *
     * @param timeout The numer of seconds allowed.
     * @return this, to allow chaining
     */
    public SourceSessionParams setTimeout(double timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Returns whether or not a reply handler has been assigned to this.
     *
     * @return true if a handler is set
     */
    boolean hasReplyHandler() {
        return replyHandler != null;
    }

    /**
     * Returns the handler to receive incoming replies.
     *
     * @return the handler
     */
    public ReplyHandler getReplyHandler() {
        return replyHandler;
    }

    /**
     * Sets the handler to recive incoming replies.
     *
     * @param handler The handler to set.
     * @return this, to allow chaining
     */
    public SourceSessionParams setReplyHandler(ReplyHandler handler) {
        replyHandler = handler;
        return this;
    }

}
