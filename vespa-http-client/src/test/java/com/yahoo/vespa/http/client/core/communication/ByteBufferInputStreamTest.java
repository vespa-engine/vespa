// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.core.communication.ByteBufferInputStream;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.20
 */
public class ByteBufferInputStreamTest {

    private static ByteBuffer[] getAbcde() {
        ByteBuffer[] buffers = new ByteBuffer[5];
        buffers[0] = ByteBuffer.wrap("a".getBytes(StandardCharsets.UTF_8));
        buffers[1] = ByteBuffer.wrap("b".getBytes(StandardCharsets.UTF_8));
        buffers[2] = ByteBuffer.wrap("c".getBytes(StandardCharsets.UTF_8));
        buffers[3] = ByteBuffer.wrap("d".getBytes(StandardCharsets.UTF_8));
        buffers[4] = ByteBuffer.wrap("e".getBytes(StandardCharsets.UTF_8));
        return buffers;
    }

    @Test
    public void requireThatExhaustedBufferWorks() throws IOException {
        ByteBuffer[] buffers = getAbcde();
        buffers[2].get();
        ByteBufferInputStream in = new ByteBufferInputStream(buffers);

        byte[] out = new byte[100];
        int pos = 0;

        final int GUARD = 1000;
        int i;
        for (i = 0; i < GUARD; i++) {
            int r = in.read();
            if (r == -1) {
                break;
            }
            out[pos] = (byte) (0xFF & r);
            ++pos;
        }
        assertTrue(i < GUARD);
        assertThat(pos, is(4));

        String outString = new String(out, 0, pos, StandardCharsets.UTF_8);
        assertThat(outString, equalTo("abde"));

    }

    @Test
    public void requireThatBulkReadWorks() throws IOException {
        ByteBuffer[] buffers = getAbcde();
        ByteBufferInputStream in = new ByteBufferInputStream(buffers);

        byte[] out = new byte[100];
        int pos = 0;

        final int GUARD = 1000;
        int i;
        for (i = 0; i < GUARD; i++) {
            int numReadNow;
            if (i == 0) {
                numReadNow = in.read(out);
            } else {
                numReadNow = in.read(out, pos, (out.length - pos));
            }
            if (numReadNow == -1) {
                break;
            }
            pos += numReadNow;
        }
        assertTrue(i < GUARD);
        assertThat(pos, is(5));

        String outString = new String(out, 0, pos, StandardCharsets.UTF_8);
        assertThat(outString, equalTo("abcde"));
    }

    @Test
    public void requireThatSingleByteReadWorks() throws IOException {
        ByteBuffer[] buffers = getAbcde();
        ByteBufferInputStream in = new ByteBufferInputStream(buffers);

        byte[] out = new byte[100];
        int pos = 0;

        final int GUARD = 1000;
        int i;
        for (i = 0; i < GUARD; i++) {
            int r = in.read();
            if (r == -1) {
                break;
            }
            out[pos] = (byte) (0xFF & r);
            ++pos;
        }
        assertTrue(i < GUARD);
        assertThat(pos, is(5));

        String outString = new String(out, 0, pos, StandardCharsets.UTF_8);
        assertThat(outString, equalTo("abcde"));
    }

    @Test
    public void requireThatMarkIsNotSupported() throws IOException {
        ByteBuffer[] buffers = getAbcde();
        ByteBufferInputStream in = new ByteBufferInputStream(buffers);
        assertThat(in.markSupported(), is(false));
        in.mark(0);  //a no-op
    }

    @Test(expected = IOException.class)
    public void requireThatResetFails() throws IOException {
        ByteBuffer[] buffers = getAbcde();
        ByteBufferInputStream in = new ByteBufferInputStream(buffers);
        in.reset();
    }

    @Test(expected = IOException.class)
    public void requireThatSkipFails() throws IOException {
        ByteBuffer[] buffers = getAbcde();
        ByteBufferInputStream in = new ByteBufferInputStream(buffers);
        in.skip(1L);
    }
}
