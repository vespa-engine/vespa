// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * Forwards input from a source InputStream while making a copy of it into an outputstream.
 * Note that it also does read-ahead and copies up to 64K of data more than was used.
 *
 * @author arnej
 */
class TeeInputStream extends InputStream {
    final InputStream src;
    final OutputStream dst;

    static final int CAPACITY = 65536;

    byte[] buf = new byte[CAPACITY];
    int readPos = 0;
    int writePos = 0;

    private int inBuf() { return writePos - readPos; }

    private void fillBuf() throws IOException {
        if (readPos == writePos) {
            readPos = 0;
            writePos = 0;
        }
        if (readPos * 3 > CAPACITY) {
            int had = inBuf();
            System.arraycopy(buf, readPos, buf, 0, had);
            readPos = 0;
            writePos = had;
        }
        int wantToRead = CAPACITY - writePos;
        if (inBuf() > 0) {
            // if we have data already, do not block, read only what is available
            wantToRead = Math.min(wantToRead, src.available());
        }
        if (wantToRead > 0) {
            int got = src.read(buf, writePos, wantToRead);
            if (got > 0) {
                dst.write(buf, writePos, got);
                writePos += got;
            }
        }
    }

    /** Construct a Tee */
    public TeeInputStream(InputStream from, OutputStream to) {
        super();
        this.src = from;
        this.dst = to;
    }

    @Override
    public int available() throws IOException {
        return inBuf() + src.available();
    }

    @Override
    public void close() throws IOException {
        src.close();
        dst.close();
    }

    @Override
    public int read() throws IOException {
        fillBuf();
        if (inBuf() > 0) {
            int r = buf[readPos++];
            return r & 0xff;
        }
        return -1;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len <= 0) {
            return 0;
        }
        fillBuf();
        int had = inBuf();
        if (had > 0) {
            len = Math.min(len, had);
            System.arraycopy(buf, readPos, b, off, len);
            readPos += len;
            return len;
        }
        return -1;
    }

}
