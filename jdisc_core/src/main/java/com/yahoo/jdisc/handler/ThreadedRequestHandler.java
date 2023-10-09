// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.Response;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * <p>This class implements a {@link RequestHandler} with a synchronous {@link #handleRequest(Request,
 * BufferedContentChannel, ResponseHandler)} API for handling {@link Request}s. An Executor is provided at construction
 * time, and all Requests are automatically scheduled for processing on that Executor.</p>
 *
 * <p>A very simple echo handler could be implemented like this:</p>
 * <pre>
 * class MyRequestHandler extends ThreadedRequestHandler {
 *
 *     &#64;Inject
 *     MyRequestHandler(Executor executor) {
 *         super(executor);
 *     }
 *
 *     &#64;Override
 *     protected void handleRequest(Request request, ReadableContentChannel requestContent, ResponseHandler handler) {
 *         ContentWriter responseContent = ResponseDispatch.newInstance(Response.Status.OK).connectWriter(handler);
 *         try {
 *             for (ByteBuffer buf : requestContent) {
 *                 responseContent.write(buf);
 *             }
 *         } catch (RuntimeException e) {
 *             requestContent.failed(e);
 *             throw e;
 *         } finally {
 *             responseContent.close();
 *         }
 *     }
 * }
 * </pre>
 *
 * @author Simon Thoresen Hult
 */
public abstract class ThreadedRequestHandler extends AbstractRequestHandler {

    private final Executor executor;
    private volatile long timeout = 0;

    protected ThreadedRequestHandler(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        this.executor = executor;
    }

    /**
     * <p>Sets the timeout that this ThreadedRequestHandler sets on all handled {@link Request}s. If the
     * <em>timeout</em> value is less than or equal to zero, no timeout will be applied.</p>
     *
     * @param timeout The allocated amount of time.
     * @param unit    The time unit of the <em>timeout</em> argument.
     */
    public final void setTimeout(long timeout, TimeUnit unit) {
        this.timeout = unit.toMillis(timeout);
    }

    /**
     * <p>Returns the timeout that this ThreadedRequestHandler sets on all handled {@link Request}s.</p>
     *
     * @param unit The unit to use for the return value.
     * @return The timeout in the appropriate unit.
     */
    public final long getTimeout(TimeUnit unit) {
        return unit.convert(timeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public final ContentChannel handleRequest(Request request, ResponseHandler responseHandler) {
        if (timeout > 0) {
            request.setTimeout(timeout, TimeUnit.MILLISECONDS);
        }
        BufferedContentChannel content = new BufferedContentChannel();
        executor.execute(new RequestTask(request, content, responseHandler));
        return content;
    }

    /**
     * <p>Override this method if you want to access the {@link Request}'s content using a {@link
     * BufferedContentChannel}. If you do not override this method, it will call {@link #handleRequest(Request,
     * ReadableContentChannel, ResponseHandler)}.</p>
     *
     * @param request         The Request to handle.
     * @param responseHandler The handler to pass the corresponding {@link Response} to.
     * @param requestContent  The content of the Request.
     */
    protected void handleRequest(Request request, BufferedContentChannel requestContent,
                                 ResponseHandler responseHandler)
    {
        ReadableContentChannel readable = requestContent.toReadable();
        try {
            handleRequest(request, readable, responseHandler);
        } finally {
            while (readable.read() != null) {} // consume all ignored content
        }
    }

    /**
     * <p>Implement this method if you want to access the {@link Request}'s content using a {@link
     * ReadableContentChannel}. If you do not override this method, it will call {@link #handleRequest(Request,
     * ContentInputStream, ResponseHandler)}.</p>
     *
     * @param request         The Request to handle.
     * @param responseHandler The handler to pass the corresponding {@link Response} to.
     * @param requestContent  The content of the Request.
     */
    protected void handleRequest(Request request, ReadableContentChannel requestContent,
                                 ResponseHandler responseHandler)
    {
        ContentInputStream inputStream = requestContent.toStream();
        try {
            handleRequest(request, inputStream, responseHandler);
        } finally {
            while (inputStream.read() >= 0) {} // consume all ignored content
        }
    }

    /**
     * <p>Implement this method if you want to access the {@link Request}'s content using a {@link ContentInputStream}.
     * If you do not override this method, it will dispatch a {@link Response} to the {@link ResponseHandler} with a
     * <code>Response.Status.NOT_IMPLEMENTED</code> status.</p>
     *
     * @param request         The Request to handle.
     * @param responseHandler The handler to pass the corresponding {@link Response} to.
     * @param requestContent  The content of the Request.
     */
    @SuppressWarnings("UnusedParameters")
    protected void handleRequest(Request request, ContentInputStream requestContent,
                                 ResponseHandler responseHandler)
    {
        ResponseDispatch.newInstance(Response.Status.NOT_IMPLEMENTED).dispatch(responseHandler);
    }

    private class RequestTask implements Runnable {

        final Request request;
        final BufferedContentChannel content;
        final ResponseHandler responseHandler;
        private final ResourceReference requestReference;

        RequestTask(Request request, BufferedContentChannel content, ResponseHandler responseHandler) {
            this.request = request;
            this.content = content;
            this.responseHandler = responseHandler;
            this.requestReference = request.refer(this);
        }

        @Override
        public void run() {
            try (final ResourceReference ref = requestReference) {
                ThreadedRequestHandler.this.handleRequest(request, content, responseHandler);
                consumeRequestContent();
            }
        }

        private void consumeRequestContent() {
            if (content.isConnected()) return;
            try {
                ReadableContentChannel requestContent = content.toReadable();
                while (requestContent.read() != null) {
                    // consume all ignored content
                }
            } catch (IllegalStateException ignored) {}
        }
    }
}
