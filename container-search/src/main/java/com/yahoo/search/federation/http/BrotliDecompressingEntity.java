// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.http;

import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;
import org.brotli.dec.BrotliInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/**
 * Used by HTTPSearcher when talking to services returning compressed content.
 *
 * @author <a href="mailto:mainak@yahoo-inc.com">Mainak Mandal</a>
 */
public class BrotliDecompressingEntity extends HttpEntityWrapper {

    private static class Resources {

        byte [] buffer;
        int total;

        Resources() {
            total = 0;
            buffer = new byte[65536];
        }
        void drain(InputStream brotliStream) throws IOException {
            int numRead = brotliStream.read(buffer, total, buffer.length);
            while (numRead != -1) {
                total += numRead;
                if ((total + 65536) > buffer.length) {
                    buffer = Arrays.copyOf(buffer, buffer.length + numRead);
                }
                numRead = brotliStream.read(buffer, total, buffer.length - total);
            }
        }

    }

    private final Resources resources = new Resources();

    public BrotliDecompressingEntity(final HttpEntity entity) throws IllegalStateException, IOException {
        super(entity);
        BrotliInputStream br = new BrotliInputStream(entity.getContent());
        InputStream brotliStream = new BufferedInputStream(br);
        //GZIPInputStream gz = new GZIPInputStream(entity.getContent());
        //InputStream zipStream = new BufferedInputStream(gz);
        try {
            resources.drain(brotliStream);
        } catch (IOException e) {
            throw e;
        } finally {
            brotliStream.close();
        }
    }

    @Override
    public InputStream getContent() throws IOException, IllegalStateException {

        final ByteBuffer buff = ByteBuffer.wrap(resources.buffer, 0, resources.total);
        return new InputStream() {

            @Override
            public int available() throws IOException {
                return buff.remaining();
            }

            @Override
            public int read() throws IOException {
                if (buff.hasRemaining())
                    return buff.get() & 0xFF;

                return -1;
            }

            @Override
            public int read(byte[] b) throws IOException {
                if (!buff.hasRemaining())
                    return -1;

                int len = b.length;
                if (len > buff.remaining())
                    len = buff.remaining();
                buff.get(b, 0, len);
                return len;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (!buff.hasRemaining())
                    return -1;

                if (len > buff.remaining())
                    len = buff.remaining();
                buff.get(b, off, len);
                return len;
            }

            @Override
            public long skip(long n) throws IOException {
                if (!buff.hasRemaining())
                    return -1;

                if (n > buff.remaining())
                    n = buff.remaining();

                buff.position(buff.position() + (int) n);
                return n;
            }
        };
    }

    @Override
    public long getContentLength() {
        return resources.total;
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        outstream.write(resources.buffer, 0, resources.total);
    }

}
