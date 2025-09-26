// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class ContentInputStreamTestCase {

    @Test
    void requireThatContentInputStreamExtendsUnsafeContentInputStream() {
        assertTrue(UnsafeContentInputStream.class.isAssignableFrom(ContentInputStream.class));
    }

    static class Completer implements CompletionHandler {
        public boolean complete = false;
        public void completed() { this.complete = true; }
        public void failed(Throwable t) { throw new IllegalStateException("completer failed: " + t); }
    }

    @Test
    void requireThatFinalizerClosesStream() throws Throwable {
        BufferedContentChannel channel = new BufferedContentChannel();
        byte[] buf = { 102, 111, 111 };
        channel.write(ByteBuffer.wrap(buf), new Completer());
        var detector = new Completer();
        channel.close(detector);
        makeContentInputStream(channel.toReadable());
        for (int retry = 0; retry < 1000; retry++) {
            if (detector.complete) {
                break;
            }
            System.err.println("Waiting for close() completion to be called... retries: " + retry);
            try {
                Thread.sleep(10);
                System.gc();
            } catch (InterruptedException e) {}
        }
        assertTrue(detector.complete);
        System.err.println("close() completion called");
    }

    void makeContentInputStream(ReadableContentChannel channel) {
        // throw it away to let it be GC'ed
        new ContentInputStream(channel);
    }

}
