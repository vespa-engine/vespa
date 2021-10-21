// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

import java.util.Base64;

/**
 * @author baldersheim
 */
public class RawBase64 {
    private final byte[] content;
    public RawBase64(byte[] content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return Base64.getEncoder().encodeToString(content);
    }
}
