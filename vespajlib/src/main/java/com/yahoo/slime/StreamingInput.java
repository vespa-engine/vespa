// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class StreamingInput implements JsonInput {

    private final InputStream source;
    private final byte[] buffer = new byte[8192];
    private int bufferPos;
    private int bufferLen;
    private String failReason;
    private final ByteArrayOutputStream consumed = new ByteArrayOutputStream();

    StreamingInput(InputStream source) {
        this.source = source;
    }

    @Override
    public byte getByte() {
        if (eof()) { fail("underflow"); return 0; }
        byte b = buffer[bufferPos++];
        consumed.write(b);
        return b;
    }

    @Override
    public boolean eof() {
        if (bufferPos < bufferLen) return false;
        fillBuffer();
        return bufferPos >= bufferLen;
    }

    private void fillBuffer() {
        try {
            bufferLen = source.read(buffer);
            if (bufferLen < 0) bufferLen = 0;
            bufferPos = 0;
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    @Override public void fail(String reason) { if (failReason == null) failReason = reason; }
    @Override public boolean failed() { return failReason != null; }
    @Override public String getErrorMessage() { return failReason; }
    @Override public byte[] getOffending() { return consumed.toByteArray(); }

}
