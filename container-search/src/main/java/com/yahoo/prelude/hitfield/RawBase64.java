// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * Wraps a byte [] and renders it as base64 encoded string
 * @author baldersheim
 */
public class RawBase64 implements Comparable<RawBase64> {
    private final byte[] content;
    private final boolean withoutPadding;
    public RawBase64(byte[] content) {
        this(content, false);
    }
    public RawBase64(byte[] content, boolean withoutPadding) {
        Objects.requireNonNull(content);
        this.content = content;
        this.withoutPadding = withoutPadding;
    }

    public byte [] value() { return content; }

    @Override
    public int compareTo(RawBase64 rhs) {
        return Arrays.compareUnsigned(content, rhs.content);
    }

    @Override
    public String toString() {
        return withoutPadding
                ? Base64.getEncoder().withoutPadding().encodeToString(content)
                : Base64.getEncoder().encodeToString(content);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RawBase64 rawBase64 = (RawBase64) o;
        return Arrays.equals(content, rawBase64.content);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(content);
    }
}
