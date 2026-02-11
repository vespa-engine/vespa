// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.documentapi.messagebus.protocol.*;
import com.yahoo.messagebus.Message;
import com.yahoo.vdslib.SearchResult;
import com.yahoo.vdslib.DocumentSummary;
import com.yahoo.document.BucketId;
import java.util.List;

/**
 * A data handler is a class that handles responses from a visitor.
 * Different clients might want different interfaces.
 * Some might want a callback interface, some might want a polling interface.
 * Some want good control of acking, while others just want something simple.
 * <p>
 * Use a data handler that fits your needs to be able to use visiting easily.
 *
 * @author Håkon Humberset
 */
public abstract class VisitorDataHandler { 
    
    protected VisitorControlSession session;

    /** Creates a new visitor data handler. */
    public VisitorDataHandler() {
    }

    /**
     * Called before the visitor starts. Override this method if you need
     * to reset local data. Remember to call the superclass' method as well.
     */
    public void reset() {
        session = null;
    }

    /**
     * Sets which session this visitor data handler belongs to. This is done by
     * the session itself and should not be called manually. The session is
     * needed for ack to work.
     *
     * @param session the session currently using this data handler
     */
    public void setSession(VisitorControlSession session) {
        this.session = session;
    }

    /**
     * Returns the next response of this session. This method returns
     * immediately.
     *
     * @return the next response, or null if no response is ready at this time
     * @throws UnsupportedOperationException if data handler does not support
     *                                       the operation
     */
    public VisitorResponse getNext() {
        throw new UnsupportedOperationException("This datahandler doesn't support polling");
    }

    /**
     * Returns the next response of this session. This will block until a
     * response is ready or the given timeout is reached.
     *
     * @param timeoutMilliseconds the max time to wait for a response. If the
     *                            number is 0, this will block without any
     *                            timeout limit
     * @return the next response, or null if no response becomes ready before
     *         the timeout expires
     * @throws InterruptedException if this thread is interrupted while waiting
     * @throws UnsupportedOperationException if data handler does not support
     *                                       the operation
     */
    public VisitorResponse getNext(int timeoutMilliseconds) throws InterruptedException {
        throw new UnsupportedOperationException("This datahandler doesn't support polling");
    }

    /**
     * Called when visiting is done, to notify clients waiting on getNext().
     */
    public void onDone() {}

    /**
     * Called when a data message is received.
     *
     * IMPORTANT:
     * May be called concurrently from multiple threads. Any internal state
     * mutations MUST be done in a thread-safe manner.
     *
     * @param m The message received
     * @param token A token to reply with when finished processing the message.
     */
    public abstract void onMessage(Message m, AckToken token);

    /**
     * Function used to ack data. You need to ack data periodically, as storage
     * will halt visiting when it has too much client requests pending.
     *
     * @param token The token to ack. Gotten from an earlier callback.
     */
    public void ack(AckToken token) {
        session.ack(token);
    }

}
