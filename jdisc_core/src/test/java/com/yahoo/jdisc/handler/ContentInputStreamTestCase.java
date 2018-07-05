// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import org.junit.Test;

import java.util.concurrent.Future;

import static org.junit.Assert.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class ContentInputStreamTestCase {

    @Test
    public void requireThatContentInputStreamExtendsUnsafeContentInputStream() {
        assertTrue(UnsafeContentInputStream.class.isAssignableFrom(ContentInputStream.class));
    }

    @Test
    @SuppressWarnings("FinalizeCalledExplicitly")
    public void requireThatFinalizerClosesStream() throws Throwable {
        BufferedContentChannel channel = new BufferedContentChannel();
        FastContentWriter writer = new FastContentWriter(channel);
        writer.write("foo");
        writer.close();

        new ContentInputStream(channel.toReadable()).finalize();
    }

}
