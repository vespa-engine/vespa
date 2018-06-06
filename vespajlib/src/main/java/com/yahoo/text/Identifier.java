// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * This class is used to represent a legal identifier of [a-zA-Z_][a-zA-Z_0-9]*
 *
 * @author baldersheim
 */
public class Identifier extends Utf8Array {

    public Identifier(String s) {
        this(Utf8.toBytes(s));
    }
    public Identifier(AbstractUtf8Array utf8) {
        this(utf8.getBytes());
    }
    public Identifier(byte [] utf8) {
        super(verify(utf8));
    }
    private static byte [] verify(final byte [] utf8) {
        if (utf8.length > 0) {
            verifyFirst(utf8[0], utf8);
            for (int i=1; i < utf8.length; i++) {
                verifyAny(utf8[i], utf8);
            }
        }
        return utf8;

    }
    private static boolean verifyFirst(byte c, byte [] identifier) {
        if (!((c == '_') || ((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z')))) {
            throw new IllegalArgumentException("Illegal starting character '" + (char)c + "' of identifier '" + new Utf8String(new Utf8Array(identifier)).toString() +"'.");
        }
        return true;
    }
    private static boolean verifyAny(byte c, byte [] identifier) {
        if (!((c == '_') || ((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z')) || ((c >= '0') && (c <= '9')))) {
            throw new IllegalArgumentException("Illegal character '" + (char)c + "' of identifier '" + new Utf8String(new Utf8Array(identifier)).toString() +"'.");
        }
        return true;
    }
}
