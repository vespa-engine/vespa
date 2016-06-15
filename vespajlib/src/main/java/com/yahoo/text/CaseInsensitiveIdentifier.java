// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * Created with IntelliJ IDEA.
 * User: balder
 * Date: 11.11.12
 * Time: 11:25
 * To change this template use File | Settings | File Templates.
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
