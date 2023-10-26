// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Request;

import java.nio.ByteBuffer;

/**
 * <p>This class provides a convenient implementation of {@link ContentChannel} that does not support being written to.
 * If {@link #write(ByteBuffer, CompletionHandler)} is called, it throws an UnsupportedOperationException. If {@link
 * #close(CompletionHandler)} is called, it calls the given {@link CompletionHandler}.</p>
 *
 * <p>A {@link RequestHandler}s that does not expect content can simply return the {@link #INSTANCE} of this class for
 * every invocation of its {@link RequestHandler#handleRequest(Request, ResponseHandler)}.</p>
 *
 * @author Simon Thoresen Hult
 */
public final class NullContent implements ContentChannel {

    public static final NullContent INSTANCE = new NullContent();

    private NullContent() {
        // hide
    }

    @Override
    public void write(ByteBuffer buf, CompletionHandler handler) {
        if (buf.hasRemaining()) {
            throw new UnsupportedOperationException();
        }
        if (handler != null) {
            handler.completed();
        }
    }

    @Override
    public void close(CompletionHandler handler) {
        if (handler != null) {
            handler.completed();
        }
    }

}
