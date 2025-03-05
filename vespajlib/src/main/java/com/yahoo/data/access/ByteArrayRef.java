package com.yahoo.data.access;

public record ByteArrayRef(byte[] data, int offset, int length) {
    public static final ByteArrayRef empty = new ByteArrayRef(new byte[]{}, 0, 0);
}
