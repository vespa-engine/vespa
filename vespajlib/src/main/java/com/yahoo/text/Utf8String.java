// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * String with Utf8 backing.
 *
 * @author baldersheim
 */
public final class Utf8String extends Utf8Array implements CharSequence {

    private final String s;

    /**
     * This will construct a utf8 backing of the given string.
     *
     * @param str The string that will be utf8 encoded
     */
    public Utf8String(String str) {
        super(Utf8.toBytes(str));
        s = str;
    }

    /**
     * This will create a string based on the utf8 sequence.
     *
     * @param utf8 The backing array
     */
    public Utf8String(AbstractUtf8Array utf8) {
        super(utf8.getBytes(), utf8.getByteOffset(), utf8.getByteLength());
        s = utf8.toString();
    }

    @Override
    public char charAt(int index) {
        return toString().charAt(index);
    }
    @Override
    public int length() {
        return toString().length();
    }
    @Override
    public CharSequence subSequence(int start, int end) {
        return toString().subSequence(start, end);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Utf8String) {
            return s.equals(o.toString());
        }
        return super.equals(o);
    }

    @Override
    public String toString() {
        return s;
    }

}
