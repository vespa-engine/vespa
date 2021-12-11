// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Response;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * This class provides an implementation of {@link ResponseHandler} that allows you to wait for a {@link Response} to
 * be returned.
 *
 * @author Simon Thoresen Hult
 */
public final class FutureResponse extends CompletableFuture<Response> implements ResponseHandler {

    private final ResponseHandler handler;

    /**
     * <p>Constructs a new FutureResponse that returns a {@link NullContent} when {@link #handleResponse(Response)} is
     * invoked.</p>
     */
    public FutureResponse() {
        this(NullContent.INSTANCE);
    }

    /**
     * <p>Constructs a new FutureResponse that returns the given {@link ContentChannel} when {@link
     * #handleResponse(Response)} is invoked.</p>
     *
     * @param content The content channel for the Response.
     */
    public FutureResponse(final ContentChannel content) {
        this(new ResponseHandler() {

            @Override
            public ContentChannel handleResponse(Response response) {
                return content;
            }
        });
    }

    public void addListener(Runnable r, Executor e) { whenCompleteAsync((__, ___) -> r.run(), e); }

    /**
     * <p>Constructs a new FutureResponse that calls the given {@link ResponseHandler} when {@link
     * #handleResponse(Response)} is invoked.</p>
     *
     * @param handler The ResponseHandler to invoke.
     */
    public FutureResponse(ResponseHandler handler) {
        this.handler = handler;
    }

    @Override
    public ContentChannel handleResponse(Response response) {
        complete(response);
        return handler.handleResponse(response);
    }

    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean isCancelled() {
        return false;
    }

}
