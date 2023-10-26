// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import static org.junit.Assert.assertEquals;

public class TlsDetectionTest {

    static private String message(byte[] data) {
        String msg = "isTls([";
        String delimiter = "";
        for (byte b: data) {
            msg += delimiter + (b & 0xff);
            delimiter = ", ";
        }
        msg += "])";
        return msg;
    }

    static private void checkTls(boolean expect, int ... values) {
        byte[] data = new byte[values.length];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) values[i];
        }
        assertEquals(message(data), expect, MaybeTlsCryptoSocket.looksLikeTlsToMe(data));
    }

    @org.junit.Test public void testValidHandshake() {
        checkTls(true, 22, 3, 1, 10, 255, 1, 0, 10, 251);
        checkTls(true, 22, 3, 3, 10, 255, 1, 0, 10, 251);
    }

    @org.junit.Test public void testDataOfWrongSize() {
        checkTls(false, 22, 3, 1, 10, 255, 1, 0, 10);
        checkTls(false, 22, 3, 1, 10, 255, 1, 0, 10, 251, 0);
    }

    @org.junit.Test public void testDataNotTaggedAsHandshake() {
        checkTls(false, 23, 3, 1, 10, 255, 1, 0, 10, 251);
    }

    @org.junit.Test public void testDataWithBadMajorVersion() {
        checkTls(false, 22, 0, 1, 10, 255, 1, 0, 10, 251);
        checkTls(false, 22, 1, 1, 10, 255, 1, 0, 10, 251);
        checkTls(false, 22, 2, 1, 10, 255, 1, 0, 10, 251);
        checkTls(false, 22, 4, 1, 10, 255, 1, 0, 10, 251);
        checkTls(false, 22, 5, 1, 10, 255, 1, 0, 10, 251);
    }

    @org.junit.Test public void testDataWithBadMinorVersion() {
        checkTls(false, 22, 3, 0, 10, 255, 1, 0, 10, 251);
        checkTls(false, 22, 3, 2, 10, 255, 1, 0, 10, 251);
        checkTls(false, 22, 3, 4, 10, 255, 1, 0, 10, 251);
        checkTls(false, 22, 3, 5, 10, 255, 1, 0, 10, 251);
    }

    @org.junit.Test public void testDataNotTaggedAsClientHello() {
        checkTls(false, 22, 3, 1, 10, 255, 0, 0, 10, 251);
        checkTls(false, 22, 3, 1, 10, 255, 2, 0, 10, 251);
    }

    @org.junit.Test public void testFrameSizeLimits() {
        checkTls(false, 22, 3, 1, 255, 255, 1,   0, 255, 251); // max
        checkTls(false, 22, 3, 1,  72,   1, 1,   0,  71, 253); // 18k + 1
        checkTls(true,  22, 3, 1,  72,   0, 1,   0,  71, 252); // 18k
        checkTls(true,  22, 3, 1,   0,   4, 1,   0,   0,   0); // 4
        checkTls(false, 22, 3, 1,   0,   3, 1,   0,   0,   0); // 3 - capped
        checkTls(false, 22, 3, 1,   0,   3, 1, 255, 255, 255); // 3 - wrapped
    }

    @org.junit.Test public void testFrameAndClientHelloSizeRelationship() {
        checkTls(true,  22, 3, 1, 10, 255, 1, 0, 10, 251);
        checkTls(false, 22, 3, 1, 10, 255, 1, 1, 10, 251);
        checkTls(false, 22, 3, 1, 10, 255, 1, 2, 10, 251);

        checkTls(false, 22, 3, 1, 10, 5, 1, 0, 10, 0);
        checkTls(true,  22, 3, 1, 10, 5, 1, 0, 10, 1);
        checkTls(false, 22, 3, 1, 10, 5, 1, 0, 10, 2);

        checkTls(false, 22, 3, 1, 10, 5, 1, 0,  9, 1);
        checkTls(true,  22, 3, 1, 10, 5, 1, 0, 10, 1);
        checkTls(false, 22, 3, 1, 10, 5, 1, 0, 11, 1);

        checkTls(true, 22, 3, 1, 10, 5, 1, 0, 10,   1);
        checkTls(true, 22, 3, 1, 10, 4, 1, 0, 10,   0);
        checkTls(true, 22, 3, 1, 10, 3, 1, 0,  9, 255);
        checkTls(true, 22, 3, 1, 10, 2, 1, 0,  9, 254);
        checkTls(true, 22, 3, 1, 10, 1, 1, 0,  9, 253);
        checkTls(true, 22, 3, 1, 10, 0, 1, 0,  9, 252);
    }
}
