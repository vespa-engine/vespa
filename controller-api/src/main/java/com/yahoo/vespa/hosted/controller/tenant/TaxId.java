// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import ai.vespa.validation.StringWrapper;

import static ai.vespa.validation.Validation.requireLength;

/**
 * @author olaa
 */
public record TaxId(Type type, Code code)  {

    public TaxId(String type, String code) { this(new Type(type), new Code(code)); }

    public static TaxId empty() { return new TaxId(Type.empty(), Code.empty()); }
    public boolean isEmpty() { return type.isEmpty() && code.isEmpty(); }

    // TODO(bjorncs) Remove legacy once no longer present in ZK
    public static TaxId legacy(String code) { return new TaxId(Type.empty(), new Code(code)); }
    public boolean isLegacy() { return type.isEmpty() && !code.isEmpty(); }

    public static class Type extends StringWrapper<Type> {
        public Type(String value) {
            super(value);
            requireLength(value, "tax code type length", 0, 16);
        }

        public static Type empty() { return new Type(""); }
        public boolean isEmpty() { return value().isEmpty(); }
    }

    public static class Code extends StringWrapper<Code> {
        public Code(String value) {
            super(value);
            requireLength(value, "tax code value length", 0, 64);
        }

        public static Code empty() { return new Code(""); }
        public boolean isEmpty() { return value().isEmpty(); }
    }
}
