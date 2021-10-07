// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.time.Clock;

import static org.junit.Assert.assertEquals;

public class DocumentTest {

    @Test
    public void simpleCaseOk() {
        String docId = "doc id";
        String docContent = "foo";
        Document document = new Document(docId, docContent.getBytes(), null, Clock.systemUTC().instant());
        assertEquals(docId, document.getDocumentId());
        assertEquals(ByteBuffer.wrap(docContent.getBytes()), document.getData());
        assertEquals(docContent, document.getDataAsString().toString());
        // Make sure that data is not modified on retrieval.
        assertEquals(docContent, document.getDataAsString().toString());
        assertEquals(ByteBuffer.wrap(docContent.getBytes()), document.getData());
        assertEquals(docId, document.getDocumentId());
    }

    @Test(expected = ReadOnlyBufferException.class)
    public void notMutablePutTest() {
        Document document = new Document("id", null, "data", null, Clock.systemUTC().instant());
        document.getData().put("a".getBytes());
    }

    @Test(expected = ReadOnlyBufferException.class)
    public void notMutableCompactTest() {
        Document document = new Document("id", null, "data", null, Clock.systemUTC().instant());
        document.getData().compact();
    }

}
