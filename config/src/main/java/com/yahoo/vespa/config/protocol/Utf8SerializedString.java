// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import com.fasterxml.jackson.core.SerializableString;
import com.yahoo.text.Utf8Array;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Wraps utf8array as a {@link com.fasterxml.jackson.core.SerializableString} to avoid extra copy.
 *
 * @author lulf
 * @since 5.17
 */
public class Utf8SerializedString implements SerializableString {
    private final Utf8Array value;
    public Utf8SerializedString(Utf8Array value) {
        this.value = value;
    }

    @Override
    public String getValue() {
        return value.toString();
    }

    @Override
    public int charLength() {
        return value.getByteLength();
    }

    @Override
    public char[] asQuotedChars() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] asUnquotedUTF8() {
        return value.getBytes();
    }

    @Override
    public byte[] asQuotedUTF8() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int appendQuotedUTF8(byte[] buffer, int offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int appendQuoted(char[] buffer, int offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int appendUnquotedUTF8(byte[] buffer, int offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int appendUnquoted(char[] buffer, int offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int writeQuotedUTF8(OutputStream out) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int writeUnquotedUTF8(OutputStream out) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int putQuotedUTF8(ByteBuffer buffer) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int putUnquotedUTF8(ByteBuffer out) throws IOException {
        throw new UnsupportedOperationException();
    }
}
