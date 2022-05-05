// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.rendering;

import com.yahoo.component.AbstractComponent;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;

import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Renders a response to a stream. The renderers are cloned just before
 * rendering, and must therefore obey the following contract:
 *
 * <ol>
 * <li>At construction time, only final members shall be initialized, and these
 * must refer to immutable data only.</li>
 * <li>State mutated during rendering shall be initialized in the init method.</li>
 * </ol>
 *
 * @author Tony Vaagenes
 * @author Steinar Knutsen
 */
public abstract class Renderer<RESPONSE extends Response> extends AbstractComponent implements Cloneable {

    /**
     * Used to create a separate instance for each result to render.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Renderer<RESPONSE> clone() {
        return (Renderer<RESPONSE>) super.clone();
    }

    /**
     * Initializes the mutable state, see the contract in the class
     * documentation. Called on the clone just before rendering.
     */
    public void init() {
    }

    /**
     * Render a response to a stream. The stream also exposes a ByteBuffer API
     * for efficient transactions to JDisc. The returned future will throw the
     * exception causing failure wrapped in an ExecutionException if rendering
     * was not successful.
     *
     * @param stream a stream API bridge to JDisc
     * @param response the response to render
     * @param execution the execution which created this response
     * @param request the request matching the response
     * @return a {@link CompletableFuture} containing a boolean where true indicates a successful rendering
     */
    public abstract CompletableFuture<Boolean> renderResponse(OutputStream stream, RESPONSE response,
                                                     Execution execution, Request request);

    /**
     * Name of the output encoding, if applicable.
     *
     * @return the encoding of the output if applicable, e.g. "utf-8"
     */
    public abstract String getEncoding();

    /**
     * The MIME type of the rendered content sent to the client.
     *
     * @return The mime type of the data written to the writer, e.g. "text/plain"
     */
    public abstract String getMimeType();

}
