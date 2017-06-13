// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server.util;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.23
 */
public class ByteLimitedInputStreamTestCase {

    private static ByteLimitedInputStream create(byte[] source, int limit) {
        if (limit > source.length) {
            throw new IllegalArgumentException("Limit is greater than length of source buffer.");
        }
        InputStream wrappedStream = new ByteArrayInputStream(source);
        return new ByteLimitedInputStream(wrappedStream, limit);
    }

    @Test
    public void requireThatBasicsWork() throws IOException {
        ByteLimitedInputStream stream = create("abcdefghijklmnopqr".getBytes(StandardCharsets.US_ASCII), 9);

        assertThat(stream.available(), is(9));
        assertThat(stream.read(), is(97));
        assertThat(stream.available(), is(8));
        assertThat(stream.read(), is(98));
        assertThat(stream.available(), is(7));
        assertThat(stream.read(), is(99));
        assertThat(stream.available(), is(6));
        assertThat(stream.read(), is(100));
        assertThat(stream.available(), is(5));
        assertThat(stream.read(), is(101));
        assertThat(stream.available(), is(4));
        assertThat(stream.read(), is(102));
        assertThat(stream.available(), is(3));
        assertThat(stream.read(), is(103));
        assertThat(stream.available(), is(2));
        assertThat(stream.read(), is(104));
        assertThat(stream.available(), is(1));
        assertThat(stream.read(), is(105));
        assertThat(stream.available(), is(0));
        assertThat(stream.read(), is(-1));
        assertThat(stream.available(), is(0));
        assertThat(stream.read(), is(-1));
        assertThat(stream.available(), is(0));
        assertThat(stream.read(), is(-1));
        assertThat(stream.available(), is(0));
        assertThat(stream.read(), is(-1));
        assertThat(stream.available(), is(0));
        assertThat(stream.read(), is(-1));
        assertThat(stream.available(), is(0));
    }

    @Test
    public void requireThatChunkedReadWorks() throws IOException {
        ByteLimitedInputStream stream = create("abcdefghijklmnopqr".getBytes(StandardCharsets.US_ASCII), 9);

        assertThat(stream.available(), is(9));
        byte[] toBuf = new byte[4];
        assertThat(stream.read(toBuf), is(4));
        assertThat(toBuf[0], is((byte) 97));
        assertThat(toBuf[1], is((byte) 98));
        assertThat(toBuf[2], is((byte) 99));
        assertThat(toBuf[3], is((byte) 100));
        assertThat(stream.available(), is(5));

        assertThat(stream.read(toBuf), is(4));
        assertThat(toBuf[0], is((byte) 101));
        assertThat(toBuf[1], is((byte) 102));
        assertThat(toBuf[2], is((byte) 103));
        assertThat(toBuf[3], is((byte) 104));
        assertThat(stream.available(), is(1));

        assertThat(stream.read(toBuf), is(1));
        assertThat(toBuf[0], is((byte) 105));
        assertThat(stream.available(), is(0));

        assertThat(stream.read(toBuf), is(-1));
        assertThat(stream.available(), is(0));
    }

}
