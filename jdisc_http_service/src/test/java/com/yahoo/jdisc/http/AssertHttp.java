// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.http.test.RemoteClient;
import com.yahoo.jdisc.http.test.ServerTestDriver;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public abstract class AssertHttp {

    public static void assertChunk(String expected, String actual) {
        if (expected.startsWith("HTTP/1.")) {
            expected = sortChunk(expected);
            actual = sortChunk(actual);
        }
        Pattern pattern = Pattern.compile(expected, Pattern.DOTALL | Pattern.MULTILINE);
        if (pattern.matcher(actual).matches()) {
            return;
        }
        assertEquals(expected, actual);
    }

    public static void assertResponse(RequestHandler requestHandler, String request,
                                      String... expectedChunks) throws IOException {
        ServerTestDriver driver = ServerTestDriver.newInstance(requestHandler);
        assertResponse(driver, request, expectedChunks);
        assertTrue(driver.close());
    }

    public static void assertResponse(ServerTestDriver driver, String request, String... expectedChunks)
            throws IOException {
        assertResponse(driver.client(), request, expectedChunks);
    }

    public static void assertResponse(RemoteClient client, String request, String... expectedChunks)
            throws IOException {
        client.writeRequest(request);
        for (String expected : expectedChunks) {
            assertChunk(expected, client.readChunk());
        }
    }

    private static String sortChunk(String chunk) {
        String[] lines = chunk.split("\r\n");
        if (lines.length > 2) {
            int prev = 1, next = 2;
            for ( ; next < lines.length && !lines[next].isEmpty(); ++next) {
                if (!Character.isLetterOrDigit(lines[next].charAt(0))) {
                    Arrays.sort(lines, prev, next);
                    prev = next + 1;
                }
            }
            if (prev < next) {
                Arrays.sort(lines, prev, next);
            }
        }
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line).append("\r\n");
        }
        return out.toString();
    }
}
