// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import java.io.IOException;
import java.io.InputStream;

/**
 * A stream which throws a TimeoutException if query timeout has been reached.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class TimedStream extends InputStream {

    /**
     * A time barrier value, the point in time from which on read operations will cause an exception.
     */
    private final long limit;

    /**
     * A wrapped InputStream instance.
     */
    private final InputStream content;

    /**
     * Wrap an InputStream to make read operations potentially fire off
     * TimeoutException.
     *
     * <p>Typical use would be<br>
     * <code>new TimedStream(httpEntity.getContent(), query.getStartTime(), query.getTimeout())</code>
     *
     * @param content
     *                the InputStream to wrap
     * @param startTime
     *                start time of query
     * @param timeout
     *                how long the query is allowed to run
     */
    public TimedStream(InputStream content, long startTime, long timeout) {
        if (content == null) {
            throw new IllegalArgumentException("Cannot instantiate TimedStream with null InputStream");
        }
        this.content = content;
        // The reasion for doing it in here instead of outside the constructor
        // is this makes the usage of the class more intuitive IMHO
        this.limit = startTime + timeout;
    }

    private void checkTime(String message) {
        if (System.currentTimeMillis() >= limit) {
            throw new TimeoutException(message);
        }
    }

    // START FORWARDING METHODS:
    // All methods below are forwarding methods to the contained stream, where
    // some do a timeout check.
    @Override
    public int read() throws IOException {
        int data = content.read();
        checkTime("Timed out during read().");
        return data;
    }

    @Override
    public int available() throws IOException {
        return content.available();
    }

    @Override
    public void close() throws IOException {
        content.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
        content.mark(readlimit);
    }

    @Override
    public boolean markSupported() {
        return content.markSupported();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int length = content.read(b, off, len);
        checkTime("Timed out during read(byte[], int, int)");
        return length;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int length = content.read(b);
        checkTime("Timed out during read(byte[])");
        return length;
    }

    @Override
    public synchronized void reset() throws IOException {
        content.reset();
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = content.skip(n);
        checkTime("Timed out during skip(long)");
        return skipped;
    }
    // END FORWARDING METHODS

}
