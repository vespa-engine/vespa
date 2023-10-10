// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.Test;

import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.test.LogDispatcherTestCase;

public class HandlerThreadTestCase {

    @Test
    public void testHandlerThread() throws InterruptedException, InvalidLogFormatException {
        HandlerThread thread = new HandlerThread("myThread");
        LogDispatcherTestCase.MockHandler h1 = new LogDispatcherTestCase.MockHandler();
        LogDispatcherTestCase.MockHandler h2 = new LogDispatcherTestCase.MockHandler();
        LogDispatcherTestCase.MockHandler h3 = new LogDispatcherTestCase.MockHandler();
        thread.registerHandler(h1);
        thread.registerHandler(h2);
        thread.registerHandler(h3);
        thread.start();
        String msgstr1 = "1098709001\t"
                + "nalle.puh.com\t"
                + "23234\t"
                + "serviceName\t"
                + "tst\t"
                + "info\t"
                + "this is a test";
        String msgstr2 = "1098709002\t"
                + "nalle.puh.com\t"
                + "23234\t"
                + "serviceName1\t"
                + "tst\t"
                + "info\t"
                + "this is a test too";
        String msgstr3 = "1098709003\t"
                + "nalle.puh.com\t"
                + "23234\t"
                + "serviceName3\t"
                + "tst\t"
                + "info\t"
                + "this is a test also";
        final LogMessage msg1 = LogMessage.parseNativeFormat(msgstr1);
        final LogMessage msg2 = LogMessage.parseNativeFormat(msgstr2);
        final LogMessage msg3 = LogMessage.parseNativeFormat(msgstr3);
        thread.handle(msg1);
        while ((h1.messages.size() < 1) || (h2.messages.size() < 1) || (h3.messages.size() < 1)) {
            Thread.sleep(10);
        }
        assertEquals(h1.messages.size(), 1);
        assertEquals(h2.messages.size(), 1);
        assertEquals(h3.messages.size(), 1);
        thread.handle(new ArrayList<LogMessage>() {{add(msg1); add(msg2); add(msg3); }});
        while ((h1.messages.size() < 4) || (h2.messages.size() < 4) || (h3.messages.size() < 4)) {
            Thread.sleep(10);
        }
        assertEquals(h1.messages.size(), 4);
        assertEquals(h2.messages.size(), 4);
        assertEquals(h3.messages.size(), 4);
        assertTrue(thread.getQueue().isEmpty());
        thread.unregisterHandler(h3);
        assertEquals(thread.getHandlers().length, 2);
        thread.flush();
        thread.close();
        thread.interrupt();
        thread.join();
    }

    @Test
    public void testAbortThread() throws InvalidLogFormatException, InterruptedException {
        HandlerThread thread = new HandlerThread("myThread");
        LogDispatcherTestCase.MockHandler h1 = new LogDispatcherTestCase.MockHandler();
        thread.registerHandler(h1);
        thread.start();
        String msgstr1 = "1098709001\t"
                + "nalle.puh.com\t"
                + "23234\t"
                + "serviceName\t"
                + "tst\t"
                + "info\t"
                + "this is a test";
        final LogMessage msg1 = LogMessage.parseNativeFormat(msgstr1);
        thread.handle(msg1);
        while (h1.messages.size() < 1) {
            Thread.sleep(10);
        }
        assertEquals(h1.messages.size(), 1);
        thread.interrupt();
        thread.join();
    }

}
