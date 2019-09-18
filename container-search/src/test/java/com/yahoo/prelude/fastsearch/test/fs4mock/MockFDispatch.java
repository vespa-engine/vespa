// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch.test.fs4mock;


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.yahoo.prelude.ConfigurationException;
import com.yahoo.prelude.fastsearch.test.DocsumDefinitionTestCase;


/**
 * A server which replies to any query with the same query result after
 * a configurable delay, with a configurable slowness (delay between each byte).
 * Connections are never timed out.
 *
 * @author bratseth
 */
public class MockFDispatch {

    private static int connectionCount = 0;

    private static Logger log = Logger.getLogger(MockFDispatch.class.getName());

    /** The port we accept incoming requests at */
    private int listenPort = 0;

    private long replyDelay;

    private long byteDelay;

    private Object barrier;

    private static byte[] queryResultPacketData = new byte[] {
        0, 0, 0, 64, 0, 0,
        0, 214 - 256, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 5, 0, 0, 0,
        25, 0, 0, 0, 111, 0, 0, 0, 97, 0, 0, 0, 3, 0, 0, 0, 23, 0, 0, 0, 7, 0, 0,
        0, 36, 0, 0, 0, 4, 0, 0, 0, 21, 0, 0, 0, 8, 0, 0, 0, 37};

    private static byte[] docsumData = DocsumDefinitionTestCase.makeDocsum();

    private static byte[] docsumHeadPacketData = new byte[] {
        0, 0, 3, 39, 0, 0,
        0, 205 - 256, 0, 0, 0, 1, 0, 0, 0, 0};

    private static byte[] eolPacketData = new byte[] {
        0, 0, 0, 8, 0, 0, 0,
        200 - 256, 0, 0, 0, 1 };

    private Set<ConnectionThread> connectionThreads = new HashSet<>();

    public MockFDispatch(int listenPort, long replyDelay, long byteDelay) {
        this.replyDelay = replyDelay;
        this.byteDelay = byteDelay;
        this.listenPort = listenPort;
    }

    public void setBarrier(Object barrier) {
        this.barrier = barrier;
    }

    public void setListenPort(int listenPort) {
        this.listenPort = listenPort;
    }

    public void run() {
        try {
            ServerSocketChannel channel = createServerSocket(listenPort);

            channel.socket().setReuseAddress(true);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // notify those waiting at the barrier that they
                    // can now proceed and talk to us
                    synchronized (barrier) {
                        if (barrier != null) {
                            barrier.notify();
                        }
                    }
                    SocketChannel socketChannel = channel.accept();

                    connectionThreads.add(new ConnectionThread(socketChannel));
                } catch (ClosedByInterruptException e) {// We'll exit
                } catch (ClosedChannelException e) {
                    return;
                } catch (Exception e) {
                    log.log(Level.WARNING, "Unexpected error reading request", e);
                }
            }
            channel.close();
        } catch (IOException e) {
            throw new ConfigurationException("Socket channel failure", e);
        }
    }

    private ServerSocketChannel createServerSocket(int listenPort)
        throws IOException {
        ServerSocketChannel channel = ServerSocketChannel.open();
        ServerSocket socket = channel.socket();

        socket.bind(
                new InetSocketAddress(InetAddress.getLocalHost(), listenPort));
        String host = socket.getInetAddress().getHostName();

        log.fine("Accepting dfispatch requests at " + host + ":" + listenPort);
        return channel;
    }

    public static void main(String[] args) {
        log.setLevel(Level.FINE);
        MockFDispatch m = new MockFDispatch(7890, Integer.parseInt(args[0]),
                Integer.parseInt(args[1]));

        m.run();
    }

    private class ConnectionThread extends Thread {

        private ByteBuffer writeBuffer = ByteBuffer.allocate(2000);

        private ByteBuffer readBuffer = ByteBuffer.allocate(2000);

        private int connectionNr = 0;

        private SocketChannel channel;

        public ConnectionThread(SocketChannel channel) {
            this.channel = channel;
            fillBuffer(writeBuffer);
            start();
        }

        private void fillBuffer(ByteBuffer buffer) {
            buffer.clear();
            buffer.put(queryResultPacketData);
            buffer.put(docsumHeadPacketData);
            buffer.put(docsumData);
            buffer.put(docsumHeadPacketData);
            buffer.put(docsumData);
            buffer.put(eolPacketData);
        }

        public void run() {
            connectionNr = connectionCount++;
            log.fine("Opened connection " + connectionNr);

            try {
                long lastRequest = System.currentTimeMillis();

                while ((System.currentTimeMillis() - lastRequest) <= 5000
                        && (!isInterrupted())) {
                    readBuffer.clear();
                    channel.read(readBuffer);
                    lastRequest = System.currentTimeMillis();
                    delay(replyDelay);

                    if (byteDelay > 0) {
                        writeSlow(writeBuffer);
                    } else {
                        write(writeBuffer);
                    }
                    log.fine(
                            "Replied in "
                                    + (System.currentTimeMillis() - lastRequest)
                                    + " ms");
                }

                log.fine("Closing timed out connection " + connectionNr);
                connectionCount--;
                channel.close();
            } catch (IOException e) {}
        }

        private void write(ByteBuffer writeBuffer) throws IOException {
            writeBuffer.flip();
            channel.write(writeBuffer);
        }

        private void writeSlow(ByteBuffer writeBuffer) throws IOException {
            writeBuffer.flip();
            int dataSize = writeBuffer.limit();

            for (int i = 0; i < dataSize; i++) {
                writeBuffer.position(i);
                writeBuffer.limit(i + 1);
                channel.write(writeBuffer);
                delay(byteDelay);
            }
            writeBuffer.limit(dataSize);
        }

        private void delay(long delay) {

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {}
        }

    }

}
