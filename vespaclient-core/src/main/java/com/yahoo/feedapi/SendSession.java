// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Result;

/**
 * Wrapper class to send Messages. Used instead of using a MessageBus session directly
 * so that unit tests can be more easily made.
 */
public abstract class SendSession {

    protected abstract Result onSend(Message m, boolean blockIfQueueIsFull) throws InterruptedException;

    public Result send(Message m, boolean blockIfQueueIsFull) throws InterruptedException {
        return onSend(m, blockIfQueueIsFull);
    }

    public abstract void close();

}
