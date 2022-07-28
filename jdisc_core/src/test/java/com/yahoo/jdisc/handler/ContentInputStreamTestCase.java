// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class ContentInputStreamTestCase {

    @Test
    void requireThatContentInputStreamExtendsUnsafeContentInputStream() {
        assertTrue(UnsafeContentInputStream.class.isAssignableFrom(ContentInputStream.class));
    }

    @Test
    @SuppressWarnings("FinalizeCalledExplicitly")
    void requireThatFinalizerClosesStream() throws Throwable {
        BufferedContentChannel channel = new BufferedContentChannel();
        FastContentWriter writer = new FastContentWriter(channel);
        writer.write("foo");
        writer.close();

        new ContentInputStream(channel.toReadable()).finalize();
    }

}
