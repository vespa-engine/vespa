// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
class YBase64Test {

    @Test
    void encodesAndDecodesCorrectly() {
        var base64Encoded = "+abcd/01234=";
        byte[] raw = Base64.getDecoder().decode(base64Encoded);
        assertEquals("+abcd/01234=", new String(Base64.getEncoder().encode(raw)));
        byte[] ybase64Decoded = YBase64.encode(raw);
        assertEquals(".abcd_01234-", new String(ybase64Decoded));
        assertArrayEquals(raw, YBase64.decode(ybase64Decoded));
    }

}