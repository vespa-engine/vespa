// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class ChunkReader {

    private static final Pattern CONTENT_LENGTH = Pattern.compile(".+^content-length: (\\d+)$.*",
                                                                  Pattern.CASE_INSENSITIVE |
                                                                  Pattern.MULTILINE |
                                                                  Pattern.DOTALL);
    private static final Pattern CHUNKED_ENCODING = Pattern.compile(".+^transfer-encoding: chunked$.*",
                                                                    Pattern.CASE_INSENSITIVE |
                                                                    Pattern.MULTILINE |
                                                                    Pattern.DOTALL);
    private final InputStream in;
    private StringBuilder reading = new StringBuilder();
    private boolean readingHeader = true;

    public ChunkReader(InputStream in) {
        this.in = in;
    }

    public boolean isEndOfContent() throws IOException {
        if (in.available() != 0) {
            StringBuilder sb = new StringBuilder();
            sb.append(in.available()).append(": ");
            for(int c = in.read(); c != -1; c = in.read()) {
                sb.append('\'');
                sb.append(c);
                sb.append("' ");
            }
            throw new IllegalStateException("This is not the end '" + sb.toString());
        }
        return in.available() == 0;
    }

    public String readChunk() throws IOException {
        while (true) {
            String ret = removeNextChunk();
            if (ret != null) {
                return ret;
            }
            readFromStream();
        }
    }

    private String readContent(int length) throws IOException {
        while (reading.length() < length) {
            readFromStream();
        }
        return splitReadBuffer(length);
    }

    private void readFromStream() throws IOException {
        byte[] buf = new byte[4096];
        try {
            while (!Thread.currentThread().isInterrupted()) {
                int len = in.read(buf, 0, buf.length);
                if (len < 0) {
                    throw new IOException("Socket is closed.");
                }
                if (len > 0) {
                    reading.append(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(buf, 0, len)));
                    break;
                }
                Thread.sleep(10);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String removeNextChunk() throws IOException {
        if (readingHeader) {
            int pos = reading.indexOf("\r\n\r\n");
            if (pos < 0) {
                return null;
            }
            String ret = splitReadBuffer(pos + 4);
            Matcher m = CONTENT_LENGTH.matcher(ret);
            if (m.matches()) {
                ret += readContent(Integer.valueOf(m.group(1)));
            }
            readingHeader = !CHUNKED_ENCODING.matcher(ret).matches();
            return ret;
        } else if (reading.indexOf("0\r\n") == 0) {
            int pos = reading.indexOf("\r\n\r\n", 1);
            if (pos < 0) {
                return null;
            }
            readingHeader = true;
            return splitReadBuffer(pos + 4);
        } else {
            int pos = reading.indexOf("\r\n");
            if (pos < 0) {
                return null;
            }
            pos = reading.indexOf("\r\n", pos + 2);
            if (pos < 0) {
                return null;
            }
            return splitReadBuffer(pos + 2);
        }
    }

    private String splitReadBuffer(int pos) {
        String ret = reading.substring(0, pos);
        if (pos < reading.length()) {
            reading = new StringBuilder(reading.substring(pos));
        } else {
            reading = new StringBuilder();
        }
        return ret;
    }
}
