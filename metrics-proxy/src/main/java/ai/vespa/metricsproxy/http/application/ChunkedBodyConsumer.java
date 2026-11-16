// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.nio.entity.AbstractBinAsyncEntityConsumer;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects a response body as the chunks it arrives in, and returns it as a stream over them.
 * Unlike the simple consumers, it holds the body only once: no buffer which grows by doubling,
 * no copy into one array when the body is complete, and no decoding into a String.
 *
 * @author andreer
 */
class ChunkedBodyConsumer extends AbstractBinAsyncEntityConsumer<InputStream> {

    private List<byte[]> chunks = new ArrayList<>();

    @Override
    protected void streamStart(ContentType contentType) { }

    @Override
    protected int capacityIncrement() { return Integer.MAX_VALUE; }

    @Override
    protected void data(ByteBuffer src, boolean endOfStream) {
        if ( ! src.hasRemaining()) return;
        byte[] chunk = new byte[src.remaining()]; // src is reused by the I/O reactor
        src.get(chunk);
        chunks.add(chunk);
    }

    @Override
    protected InputStream generateContent() {
        List<InputStream> streams = new ArrayList<>(chunks.size());
        for (byte[] chunk : chunks) streams.add(new ByteArrayInputStream(chunk));
        chunks = null; // Owned by the returned stream from now on
        return new SequenceInputStream(Collections.enumeration(streams));
    }

    @Override
    public void releaseResources() { chunks = null; }

}
