// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;

/**
 * Wrapper for adding timeout to an HttpEntity instance.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class TimedHttpEntity implements HttpEntity {
    /**
     * The wrapped entity. Never null.
     */
    private final HttpEntity entity;
    private final long startTime;
    private final long timeout;

    public TimedHttpEntity(HttpEntity entity, long startTime, long timeout) {
        if (entity == null) {
            throw new IllegalArgumentException("TimedHttpEntity cannot be instantiated with null HttpEntity.");
        }
        this.entity = entity;
        this.startTime = startTime;
        this.timeout = timeout;
    }


    @Override
    public InputStream getContent() throws IOException, IllegalStateException {
        InputStream content = entity.getContent();
        if (content == null) {
            return null;
        } else {
            return new TimedStream(content, startTime, timeout);
        }
    }


    // START OF PURE FORWARDING METHODS
    @Override
    public void consumeContent() throws IOException {
        entity.consumeContent();
    }


    @Override
    public Header getContentEncoding() {
        return entity.getContentEncoding();
    }

    @Override
    public long getContentLength() {
        return entity.getContentLength();
    }

    @Override
    public Header getContentType() {
        return entity.getContentType();
    }

    @Override
    public boolean isChunked() {
        return entity.isChunked();
    }

    @Override
    public boolean isRepeatable() {
        return entity.isRepeatable();
    }

    @Override
    public boolean isStreaming() {
        return entity.isStreaming();
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        entity.writeTo(outstream);
    }
    // END OF PURE FORWARDING METHODS

}
