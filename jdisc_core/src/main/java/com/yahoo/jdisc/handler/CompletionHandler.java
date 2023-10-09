// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Container;

/**
 * This interface defines a handler for consuming the result of an asynchronous I/O operation.
 * <p>
 * The asynchronous channels defined in this package allow a completion handler to be specified to consume the result of
 * an asynchronous operation. The {@link #completed()} method is invoked when the I/O operation completes successfully.
 * The {@link #failed(Throwable)} method is invoked if the I/O operations fails. The implementations of these methods
 * should complete in a timely manner so as to avoid keeping the invoking thread from dispatching to other completion
 * handlers.
 * <p>
 * Because a CompletionHandler might have a completely different lifespan than the originating ContentChannel objects,
 * all instances of this interface are internally backed by a reference to the {@link Container} that was active when
 * the initial Request was created. This ensures that the configured environment of the CompletionHandler is stable
 * throughout its lifetime. This also means that the either {@link #completed()} or {@link #failed(Throwable)} MUST be
 * called in order to release that reference. Failure to do so will prevent the Container from ever shutting down.
 *
 * @author Simon Thoresen Hult
 */
public interface CompletionHandler {

    /**
     * Invoked when an operation has completed. Notice that you MUST call either this or {@link #failed(Throwable)} to
     * release the internal {@link Container} reference. Failure to do so will prevent the Container from ever shutting
     * down.
     */
    void completed();

    /**
     * Invoked when an operation fails. Notice that you MUST call either this or {@link #completed()} to release the
     * internal {@link Container} reference. Failure to do so will prevent the Container from ever shutting down.
     *
     * @param t The exception to indicate why the I/O operation failed.
     */
    void failed(Throwable t);

}
