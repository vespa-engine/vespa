// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.compress;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author bjorncs
 */
public class ZstdOuputStream extends OutputStream {

    private final ZstdCompressor compressor = new ZstdCompressor();

    public static final int DEFAULT_INPUT_BUFFER_SIZE = 8*1024;

    private final OutputStream out;
    private final byte[] inputBuffer;
    private final byte[] outputBuffer;
    private int inputPosition = 0;
    private boolean isClosed = false;

    public ZstdOuputStream(OutputStream out, int inputBufferSize) {
        this.out = out;
        this.inputBuffer = new byte[inputBufferSize];
        this.outputBuffer = new byte[ZstdCompressor.getMaxCompressedLength(inputBufferSize)];
    }

    public ZstdOuputStream(OutputStream out) {
        this(out, DEFAULT_INPUT_BUFFER_SIZE);
    }

    @Override
    public void write(int b) throws IOException {
        throwIfClosed();
        inputBuffer[inputPosition++] = (byte) b;
        flushIfFull();
    }

    @Override
    public void write(byte[] b) throws IOException {
        throwIfClosed();
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        throwIfClosed();
        int end = off + len;
        while (off < end) {
            int copyLength = Math.min(end - off, inputBuffer.length - inputPosition);
            System.arraycopy(b, off, inputBuffer, inputPosition, copyLength);
            off += copyLength;
            inputPosition += copyLength;
            flushIfFull();
        }
    }

    @Override
    public void flush() throws IOException {
        flushInternal();
        out.flush();
    }

    @Override
    public void close() throws IOException {
        throwIfClosed();
        flush();
        out.close();
        isClosed = true;
    }

    private void flushInternal() throws IOException {
        throwIfClosed();
        int compressedLength = compressor.compress(inputBuffer, 0, inputPosition, outputBuffer, 0, outputBuffer.length);
        out.write(outputBuffer, 0, compressedLength);
        inputPosition = 0;
    }

    private void flushIfFull() throws IOException {
        if (inputPosition == inputBuffer.length) {
            flushInternal();
        }
    }

    private void throwIfClosed() {
        if (isClosed) throw new IllegalArgumentException("Output stream is already closed");
    }
}
