// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.client;

import com.ning.http.client.Body;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
class ChunkedRequestBody implements Body {

    private final ChunkedRequestContent content;
    private ByteBuffer currentBuf;

    public ChunkedRequestBody(ChunkedRequestContent content) {
        this.content = content;
    }

    @Override
    public long getContentLength() {
        return -1; // unknown
    }

    @Override
    public long read(ByteBuffer dst) throws IOException {
        if (content.isEndOfInput()) {
            return -1;
        }
        if (currentBuf == null || currentBuf.remaining() == 0) {
            currentBuf = content.nextChunk();
        }
        if (currentBuf == null) {
            return 0;
        }
        int len = Math.min(currentBuf.remaining(), dst.remaining());
        for (int i = 0; i < len; ++i) {
            dst.put(currentBuf.get());
        }
        return len;
    }

    @Override
    public void close() throws IOException {

    }
}
