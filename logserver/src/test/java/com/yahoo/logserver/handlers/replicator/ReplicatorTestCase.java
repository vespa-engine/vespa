// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.replicator;

import com.yahoo.io.Listener;
import com.yahoo.log.InvalidLogFormatException;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.Server;
import com.yahoo.logserver.filter.LogFilter;
import com.yahoo.logserver.filter.LogFilterManager;
import com.yahoo.logserver.formatter.LogFormatterManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplicatorTestCase {
    Server server;
    Thread serverThread;
    Replicator replicator;
    private SocketChannel socket;
    private Listener listener;
    private ReplicatorConnection conn;

    @Before
    public void setUp() throws InterruptedException, IOException {
        server = Server.getInstance();
        server.initialize(18321);
        serverThread = new Thread(server);
        serverThread.start();
        long start = System.currentTimeMillis();
        long timeout = 60000;
        while (System.currentTimeMillis() < (start + timeout)) {
            try {
                socket = SocketChannel.open(new InetSocketAddress("localhost", 18321));
                break;
            } catch (Exception e) {
                Thread.sleep(100);
            }
        }
        listener = new Listener("test");
        replicator = new Replicator(18323);
        conn = (ReplicatorConnection) replicator.newConnection(socket, listener);
    }

    @After
    public void tearDown() throws InterruptedException {
        if (serverThread != null) {
            serverThread.interrupt();
            serverThread.join();
        }
        if (replicator != null) replicator.close();
    }

    @Test
    public void testReplicator() throws IOException, InvalidLogFormatException {
        LogMessage msg = LogMessage.
                                           parseNativeFormat("1343996283.239582\texample.yahoo.com\t27301/7637\tconfig-sentinel\trunserver\tevent\tfoo");
        assertFalse(conn.isLoggable(msg)); // Default all muted
        conn.onUse("system.all");
        assertTrue(conn.isLoggable(msg));
        assertTrue(conn.getTotalBytesWritten() > 50); // Should be in this ballpark
        conn.onCommand("use system.mute");
        assertFalse(conn.isLoggable(msg));
        assertEquals("system.mute", conn.getLogFilterName());
        replicator.doHandle(msg);
        conn.onFormatter("system.textformatter");
        assertEquals(conn.formatter, LogFormatterManager.getLogFormatter("system.textformatter"));
        conn.onCommand("formatter system.nullformatter");
        assertEquals(conn.formatter, LogFormatterManager.getLogFormatter("system.nullformatter"));
        assertEquals(4, conn.getNumHandled());
        conn.onList();
        assertEquals(11, conn.getNumHandled()); // 6 filters + start/stop msg
        conn.onCommand("list");
        assertEquals(18, conn.getNumHandled()); // 6 filters + start/stop msg
        conn.onListFormatters();
        assertEquals(22, conn.getNumHandled()); // 4 formatters
        conn.onCommand("listformatters");
        assertEquals(26, conn.getNumHandled()); // 4 formatters

        conn.onStats();
        assertEquals(27, conn.getNumHandled()); // 1 line
        conn.onCommand("stats");
        assertEquals(28, conn.getNumHandled()); // 1 line
        conn.enqueue(ByteBuffer.wrap("baz".getBytes()));
        assertEquals(29, conn.getNumHandled()); // 1 line
        conn.onCommand("ping");
        assertEquals(30, conn.getNumHandled()); // 1 line
        conn.onCommand("quit");
        assertEquals(31, conn.getNumHandled()); // 1 line
        assertEquals(0, conn.getNumDropped());
        assertEquals(0, conn.getNumQueued());
        LogFilterManager.addLogFilter("test.onlyerror", new LogFilter() {
            @Override
            public boolean isLoggable(LogMessage msg) {
                return msg.getLevel().equals(LogLevel.ERROR);
            }

            @Override
            public String description() {
                return "Only error";
            }
        });
        conn.onUse("test.onlyerror");
        assertFalse(conn.isLoggable(LogMessage.
                                                      parseNativeFormat("1343996283.239582\texample.yahoo.com\t27301/7637\tconfig-sentinel\trunserver\tdebug\tfoo")));
        assertTrue(conn.isLoggable(LogMessage.
                                                     parseNativeFormat("1343996283.239582\texample.yahoo.com\t27301/7637\tconfig-sentinel\trunserver\terror\tbar")));
        assertEquals(conn.selectOps(), 1);
        assertEquals(conn.description(), "Only error");
        conn.setFilter(null);
        assertTrue(conn.isLoggable(LogMessage.
                                                     parseNativeFormat("1343996283.239582\texample.yahoo.com\t27301/7637\tconfig-sentinel\trunserver\terror\tbar")));
        assertEquals(conn.description(), "No filter defined");
        assertEquals(conn.getRemoteHost(), "localhost");
        conn.onFormatter("nonexistant");
        assertEquals(conn.formatter, LogFormatterManager.getLogFormatter("system.nullformatter")); // unchanged
        conn.onUse("nonexistant");
        assertTrue(conn.isLoggable(LogMessage.
                                                     parseNativeFormat("1343996283.239582\texample.yahoo.com\t27301/7637\tconfig-sentinel\trunserver\terror\tbar")));
        conn.close();
    }

}
