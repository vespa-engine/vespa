// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import java.nio.channels.WritableByteChannel;
import java.nio.ByteBuffer;
import java.io.IOException;
import com.yahoo.io.GrowableBufferOutputStream;


/**
 * Tests the GrowableBufferOutputStream
 *
 * @author  <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 */
public class GrowableBufferOutputStreamTestCase extends junit.framework.TestCase {
    private byte[] testData;

    static class DummyWritableByteChannel implements WritableByteChannel {
        private ByteBuffer buffer;

        public DummyWritableByteChannel(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        public int write(ByteBuffer src) throws IOException {
            int written = Math.min(src.remaining(), buffer.remaining());

            if (buffer.remaining() < src.remaining()) {
                ByteBuffer tmp = src.slice();

                tmp.limit(written);
                src.position(src.position() + written);
            } else {
                buffer.put(src);
            }
            return written;
        }

        public boolean isOpen() {
            return true;
        }

        public void close() throws IOException {}
    }

    public GrowableBufferOutputStreamTestCase(String name) {
        super(name);
    }

    public void setUp() {
        testData = new byte[100];
        for (int i = 0; i < 100; ++i) {
            testData[i] = (byte) i;
        }
    }

    public void testSimple() throws IOException {
        GrowableBufferOutputStream g = new GrowableBufferOutputStream(10, 5);

        g.write(testData, 0, 100);
        g.flush();
        assertEquals(10, g.numWritableBuffers());
        assertEquals(100, g.writableSize());

        ByteBuffer sink = ByteBuffer.allocate(60);
        DummyWritableByteChannel channel = new DummyWritableByteChannel(sink);
        int written = g.channelWrite(channel);

        assertEquals(60, written);
        assertEquals(60, sink.position());
        assertEquals(40, g.writableSize());

        // there should be 4 buffers left now
        assertEquals(4, g.numWritableBuffers());

        // ensure that we got what we expected
        for (int i = 0; i < 60; ++i) {
            if (((int) sink.get(i)) != i) {
                fail();
            }
        }

        // then we write more data
        g.write(testData, 0, 100);
        g.flush();
        assertEquals(140, g.writableSize());

        // ...which implies that we should now have 14 writable buffers
        assertEquals(14, g.numWritableBuffers());

        // reset the sink so it can consume more data
        sink.clear();

        // then write more to the DummyWritableByteChannel
        written = g.channelWrite(channel);
        assertEquals(60, written);
        assertEquals(60, sink.position());
        assertEquals(80, g.writableSize());

        // now there should be 8 buffers
        assertEquals(8, g.numWritableBuffers());

        // ensure that we got what we expected
        for (int i = 0; i < 60; ++i) {
            int val = (int) sink.get(i);
            int expected = (i + 60) % 100;

            if (val != expected) {
                fail("Value was " + val + " and not " + i);
            }
        }

        // when we clear there should be no buffers
        g.clear();
        assertEquals(0, g.numWritableBuffers());
        assertEquals(0, g.writableSize());

        // ditto after flush after clear
        g.flush();
        assertEquals(0, g.numWritableBuffers());

        // flush the cache too
        g.clearAll();
        assertEquals(0, g.numWritableBuffers());
    }
}
