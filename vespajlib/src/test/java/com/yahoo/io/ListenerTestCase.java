// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.collections.Tuple2;
import com.yahoo.concurrent.Receiver;
import com.yahoo.concurrent.Receiver.MessageState;

/**
 * Test a NIO based Reactor pattern implementation, com.yahoo.io.Listener.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ListenerTestCase {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    Receiver<Byte> r = new Receiver<>();

    private final class MockConnection implements Connection {

        private SocketChannel channel;

        MockConnection(SocketChannel channel, Listener listener) {
            this.channel = channel;
        }

        @Override
        public void write() throws IOException {
        }

        @Override
        public void read() throws IOException {
            ByteBuffer b = ByteBuffer.allocate(1);
            channel.read(b);
            b.flip();
            r.put(b.get());
        }

        @Override
        public void close() throws IOException {
            channel.close();

        }

        @Override
        public void connect() throws IOException {
        }

        @Override
        public int selectOps() {
            return SelectionKey.OP_READ;
        }

        @Override
        public SocketChannel socketChannel() {
            return channel;
        }
    }

    private final class GetConnection implements ConnectionFactory {

        @Override
        public Connection newConnection(SocketChannel channel, Listener listener) {
            return new MockConnection(channel, listener);
        }
    }

    @Test
    public final void testRun() throws IOException, InterruptedException {
        Listener l = new Listener("ListenerTestCase");
        l.listen(new GetConnection(), 0);
        l.start();
        int port = ((InetSocketAddress) l.acceptors.get(0).socket.getLocalAddress()).getPort();
        Socket s = new Socket("127.0.0.1", port);
        final byte expected = 42;
        s.getOutputStream().write(expected);
        s.getOutputStream().flush();
        s.close();
        Tuple2<MessageState, Byte> received = r.get(60 * 1000);
        l.acceptors.get(0).interrupt();
        l.acceptors.get(0).socket.close();
        l.acceptors.get(0).join();
        l.interrupt();
        l.join();
        assertTrue("Test timed out.", received.first == MessageState.VALID);
        assertEquals(expected, received.second.byteValue());
    }

}
