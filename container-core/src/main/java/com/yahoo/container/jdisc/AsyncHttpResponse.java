// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import java.io.IOException;
import java.io.OutputStream;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;

/**
 * HTTP response which supports async response rendering.
 *
 * @author bratseth
 * @author Steinar Knutsen
 */
public abstract class AsyncHttpResponse extends HttpResponse {

    /**
     * Create a new HTTP response with support for async output.
     *
     * @param status the HTTP status code for jdisc
     * @see Response
     */
    public AsyncHttpResponse(int status) {
        super(status);
    }

    /**
     * Render to output asynchronously. The output stream will not be closed
     * when this return. The implementation is responsible for closing the
     * output (using the provided channel and completion handler) when (async)
     * rendering is completed.
     *
     * @param output the stream to which content should be rendered
     * @param networkChannel the channel which must be closed on completion
     * @param handler the completion handler to submit when closing the channel, may be null
     */
    public abstract void render(OutputStream output, ContentChannel networkChannel, CompletionHandler handler)
            throws IOException;

    /**
     * Throws UnsupportedOperationException. Use
     * {@link #render(OutputStream, ContentChannel, CompletionHandler)} instead.
     */
    @Override
    public final void render(OutputStream output) {
        throw new UnsupportedOperationException("Illegal use.");
    }

}
