// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.log.LogLevel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Check error logging from ContentChannelOutputStream is sane.
 *
 * @author Steinar Knutsen
 */
public class LoggingTestCase {

    Logger logger = Logger.getLogger(ContentChannelOutputStream.class.getName());
    boolean initUseParentHandlers = logger.getUseParentHandlers();
    LogCheckHandler logChecker;
    Level initLevel;

    private static class FailingContentChannel implements ContentChannel {

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            handler.failed(new RuntimeException());
        }

        @Override
        public void close(CompletionHandler handler) {
            // NOP

        }
    }

    private class LogCheckHandler extends Handler {
        Map<Level, Integer> errorCounter = new HashMap<>();

        @Override
        public void publish(LogRecord record) {
            synchronized (errorCounter) {
                Integer count = errorCounter.get(record.getLevel());
                if (count == null) {
                    count = Integer.valueOf(0);
                }
                errorCounter.put(record.getLevel(),
                        Integer.valueOf(count.intValue() + 1));
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }
    }

    ContentChannelOutputStream stream;

    @Before
    public void setUp() throws Exception {
        stream = new ContentChannelOutputStream(new FailingContentChannel());
        logger = Logger.getLogger(ContentChannelOutputStream.class.getName());
        initUseParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logChecker = new LogCheckHandler();
        logger.addHandler(logChecker);
    }

    @After
    public void tearDown() throws Exception {
        logger.removeHandler(logChecker);
        logger.setUseParentHandlers(initUseParentHandlers);
        logger.setLevel(initLevel);
    }

    private ByteBuffer createData() {
        ByteBuffer b = ByteBuffer.allocate(10);
        return b;
    }

    @Test
    public final void testFailed() throws IOException, InterruptedException {
        stream.send(createData());
        stream.send(createData());
        stream.send(createData());
        stream.flush();
        assertNull(logChecker.errorCounter.get(LogLevel.INFO));
        assertEquals(1, logChecker.errorCounter.get(LogLevel.DEBUG).intValue());
        assertEquals(2, logChecker.errorCounter.get(LogLevel.SPAM).intValue());
    }

}
