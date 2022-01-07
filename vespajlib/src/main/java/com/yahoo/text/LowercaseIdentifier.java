// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * @author baldersheim
 */
public class LowercaseIdentifier extends Identifier {

    public LowercaseIdentifier(String s) {
        this(Utf8.toBytes(s));
    }
    public LowercaseIdentifier(AbstractUtf8Array utf8) {
        this(utf8.getBytes());
    }
    public LowercaseIdentifier(byte [] utf8) {
        super(verify(utf8));
    }
    private static byte [] verify(final byte [] utf8) {
        for (int i=0; i < utf8.length; i++) {
            verifyAny(utf8[i], utf8);
        }

        return utf8;

    }
    private static boolean verifyAny(byte c, byte [] identifier) {
        if ((c >= 'A') && (c <= 'Z')) {
            throw new IllegalArgumentException("Illegal uppercase character '" + (char)c + "' of identifier '" + new Utf8String(new Utf8Array(identifier)).toString() +"'.");
        }
        return true;
    }

}
