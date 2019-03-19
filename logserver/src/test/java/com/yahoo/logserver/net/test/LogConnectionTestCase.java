// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.net.test;

import com.yahoo.log.LogLevel;
import com.yahoo.log.LogMessage;
import com.yahoo.logserver.LogDispatcher;
import com.yahoo.logserver.handlers.AbstractLogHandler;
import com.yahoo.logserver.net.LogConnection;
import com.yahoo.logserver.test.MockLogEntries;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * Unit tests for the log connection class.  Wow is this too
 * complex!
 *
 * @author Bjorn Borud
 */
public class LogConnectionTestCase {
    private static final Logger log = Logger.getLogger(LogConnectionTestCase.class.getName());

    private static final Charset charset = Charset.forName("utf-8");
    private static final ByteBuffer bigBuffer;
    private int port;

    static {
        StringBuilder sb = new StringBuilder(LogConnection.READBUFFER_SIZE * 6)
                .append(MockLogEntries.getMessages()[0].toString())
                .append(MockLogEntries.getMessages()[1].toString())
                .append(MockLogEntries.getMessages()[2].toString());

        // get a valid log message prefix
        String prefix = MockLogEntries.getMessages()[2].toString();
        prefix = prefix.substring(0, prefix.length() - 1);
        sb.append(prefix);

        // fill up the remaining buffer with rubbish to make
        // it too long
        for (int i = 0; i < (LogConnection.READBUFFER_SIZE * 3); i++) {
            sb.append("a");
        }
        sb.append('\n');
        sb.append(MockLogEntries.getMessages()[2].toString());
        bigBuffer = charset.encode(sb.toString());
    }

    /**
     * this code is nothing short of completely hideous.  the exception
     * handling is awful and the code is messy, but it should be a fairly
     * efficient and robust way of testing buffer overflow conditions.
     */
    @Test
    public void testOverflow() {
        final CyclicBarrier barrier = new CyclicBarrier(2);


        // this inner class is used to define a mock handler
        // which will help us examine the messages actually
        // handled.
        class MockHandler extends AbstractLogHandler {
            private final List<LogMessage> messages = new LinkedList<LogMessage>();

            public boolean doHandle(LogMessage msg) {
                messages.add(msg);
                return true;
            }

            public List<LogMessage> getMessages() {
                return messages;
            }

            public void flush() {
            }

            public void close() {
            }

            public String toString() {
                return null;
            }
        }

        Thread serverThread = new Thread() {
            public void run() {
                ServerSocketChannel ss = setUpListenSocket();
                if (ss == null) {
                    fail("unable to set up listen socket");
                    return;
                }

                setPort(ss.socket().getLocalPort());

                // listen port is up now so we can trigger the barrier
                try {
                    barrier.await();

                    while (!Thread.currentThread().isInterrupted()) {
                        SocketChannel s = ss.accept();
                        pushBigBuffer(s);
                        s.close();
                    }
                } catch (BrokenBarrierException e) {
                    fail(e.getMessage());
                    return;
                } catch (InterruptedException | java.nio.channels.ClosedByInterruptException e) {
                    return;
                } catch (IOException e) {
                    log.log(LogLevel.ERROR, "argh", e);
                    fail();
                    return;
                }

            }
        };
        serverThread.start();
        assertTrue(serverThread.isAlive());

        try {
            barrier.await();
        } catch (BrokenBarrierException e) {
            fail(e.getMessage());
            return;
        } catch (InterruptedException e) {
            return;
        }

        SocketChannel sock;
        try {
            sock = SocketChannel.open(new InetSocketAddress("localhost", port));
        } catch (IOException e) {
            fail(e.getMessage());
            return;
        }

        LogDispatcher dispatcher = new LogDispatcher();
        MockHandler mock = new MockHandler();
        assertTrue(mock.getName().endsWith("MockHandler"));
        dispatcher.registerLogHandler(mock);
        LogConnection logConnection =
                new LogConnection(sock, null, dispatcher);

        try {
            for (int i = 0; i < 100; i++) {
                logConnection.read();
            }
        } catch (java.nio.channels.ClosedChannelException e) {
            // ignore, this is normal
        } catch (IOException e) {
            log.log(LogLevel.ERROR, "error during reading", e);
        }

        // there should be 5 messages
        assertEquals(5, mock.getMessages().size());
        assertEquals(5, mock.getCount());

        // the 4'th message should be long
        String m = (mock.getMessages().get(3)).getPayload();
        assertTrue(m.length() > 10000);

        serverThread.interrupt();
        try {
            serverThread.join();
            assertTrue(true);
        } catch (InterruptedException e) {
            fail();
        }
    }

    private void pushBigBuffer(SocketChannel socket) {
        try {
            ByteBuffer slice = bigBuffer.slice();
            while (slice.hasRemaining()) {
                @SuppressWarnings("unused")
                int ret = socket.write(slice);
            }
        } catch (java.io.IOException e) {

        }
    }

    private void setPort(int port) {
        this.port = port;
    }

    private ServerSocketChannel setUpListenSocket() {
        int p = 18327;
        try {
            ServerSocketChannel s = ServerSocketChannel.open();
            s.socket().setReuseAddress(true);
            s.socket().bind(new InetSocketAddress("127.0.0.1", p));
            return s;
        } catch (Exception e) {
            fail(e.getMessage());
        }
        return null;
    }
}
