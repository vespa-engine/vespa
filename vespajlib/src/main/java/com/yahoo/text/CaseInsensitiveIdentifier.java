// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * @author baldersheim
 */
public class CaseInsensitiveIdentifier extends Identifier {

    private final Identifier original;

    public CaseInsensitiveIdentifier(String s) {
        this(new Utf8String(s));
    }
    public CaseInsensitiveIdentifier(byte [] utf8) {
        this(new Utf8Array(utf8));
    }
    public CaseInsensitiveIdentifier(AbstractUtf8Array utf8) {
        super(utf8.ascii7BitLowerCase());
        original = new Identifier(utf8);
    }
    public String toString() { return original.toString(); }

}
