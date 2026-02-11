// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.test;

import java.util.ArrayList;
import java.util.List;

import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.logserver.LogDispatcher;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.handlers.LogHandler;

import org.junit.*;

import static org.junit.Assert.*;

/**
 * Unit tests for thge LogMessage class.
 *
 * @author Bjorn Borud
 */
public class LogDispatcherTestCase {
    private static LogMessage sample1;
    private static LogMessage sample2;

    static {
        try {
            sample1 = LogMessage.parseNativeFormat("1096639280.524133	malfunction	26851	-	logtest	info	Starting up, called as ./log/logtest");
            sample2 = LogMessage.parseNativeFormat("1096639280.524133	malfunction	26851	-	logtest	info	More crap");
        } catch (InvalidLogFormatException e) {
            assertTrue(false);
        }
    }

    public static class MockHandler implements LogHandler {
        public final List<LogMessage> messages = new ArrayList<LogMessage>(5);
        public int flushCalled = 0;
        public int closeCalled = 0;

        public void handle(LogMessage msg) {
            messages.add(msg);
        }

        public void handle(List<LogMessage> messages) {
            for (LogMessage lm : messages) {
                handle(lm);
            }
        }

        public void flush() {
            flushCalled++;
        }

        public void close() {
            closeCalled++;
        }

        public String getName() {
            return MockHandler.class.getName();
        }
    }

    @Test
    public void testLogDispatcherBatchMode() {
        MockHandler handler = new MockHandler();
        LogDispatcher dispatcher = new LogDispatcher();
        dispatcher.setBatchedMode(true);
        dispatcher.registerLogHandler(handler);
        assertEquals(0, dispatcher.getMessageCount());
        dispatcher.handle(sample1);
        assertEquals(1, dispatcher.getMessageCount());
        dispatcher.handle(sample2);
        assertEquals(2, dispatcher.getMessageCount());
        assertEquals(0, handler.messages.size());
        dispatcher.flush();
        assertEquals(2, handler.messages.size());
    }

    @Test
    public void testTestLogHandlerRegistration() {
        MockHandler handler = new MockHandler();
        LogDispatcher dispatcher = new LogDispatcher();
        dispatcher.registerLogHandler(handler);

        assertNotNull(dispatcher.getLogHandlers());
        LogHandler[] handlers = dispatcher.getLogHandlers();
        assertNotNull(handlers);
        assertEquals(1, handlers.length);
        assertTrue(handler == handlers[0]);
    }

    @Test
    public void testMessageCount() {
        MockHandler handler = new MockHandler();
        LogDispatcher dispatcher = new LogDispatcher();
        dispatcher.registerLogHandler(handler);
        assertEquals(0, dispatcher.getMessageCount());

        dispatcher.handle(sample1);
        assertEquals(1, dispatcher.getMessageCount());

        dispatcher.handle(sample2);
        assertEquals(2, dispatcher.getMessageCount());

        assertEquals(2, handler.messages.size());
    }

    @Test
    public void testVerifyMessages() {
        MockHandler handler = new MockHandler();
        LogDispatcher dispatcher = new LogDispatcher();
        dispatcher.registerLogHandler(handler);

        dispatcher.handle(sample1);
        dispatcher.handle(sample2);

        assertTrue(sample1 == handler.messages.get(0));
        assertTrue(sample2 == handler.messages.get(1));
    }

    // TODO: this test makes very little sense until we refactor a bit
    @Test
    public void testClose() {
        MockHandler handler = new MockHandler();
        LogDispatcher dispatcher = new LogDispatcher();
        dispatcher.registerLogHandler(handler);

        assertEquals(0, handler.flushCalled);
        assertEquals(0, handler.closeCalled);

        dispatcher.close();
        assertEquals(0, handler.closeCalled);
    }

}
