// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4.mplex;

import com.yahoo.container.search.Fs4Config;
import com.yahoo.fs4.BasicPacket;
import com.yahoo.fs4.ChannelTimeoutException;
import com.yahoo.fs4.PacketListener;
import com.yahoo.fs4.PingPacket;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.fs4.mplex.Backend.BackendStatistics;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.search.Query;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test networking code for talking to dispatch.
 *
 * @author Steinar Knutsen
 */
public class BackendTestCase {

    public static class MockDispatch implements Runnable {

        public final ServerSocket socket;
        public volatile Socket connection;
        volatile int channelId;

        public byte[] packetData = new byte[] {0,0,0,104,
                0,0,0,217-256,
                0,0,0,1,
                0,0,0,0,
                0,0,0,2,
                0,0,0,0,0,0,0,5,
                0x40,0x39,0,0,0,0,0,0,
                0,0,0,111,
                0,0,0,97,
                0,0,0,3, 1,1,1,1,1,1,1,1,1,1,1,1, 0x40,0x37,0,0,0,0,0,23, 0,0,0,7, 0,0,0,36,
                0,0,0,4, 2,2,2,2,2,2,2,2,2,2,2,2, 0x40,0x35,0,0,0,0,0,21, 0,0,0,8, 0,0,0,37};

        public MockDispatch(ServerSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                connection = socket.accept();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            requestRespond();
        }

        void requestRespond() {
            byte[] length = new byte[4];
            try {
                connection.getInputStream().read(length);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            int actual = ByteBuffer.wrap(length).getInt();

            int read = 0;
            int i = 0;
            while (read != -1 && i < actual) {
                try {
                    read = connection.getInputStream().read();
                    ++i;
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
            ByteBuffer reply = ByteBuffer.wrap(packetData);
            if (channelId != -1) {
                reply.putInt(8, channelId);
            }
            try {
                connection.getOutputStream().write(packetData);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void setNoChannel() {
            channelId = -1;
        }

    }

    public static class MockPacketListener implements PacketListener {

        @Override
        public void packetSent(FS4Channel channel, BasicPacket packet, ByteBuffer serializedForm) { }

        @Override
        public void packetReceived(FS4Channel channel, BasicPacket packet, ByteBuffer serializedForm) { }

    }

    public static class MockServer {
        public InetSocketAddress host;
        public Thread worker;
        public MockDispatch dispatch;

        public MockServer() throws IOException {
            ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            host = (InetSocketAddress) socket.getLocalSocketAddress();
            dispatch = new MockDispatch(socket);
            worker = new Thread(dispatch);
            worker.start();
        }

    }

    Backend backend;
    MockServer server;
    private Logger logger;
    private boolean initUseParent;
    FS4ResourcePool listeners;

    public static final byte[] PONG = new byte[] { 0, 0, 0, 32, 0, 0, 0, 221 - 256,
                                                   0,0,0,1, 0, 0, 0, 42, 0, 0, 0, 127, 0, 0, 0, 2, 0, 0, 0, 2, 0, 0, 0, 1, 0,
                                                   0, 0, 1 };

    @Before
    public void setUp() throws Exception {
        logger = Logger.getLogger(Backend.class.getName());
        initUseParent = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        listeners = new FS4ResourcePool(new Fs4Config(new Fs4Config.Builder()));

        server = new MockServer();
        backend = listeners.getBackend(server.host.getHostString(), server.host.getPort());
    }

    @After
    public void tearDown() throws Exception {
        listeners.deconstruct();
        server.dispatch.socket.close();
        if (server.dispatch.connection !=null) server.dispatch.connection.close();
        if (server.worker!=null) server.worker.join();
        if (logger !=null) logger.setUseParentHandlers(initUseParent);
    }

    @Test
    public void testBackend() throws IOException, InvalidChannelException {
        FS4Channel channel = backend.openChannel();
        Query q = new Query("/?query=a");
        BasicPacket[] b = null;
        int channelId = channel.getChannelId();
        server.dispatch.channelId = channelId;

        assertTrue(backend.sendPacket(QueryPacket.create(q), channelId));
        try {
            b = channel.receivePackets(1000, 1);
        } catch (ChannelTimeoutException e) {
            fail("Could not get packets from simulated backend.");
        }
        assertEquals(1, b.length);
        assertEquals(217, b[0].getCode());
        channel.close();
    }

    @Test
    public void testPinging() throws IOException, InvalidChannelException {
        FS4Channel channel = backend.openPingChannel();
        BasicPacket[] b = null;
        server.dispatch.setNoChannel();
        server.dispatch.packetData = PONG;

        assertTrue(channel.sendPacket(new PingPacket()));
        try {
            b = channel.receivePackets(1000, 1);
        } catch (ChannelTimeoutException e) {
            fail("Could not get packets from simulated backend.");
        }
        assertEquals(1, b.length);
        assertEquals(221, b[0].getCode());
        channel.close();
    }

    @Test
    public void requireStatistics() throws IOException, InvalidChannelException {
        FS4Channel channel = backend.openPingChannel();
        server.dispatch.channelId = -1;
        server.dispatch.packetData = PONG;

        assertTrue(channel.sendPacket(new PingPacket()));
        try {
            channel.receivePackets(1000, 1);
        } catch (ChannelTimeoutException e) {
            fail("Could not get packets from simulated backend.");
        }
        BackendStatistics stats = backend.getStatistics();
        assertEquals(1, stats.totalConnections());
    }

}
