// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.sync;

import com.yahoo.compress.ZstdCompressor;

import java.io.IOException;
import java.io.InputStream;

/**
 * InputStream that outputs given InputStream compressed with the ZStandard.
 *
 * @author freva
 */
public class ZstdCompressingInputStream extends InputStream {

    public static final int DEFAULT_INPUT_BUFFER_SIZE = 8 * 1024;
    static final ZstdCompressor compressor = new ZstdCompressor();

    private final InputStream is;
    private final byte[] inputBuffer;
    private final byte[] outputBuffer;

    private int outputPosition = 0;
    private int outputLength = 0;
    private boolean isClosed = false;

    public ZstdCompressingInputStream(InputStream is, int inputBufferSize) {
        this.is = is;
        this.inputBuffer = new byte[inputBufferSize];
        this.outputBuffer = new byte[ZstdCompressor.getMaxCompressedLength(inputBufferSize)];
    }

    public ZstdCompressingInputStream(InputStream is) {
        this(is, DEFAULT_INPUT_BUFFER_SIZE);
    }

    @Override
    public int read() throws IOException {
        throwIfClosed();

        if (outputPosition >= outputLength) {
            int readLength = is.read(inputBuffer);
            if (readLength == -1)
                return -1;

            outputLength = compressor.compress(inputBuffer, 0, readLength, outputBuffer, 0, outputBuffer.length);
            outputPosition = 0;
        }

        return Byte.toUnsignedInt(outputBuffer[outputPosition++]);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int first = read();
        if (first == -1) return -1;

        b[off++] = (byte) first;
        len = Math.min(Math.min(len, outputLength - outputPosition), b.length - off);
        System.arraycopy(outputBuffer, outputPosition, b, off, len);
        outputPosition += len;
        return len + 1;
    }

    @Override
    public void close() throws IOException {
        throwIfClosed();
        is.close();
        isClosed = true;
    }

    private void throwIfClosed() {
        if (isClosed) throw new IllegalArgumentException("Input stream is already closed");
    }
}
