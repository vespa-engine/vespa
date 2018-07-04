// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Simon Thoresen Hult
 */
public class UnsafeContentInputStreamTestCase {

    @Test
    public void requireThatBytesCanBeRead() throws IOException {
        BufferedContentChannel channel = new BufferedContentChannel();
        FastContentWriter writer = new FastContentWriter(channel);
        writer.write("Hello ");
        writer.write("World!");
        writer.close();

        BufferedReader reader = asBufferedReader(channel);
        assertEquals("Hello World!", reader.readLine());
        assertNull(reader.readLine());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void requireThatCompletionsAreCalledWithDeprecatedContentWriter() throws IOException {
        BufferedContentChannel channel = new BufferedContentChannel();
        FastContentWriter writer = new FastContentWriter(channel);
        writer.write("foo");
        writer.close();

        InputStream stream = asInputStream(channel);
        assertEquals('f', stream.read());
        assertEquals('o', stream.read());
        assertEquals('o', stream.read());
        assertEquals(-1, stream.read());
        assertTrue(writer.isDone());
    }

    @Test
    public void requireThatCompletionsAreCalled() throws IOException {
        BufferedContentChannel channel = new BufferedContentChannel();
        FastContentWriter writer = new FastContentWriter(channel);
        writer.write("foo");
        writer.close();

        InputStream stream = asInputStream(channel);
        assertEquals('f', stream.read());
        assertEquals('o', stream.read());
        assertEquals('o', stream.read());
        assertEquals(-1, stream.read());
        assertTrue(writer.isDone());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void requireThatCloseDrainsStreamWithDeprecatedContentWriter() {
        BufferedContentChannel channel = new BufferedContentChannel();
        FastContentWriter writer = new FastContentWriter(channel);
        writer.write("foo");
        writer.close();

        asInputStream(channel).close();
        assertTrue(writer.isDone());
    }

    @Test
    public void requireThatCloseDrainsStream() {
        BufferedContentChannel channel = new BufferedContentChannel();
        FastContentWriter writer = new FastContentWriter(channel);
        writer.write("foo");
        writer.close();

        asInputStream(channel).close();
        assertTrue(writer.isDone());
    }

    @Test
    public void requireThatAvailableIsNotBlocking() throws IOException {
        BufferedContentChannel channel = new BufferedContentChannel();
        InputStream stream = asInputStream(channel);
        assertEquals(0, stream.available());
        channel.write(ByteBuffer.wrap(new byte[] { 6, 9 }), null);
        assertTrue(stream.available() > 0);
        assertEquals(6, stream.read());
        assertTrue(stream.available() > 0);
        assertEquals(9, stream.read());
        assertEquals(0, stream.available());
        channel.close(null);
        assertEquals(-1, stream.read());
        assertEquals(0, stream.available());
    }

    @Test
    public void requireThatReadLargeArrayIsNotBlocking() throws IOException {
        BufferedContentChannel channel = new BufferedContentChannel();
        InputStream stream = asInputStream(channel);
        assertEquals(0, stream.available());
        channel.write(ByteBuffer.wrap(new byte[] { 6, 9 }), null);
        assertTrue(stream.available() > 0);
        byte[] buf = new byte[69];
        assertEquals(2, stream.read(buf));
        assertEquals(6, buf[0]);
        assertEquals(9, buf[1]);
        assertEquals(0, stream.available());
        channel.close(null);
        assertEquals(-1, stream.read(buf));
        assertEquals(0, stream.available());
    }

    @Test
    public void requireThatAllByteValuesCanBeRead() throws IOException {
        ReadableContentChannel content = new ReadableContentChannel();
        InputStream in = new UnsafeContentInputStream(content);
        for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; ++i) {
            content.write(ByteBuffer.wrap(new byte[] { (byte)i }), null);
            assertEquals(i, (byte)in.read());
        }
    }

    private static BufferedReader asBufferedReader(BufferedContentChannel channel) {
        return new BufferedReader(new InputStreamReader(asInputStream(channel)));
    }

    private static UnsafeContentInputStream asInputStream(BufferedContentChannel channel) {
        return new UnsafeContentInputStream(channel.toReadable());
    }
}
