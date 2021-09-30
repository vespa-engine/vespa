// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

/**
 * Superclass for document <i>visiting</i> functionality - accessing
 * documents in an order decided by the document repository. This allows much
 * higher read throughput than random access.
 * <p>
 * The class supplies an interface for functions that are common for different
 * kinds of visitor sessions, such as acking visitor data and aborting the
 * session.
 *
 * @author Håkon Humberset
 */
public interface VisitorControlSession {

    /**
     * Acknowledges a response previously retrieved by the <code>getNext</code>
     * method.
     *
     * @param token The ack token. You must get this from the visitor response
     *              returned by the <code>getNext</code> method.
     */
    void ack(AckToken token);

    /**
     * Aborts the session.
     */
    void abort();

    /**
     * Returns the next response of this session. This method returns immediately.
     *
     * @return the next response, or null if no response is ready at this time
     */
    VisitorResponse getNext();

    /**
     * Returns the next response of this session. This will block until a response is ready
     * or until the given timeout is reached
     *
     * @param timeoutMilliseconds the max time to wait for a response. If the number is 0, this will block
     *                            without any timeout limit
     * @return the next response, or null if no response becomes ready before the timeout expires
     * @throws InterruptedException if this thread is interrupted while waiting
     */
    VisitorResponse getNext(int timeoutMilliseconds) throws InterruptedException;

    /**
     * Destroys this session and frees up any resources it has held.
     */
    void destroy();

}
