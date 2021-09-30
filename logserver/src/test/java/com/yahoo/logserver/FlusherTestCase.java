// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver;

import com.yahoo.log.LogMessage;
import com.yahoo.logserver.handlers.LogHandler;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class FlusherTestCase {

    @Test
    public void flush_is_invoked_on_handler_and_flusher_can_be_shut_down() throws InterruptedException {
        Flusher flusher = new Flusher();
        MockHandler handler = new MockHandler();
        Flusher.register(handler);
        flusher.start();
        try {
            assertTrue(handler.flushInvoked.await(60, TimeUnit.SECONDS));
        } finally {
            flusher.interrupt();
            flusher.join();
        }
    }

    private static class MockHandler implements LogHandler {

        private final CountDownLatch flushInvoked = new CountDownLatch(1);

        @Override public void flush() { flushInvoked.countDown(); }

        @Override public void handle(LogMessage msg) {}
        @Override public void handle(List<LogMessage> messages) {}
        @Override public void close() {}
    }

}
