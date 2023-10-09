// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;

import java.nio.ByteBuffer;

/**
 * @author Simon Thoresen Hult
 */
public final class NonWorkingContentChannel implements ContentChannel {

    @Override
    public void write(ByteBuffer buf, CompletionHandler handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close(CompletionHandler handler) {
        throw new UnsupportedOperationException();
    }
}
