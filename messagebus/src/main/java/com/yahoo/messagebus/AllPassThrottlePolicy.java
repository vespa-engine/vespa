// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

/**
 * This is an implementation of the {@link ThrottlePolicy} that passes all requests (no real throttling).
 *
 * @author dybis
 */
public class AllPassThrottlePolicy implements ThrottlePolicy {

    @Override
    public boolean canSend(Message msg, int pendingCount) {
        return true;
    }

    @Override
    public void processMessage(Message msg) {
    }

    @Override
    public void processReply(Reply reply) {
    }

}
