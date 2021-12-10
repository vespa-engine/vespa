// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.SharedResource;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <p>This class provides a convenient way of safely dispatching a {@link Response}. It is similar in use to {@link
 * RequestDispatch}, where you need to subclass and implement and override the appropriate methods. Because a Response
 * is not a {@link SharedResource}, its construction is less strenuous, and this class is able to provide a handful of
 * convenient factory methods to dispatch the simplest of Responses.</p>
 * <p>The following is a simple example on how to use this class without the factories:</p>
 * <pre>
 * public void signalInternalError(ResponseHandler handler) {
 *     new ResponseDispatch() {
 *         &#64;Override
 *         protected Response newResponse() {
 *             return new Response(Response.Status.INTERNAL_SERVER_ERROR);
 *         }
 *         &#64;Override
 *         protected Iterable&lt;ByteBuffer&gt; responseContent() {
 *             return Collections.singleton(ByteBuffer.wrap(new byte[] { 6, 9 }));
 *         }
 *     }.dispatch(handler);
 * }
 * </pre>
 *
 * @author Simon Thoresen Hult
 */
public abstract class ResponseDispatch implements Future<Boolean> {

    private final FutureConjunction completions = new FutureConjunction();

    /**
     * <p>Creates and returns the {@link Response} to dispatch.</p>
     *
     * @return The Response to dispatch.
     */
    protected abstract Response newResponse();

    /**
     * <p>Returns an Iterable for the ByteBuffers that the {@link #dispatch(ResponseHandler)} method should write to the
     * {@link Response} once it has {@link ResponseHandler#handleResponse(Response) connected}. The default
     * implementation returns an empty list. Because this method uses the Iterable interface, you can provide the
     * ByteBuffers lazily, or as they become available.</p>
     *
     * @return The ByteBuffers to write to the Response's ContentChannel.
     */
    protected Iterable<ByteBuffer> responseContent() {
        return Collections.emptyList();
    }

    /**
     * <p>This methods calls {@link #newResponse()} to create a new {@link Response}, and then calls {@link
     * ResponseHandler#handleResponse(Response)} with that.</p>
     *
     * @param responseHandler The ResponseHandler to connect to.
     * @return The ContentChannel to write the Response's content to.
     */
    public final ContentChannel connect(ResponseHandler responseHandler) {
        return responseHandler.handleResponse(newResponse());
    }

    /**
     * <p>Convenience method for constructing a {@link FastContentWriter} over the {@link ContentChannel} returned by
     * calling {@link #connect(ResponseHandler)}.</p>
     *
     * @param responseHandler The ResponseHandler to connect to.
     * @return The FastContentWriter for the connected Response.
     */
    public final FastContentWriter connectFastWriter(ResponseHandler responseHandler) {
        return new FastContentWriter(connect(responseHandler));
    }

    /**
     * <p>This method calls {@link #connect(ResponseHandler)} to establish a {@link ContentChannel} for the {@link
     * Response}, and then iterates through all the ByteBuffers returned by {@link #responseContent()} and writes them
     * to that ContentChannel. This method uses a <code>finally</code> block to make sure that the ContentChannel is always
     * {@link ContentChannel#close(CompletionHandler) closed}.</p>
     * <p>The returned Future will wait for all CompletionHandlers associated with the Response have been
     * completed.</p>
     *
     * @param responseHandler The ResponseHandler to dispatch to.
     * @return A Future that can be waited for.
     */
    public final CompletableFuture<Boolean> dispatch(ResponseHandler responseHandler) {
        try (FastContentWriter writer = new FastContentWriter(connect(responseHandler))) {
            for (ByteBuffer buf : responseContent()) {
                writer.write(buf);
            }
            completions.addOperand(writer);
        }
        return completions.completableFuture();
    }

    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean isCancelled() {
        return false;
    }

    @Override public boolean isDone() { return completions.isDone(); }

    @Override public Boolean get() throws InterruptedException, ExecutionException { return completions.get(); }

    @Override
    public Boolean get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return completions.get(timeout, unit);
    }

    /**
     * <p>Factory method for creating a ResponseDispatch with a {@link Response} that has the given status code, and
     * ByteBuffer content.</p>
     *
     * @param responseStatus The status code of the Response to dispatch.
     * @param content        The ByteBuffer content of the Response, may be empty.
     * @return The created ResponseDispatch.
     */
    public static ResponseDispatch newInstance(int responseStatus, ByteBuffer... content) {
        return newInstance(new Response(responseStatus), Arrays.asList(content));
    }

    /**
     * <p>Factory method for creating a ResponseDispatch with a {@link Response} that has the given status code, and
     * collection of ByteBuffer content.
     * Because this method uses the Iterable interface, you can create the ByteBuffers lazily, or
     * provide them as they become available.</p>
     *
     * @param responseStatus The status code of the Response to dispatch.
     * @param content        The provider of the Response's ByteBuffer content.
     * @return The created ResponseDispatch.
     */
    public static ResponseDispatch newInstance(int responseStatus, Iterable<ByteBuffer> content) {
        return newInstance(new Response(responseStatus), content);
    }

    /**
     * <p>Factory method for creating a ResponseDispatch over a given {@link Response} and ByteBuffer content.</p>
     *
     * @param response The Response to dispatch.
     * @param content  The ByteBuffer content of the Response, may be empty.
     * @return The created ResponseDispatch.
     */
    public static ResponseDispatch newInstance(Response response, ByteBuffer... content) {
        return newInstance(response, Arrays.asList(content));
    }

    /**
     * <p>Factory method for creating a ResponseDispatch over a given {@link Response} and ByteBuffer content.
     * Because this method uses the Iterable interface, you can create the ByteBuffers lazily, or provide them as they
     * become available.</p>
     *
     * @param response The Response to dispatch.
     * @param content  The provider of the Response's ByteBuffer content.
     * @return The created ResponseDispatch.
     */
    public static ResponseDispatch newInstance(Response response, Iterable<ByteBuffer> content) {
        return new ResponseDispatch() {

            @Override
            protected Response newResponse() {
                return response;
            }

            @Override
            public Iterable<ByteBuffer> responseContent() {
                return content;
            }
        };
    }

}
