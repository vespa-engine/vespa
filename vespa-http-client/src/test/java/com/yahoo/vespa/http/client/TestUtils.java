// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertNull;

/**
 * @author Einar M R Rosenvinge
 */
@SuppressWarnings("deprecation")
public class TestUtils {

    public static void writeDocuments(Session session, List<TestDocument> documents) throws IOException {
        for (TestDocument document : documents) {
            writeDocument(session, document);
        }
    }

    public static void writeDocument(Session session, TestDocument document) throws IOException {
        OutputStream operation = session.stream(document.getDocumentId());
        operation.write(document.getContents());
        operation.close();
    }

    public static Map<String, Result> getResults(Session session, int num) throws InterruptedException {
        Map<String, Result> results = new HashMap<>();
        for (int i = 0; i < num; i++) {
            Result r = session.results().poll(120, TimeUnit.SECONDS);
            if (r == null) {
                String extraInfo = "";
                    extraInfo = "stats=" + session.getStatsAsJson();
                throw new IllegalStateException("Did not receive result within timeout. (" + extraInfo + ") " +
                                                "Results received: " + results.values());
            }
            results.put(r.getDocumentId(), r);
        }
        assertNull(session.results().poll(100, TimeUnit.MILLISECONDS));
        return results;
    }

    public static String zipStreamToString(InputStream inputStream) throws IOException {
        GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
        final StringBuilder rawContent = new StringBuilder();
        while (true) {
            int x = gzipInputStream.read();
            if (x < 0) {
                break;
            }
            rawContent.append((char) x);
        }
        return rawContent.toString();
    }

}
