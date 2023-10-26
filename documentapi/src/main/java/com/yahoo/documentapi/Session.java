// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

/**
 * Superclass of all document api sessions. A session provides a handle through
 * which an application can work with a document repository. There are various
 * session subclasses for various types of interaction with the repository.
 * <p>
 * Each session can be used by multiple client application threads, i.e they are
 * multithread safe.
 *
 * @author bratseth
 */
public interface Session {

    /**
     * Returns the next response of this session. This method returns immediately.
     *
     * @return the next response, or null if no response is ready at this time
     */
    Response getNext();

    /**
     * Returns the next response of this session. This will block until a response is ready
     * or until the given timeout is reached
     *
     * @param timeoutMilliseconds the max time to wait for a response.
     * @return the next response, or null if no response becomes ready before the timeout expires
     * @throws InterruptedException if this thread is interrupted while waiting
     */
    Response getNext(int timeoutMilliseconds) throws InterruptedException;

    /**
     * Destroys this session and frees up any resources it has held. Making further calls on a destroyed
     * session causes a runtime exception.
     */
    void destroy();

}
