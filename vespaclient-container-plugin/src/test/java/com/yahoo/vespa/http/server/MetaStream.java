// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.text.Utf8;

import java.io.ByteArrayInputStream;

/**
 * Stream with extra data outside the actual input stream.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public final class MetaStream extends ByteArrayInputStream {

    private byte[] operations;
    int i;

    public MetaStream(byte[] buf) {
        super(createPayload(buf));
        this.operations = buf;
        i = 0;
    }

    private static final byte[] createPayload(byte[] buf) {
        StringBuilder bu = new StringBuilder();
        for (int i = 0; i < buf.length; i++) {
            bu.append("id:banana:banana::doc1 0\n");
        }
        return Utf8.toBytes(bu.toString());
    }

    public byte getNextOperation() {
        if (i >= operations.length) {
            return 0;
        }
        return operations[i++];
    }

}
