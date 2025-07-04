// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.jdisc.handler.CompletionHandler;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * <p>Reasonably simple async response writer interface that allows for buffered and streaming
 * response semantics. The devil is in the details, so please read carefully!</p>
 *
 * <p>All writes performed <em>prior</em> to invoking {@link #commit(int, String, boolean)} will be
 * enqueued (and then subsequently flushed at commit time). All writes performed <em>after</em>
 * invoking {@link #commit(int, String, boolean)}  will be sent immediately without intermediate queuing.</p>
 *
 * <p>This means supporting buffered vs. streaming response semantics becomes a matter of deciding
 * when to commit relative to performing the response payload writes. However, if this is used in a
 * <em>non-streaming</em> context, great care must be taken to avoid a catch-22/deadlock scenario
 * where invoking {@link #commit(int, String, boolean)} transitively depends on the invocation of
 * one or more completion handlers already submitted via {@link #write(ByteBuffer, CompletionHandler)},
 * as these will <em>not</em> be invoked prior to commit.</p>
 *
 * <p>Note that any and all response headers are sent at commit-time and cannot be changed after
 * the fact. This also includes the HTTP status code.</p>
 *
 * @author vekterli
 */
interface ResponseWriter extends AutoCloseable {
    /**
     * Commit response status and headers, and flush all queued buffers from previous
     * {@link #write(ByteBuffer, CompletionHandler)} calls in-order to the underlying transport.
     * After commit, all subsequent writes will be dispatched directly to the underlying
     * transport and will <em>not</em> be implicitly enqueued.
     *
     * @param status HTTP status code.
     * @param contentType String to send verbatim as HTTP <code>Content-Type</code> header iff non-null.
     * @param fullyApplied Whether the request this response was created for had all its fields
     *                     recognized during processing. Should only be false if one or more request
     *                     fields were ignored. Only really makes sense for mutating operations.
     */
    void commit(int status, String contentType, boolean fullyApplied) throws IOException;

    /**
     * Write data to the underlying transport, with an optional completion handler.
     *
     * <strong>Important:</strong> any provided completion handler will <em>not</em> be invoked
     * before {@link #commit(int, String, boolean)} has been called on the same instance used for
     * writing. See the class comments for why this matters.
     *
     * @param buffer Buffer containing data to be written verbatim to the underlying transport.
     * @param completionHandlerOrNull Completion handler that will be invoked once the write has been
     *                                dispatched to the receiver. <strong>Important:</strong> it is
     *                                unspecified which thread this happens from, and the handler may
     *                                be invoked by the same thread that called <code>write</code>
     *                                <em>while</em> <code>write</code> is being called, i.e. recursively.
     *                                If null, a default no-op completion handler will be substituted.
     */
    void write(ByteBuffer buffer, CompletionHandler completionHandlerOrNull);

    /**
     * Narrowing of exception specifier of {@link AutoCloseable#close()}.
     */
    void close() throws IOException;
}
