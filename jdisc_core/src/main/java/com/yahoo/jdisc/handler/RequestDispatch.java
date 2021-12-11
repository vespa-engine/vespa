// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.References;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.SharedResource;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <p>This class provides a convenient way of safely dispatching a {@link Request}. Using this class you do not have to
 * worry about the exception safety surrounding the {@link SharedResource} logic. The internal mechanics of this class
 * will ensure that anything that goes wrong during dispatch is safely handled according to jDISC contracts.</p>
 *
 * <p>It also provides a default implementation of the {@link ResponseHandler} interface that returns a {@link
 * NullContent}. If you want to return a different {@link ContentChannel}, you need to override {@link
 * #handleResponse(Response)}.</p>
 *
 * <p>The following is a simple example on how to use this class:</p>
 * <pre>
 * public void handleRequest(final Request parent, final ResponseHandler handler) {
 *     new RequestDispatch() {
 *         &#64;Override
 *         protected Request newRequest() {
 *             return new Request(parent, URI.create("http://remotehost/"));
 *         }
 *         &#64;Override
 *         protected Iterable&lt;ByteBuffer&gt; requestContent() {
 *             return Collections.singleton(ByteBuffer.wrap(new byte[] { 6, 9 }));
 *         }
 *         &#64;Override
 *         public ContentChannel handleResponse(Response response) {
 *             return handler.handleResponse(response);
 *         }
 *     }.dispatch();
 * }
 * </pre>
 *
 * @author Simon Thoresen Hult
 */
public abstract class RequestDispatch implements Future<Response>, ResponseHandler {

    private final FutureConjunction completions = new FutureConjunction();
    private final FutureResponse futureResponse = new FutureResponse(this);

    /**
     * <p>Creates and returns the {@link Request} to dispatch. The internal code that calls this method takes care of
     * the necessary exception safety of connecting the Request.</p>
     *
     * @return The Request to dispatch.
     */
    protected abstract Request newRequest();

    /**
     * <p>Returns an Iterable for the ByteBuffers that the {@link #dispatch()} method should write to the {@link
     * Request} once it has {@link #connect() connected}. The default implementation returns an empty list. Because this
     * method uses the Iterable interface, you can create the ByteBuffers lazily, or provide them as they become
     * available.</p>
     *
     * @return The ByteBuffers to write to the Request's ContentChannel.
     */
    protected Iterable<ByteBuffer> requestContent() {
        return Collections.emptyList();
    }

    /**
     * <p>This methods calls {@link #newRequest()} to create a new {@link Request}, and then calls {@link
     * Request#connect(ResponseHandler)} on that. This method uses a <code>finally</code> block to make sure that the
     * Request is always {@link Request#release() released}.</p>
     *
     * @return The ContentChannel to write the Request's content to.
     */
    public final ContentChannel connect() {
        final Request request = newRequest();
        try (final ResourceReference ref = References.fromResource(request)) {
            return request.connect(futureResponse);
        }
    }

    /**
     * <p>This is a convenient method to construct a {@link FastContentWriter} over the {@link ContentChannel} returned by
     * calling {@link #connect()}.</p>
     *
     * @return The ContentWriter for the connected Request.
     */
    public final FastContentWriter connectFastWriter() {
        return new FastContentWriter(connect());
    }

    /**
     * <p>This method calls {@link #connect()} to establish a {@link ContentChannel} for the {@link Request}, and then
     * iterates through all the ByteBuffers returned by {@link #requestContent()} and writes them to that
     * ContentChannel. This method uses a <code>finally</code> block to make sure that the ContentChannel is always {@link
     * ContentChannel#close(CompletionHandler) closed}.</p>
     *
     * <p>The returned Future will wait for all CompletionHandlers associated with the Request have been completed, and
     * a {@link Response} has been received.</p>
     *
     * @return A Future that can be waited for.
     */
    public final CompletableFuture<Response> dispatch() {
        try (FastContentWriter writer = new FastContentWriter(connect())) {
            for (ByteBuffer buf : requestContent()) {
                writer.write(buf);
            }
            completions.addOperand(writer);
        }
        return CompletableFuture.allOf(completions.completableFuture(), futureResponse)
                .thenApply(__ -> {
                    try {
                        return futureResponse.get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new IllegalStateException(e); // Should not happens since both futures are complete
                    }
                });
    }

    public void addListener(Runnable listener, Executor executor) {
        CompletableFuture.allOf(completions.completableFuture(), futureResponse)
                .whenCompleteAsync((__, ___) -> listener.run(), executor);
    }

    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean isCancelled() {
        return false;
    }

    @Override
    public final boolean isDone() {
        return completions.isDone() && futureResponse.isDone();
    }

    @Override
    public final Response get() throws InterruptedException, ExecutionException {
        completions.get();
        return futureResponse.get();
    }

    @Override
    public final Response get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
                                                                  TimeoutException
    {
        long now = System.nanoTime();
        completions.get(timeout, unit);
        return futureResponse.get(unit.toNanos(timeout) - (System.nanoTime() - now), TimeUnit.NANOSECONDS);
    }

    @Override
    public ContentChannel handleResponse(Response response) {
        return NullContent.INSTANCE;
    }
}
