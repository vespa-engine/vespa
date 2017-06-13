// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver;

import static org.junit.Assert.*;

import org.junit.Test;

import com.yahoo.logserver.handlers.LogHandler;
import com.yahoo.logserver.test.LogDispatcherTestCase;

public class FlusherTestCase {

    @Test
    public void testFlusher() throws InterruptedException {
        Flusher flusher = new Flusher();
        LogDispatcherTestCase.MockHandler handler = new LogDispatcherTestCase.MockHandler();
        Flusher.register(handler);
        flusher.start();
        Thread.sleep(5000);
        flusher.interrupt();
        flusher.join();
        assertTrue(handler.flushCalled > 0);
    }

}
