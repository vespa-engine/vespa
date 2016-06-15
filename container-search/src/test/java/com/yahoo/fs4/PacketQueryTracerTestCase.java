// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.fs4;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.fs4.mplex.FS4Channel;
import com.yahoo.fs4.mplex.InvalidChannelException;
import com.yahoo.search.Query;

/**
 * Ensure hex dumping of packets seems to work.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class PacketQueryTracerTestCase {
    FS4Channel channel;
    BasicPacket packet;
    PacketListener tracer;

    static class MockChannel extends FS4Channel {

        @Override
        public void setQuery(Query query) {
            super.setQuery(query);
        }

        @Override
        public Query getQuery() {
            return super.getQuery();
        }

        @Override
        public Integer getChannelId() {
            return 1;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean sendPacket(BasicPacket packet)
                throws InvalidChannelException, IOException {
            return true;
        }

        @Override
        public BasicPacket[] receivePackets(long timeout, int packetCount)
                throws InvalidChannelException, ChannelTimeoutException {
            return null;
        }

        @Override
        public BasicPacket nextPacket(long timeout)
                throws InterruptedException, InvalidChannelException {
            return null;
        }

        @Override
        protected void addPacket(BasicPacket packet)
                throws InterruptedException, InvalidChannelException {
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public String toString() {
            return "MockChannel";
        }
    }

    @Before
    public void setUp() throws Exception {
        channel = new MockChannel();
        channel.setQuery(new Query("/?query=a&tracelevel=11"));
        packet = new PingPacket();
        tracer = new PacketQueryTracer();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public final void testPacketSent() throws IOException {
        byte[] simulatedPacket = new byte[] { 1, 2, 3 };
        tracer.packetReceived(channel, packet, ByteBuffer.wrap(simulatedPacket));
        StringWriter w = new StringWriter();
        channel.getQuery().getContext(false).render(w);
        assertTrue(w.getBuffer().toString().indexOf("PingPacket: 010203") != -1);
    }

    @Test
    public final void testPacketReceived() throws IOException {
        byte[] simulatedPacket = new byte[] { 1, 2, 3 };
        tracer.packetReceived(channel, packet, ByteBuffer.wrap(simulatedPacket));
        StringWriter w = new StringWriter();
        channel.getQuery().getContext(false).render(w);
        assertTrue(w.getBuffer().toString().indexOf("PingPacket: 010203") != -1);
    }

}
