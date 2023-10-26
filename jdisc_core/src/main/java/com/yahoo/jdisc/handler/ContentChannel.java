// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Container;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;

import java.nio.ByteBuffer;

/**
 * This interface defines a callback for asynchronously writing the content of a {@link Request} or a {@link Response}
 * to a recipient. It is the returned both by {@link RequestHandler#handleRequest(Request, ResponseHandler)} and {@link
 * ResponseHandler#handleResponse(Response)}. Note that methods of this channel only <em>schedule</em> the appropriate
 * action - if you need to act on the result you will need submit a {@link CompletionHandler} to the appropriate method.
 * <p>
 * Because a ContentChannel might have a different lifespan than the originating Request and Response
 * objects, all instances of this interface are internally backed by a reference to the {@link Container} that was
 * active when the initial Request was created. This ensures that the configured environment of the ContentChannel is
 * stable throughout its lifetime. This also means that the {@link #close(CompletionHandler)} method MUST be called in
 * order to release that reference. Failure to do so will prevent the Container from ever shutting down. This
 * requirement is regardless of any errors that may occur while calling any of its other methods or its derived {@link
 * CompletionHandler}s.
 *
 * @author Simon Thoresen Hult
 */
public interface ContentChannel {

    /**
     * Schedules the given {@link ByteBuffer} to be written to the content corresponding to this ContentChannel. This
     * call <em>transfers ownership</em> of the given ByteBuffer to this ContentChannel, i.e. no further calls can be
     * made to the buffer. The execution of writes happen in the same order as this method was invoked.
     *
     * @param buf     The {@link ByteBuffer} to schedule for write. No further calls can be made to this buffer.
     * @param handler The {@link CompletionHandler} to call after the write has been executed.
     */
    void write(ByteBuffer buf, CompletionHandler handler);

    /**
     * Closes this ContentChannel. After a channel is closed, any further attempt to invoke {@link #write(ByteBuffer,
     * CompletionHandler)} upon it will cause an {@link IllegalStateException} to be thrown. If this channel is already
     * closed then invoking this method has no effect, but {@link CompletionHandler#completed()} will still be called.
     *
     * Notice that you MUST call this method, regardless of any exceptions that might have occurred while writing to this
     * ContentChannel. Failure to do so will prevent the {@link Container} from ever shutting down.
     *
     * @param handler The {@link CompletionHandler} to call after the close has been executed.
     */
    void close(CompletionHandler handler);


    /**
     * Invoked when an error occurs during processing of request content. Signals that the caller was
     * unable to write all data to this ContentChannel.
     *
     * This method can be invoked at any time after the content channel is created, but it's never invoked after {@link #close(CompletionHandler)}.
     * {@link #close(CompletionHandler)} will be invoked immediately after this method returning
     * (no intermediate calls to #{@link #write(ByteBuffer, CompletionHandler)}).
     */
    default void onError(Throwable error) {}

}
