// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io;

import com.yahoo.text.GenericWriter;
import com.yahoo.text.AbstractUtf8Array;
import com.yahoo.text.Utf8;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * Base class for writers needing to accept binary data.
 *
 * @author Steinar Knutsen
 * @author baldersheim
 */
public abstract class AbstractByteWriter extends GenericWriter implements WritableByteTransmitter {

    protected final CharsetEncoder encoder;
    protected final BufferChain buffer;
    protected final CharBuffer charBuffer = CharBuffer.allocate(2);

    protected AbstractByteWriter(final CharsetEncoder encoder) {
        this.encoder = encoder;
        buffer = new BufferChain(this);
    }

    /** Returns the charset this encodes its output in */
    public Charset getEncoding() {
        return encoder.charset();
    }

    @Override
    public GenericWriter write(AbstractUtf8Array v) throws java.io.IOException {
        buffer.append(v);
        return this;
    }

    @Override
    public GenericWriter write(long v) throws java.io.IOException {
        buffer.append(Utf8.toAsciiBytes(v));
        return this;
    }

    /**
     * Do note, if writing the first character of a surrogate pair, the next
     * character written must be the second part of the pair. If this is not the
     * case, the surrogate will be omitted from output.
     */
    @Override
    public void write(int v) throws java.io.IOException {
        char c = (char) v;
        if (Character.isSurrogate(c)) {
            charBuffer.append(c);
            if (!charBuffer.hasRemaining()) {
                charBuffer.flip();
                buffer.append(charBuffer, encoder);
                charBuffer.clear();
            }
        } else {
            charBuffer.clear(); // to nuke misplaced singleton surrogates
            charBuffer.append((char) v);
            charBuffer.flip();
            buffer.append(charBuffer, encoder);
            charBuffer.clear();
        }
    }

    @Override
    public GenericWriter write(double v) throws java.io.IOException {
        buffer.append(Utf8.toBytes(String.valueOf(v)));
        return this;
    }
    @Override
    public GenericWriter write(float v) throws java.io.IOException {
        buffer.append(Utf8.toBytes(String.valueOf(v)));
        return this;
    }

    @Override
    public GenericWriter write(short v) throws java.io.IOException {
        buffer.append(Utf8.toAsciiBytes(v));
        return this;
    }
    @Override
    public GenericWriter write(boolean v) throws java.io.IOException {
        buffer.append(Utf8.toAsciiBytes(v));
        return this;
    }

    @Override
    public void write(final char[] cbuf, final int offset, final int len)
            throws java.io.IOException {
        final CharBuffer in = CharBuffer.wrap(cbuf, offset, len);
        buffer.append(in, encoder);
    }

    public void append(final ByteBuffer alreadyEncoded)
            throws java.io.IOException {
        buffer.append(alreadyEncoded);
    }

    public void append(final byte alreadyEncoded) throws java.io.IOException {
        buffer.append(alreadyEncoded);
    }

    public void append(final byte[] alreadyEncoded) throws java.io.IOException {
        buffer.append(alreadyEncoded);
    }

    public void append(final byte[] alreadyEncoded, final int offset,
            final int length) throws java.io.IOException {
        buffer.append(alreadyEncoded, offset, length);
    }

    /**
     * Return the number of bytes this writer will produce for the underlying
     * layer. That is, it sums the length of the raw bytes received and the
     * number of bytes in the written strings after encoding.
     *
     * @return the number of bytes appended to this writer
     */
    public long appended() {
        return buffer.appended();
    }
}
