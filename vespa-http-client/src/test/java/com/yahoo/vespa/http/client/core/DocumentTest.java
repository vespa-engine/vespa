// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class DocumentTest {
    @Test
    public void simpleCaseOk() throws Document.DocumentException {
        String docId = "doc id";
        String docContent = "foo";
        Document document = new Document(docId, docContent.getBytes(), null /* context */);
        assertThat(document.getDocumentId(), is(docId));
        assertThat(document.getData(), is(ByteBuffer.wrap(docContent.getBytes())));
        assertThat(document.getDataAsString().toString(), is(docContent));
        // Make sure that data is not modified on retrieval.
        assertThat(document.getDataAsString().toString(), is(docContent));
        assertThat(document.getData(), is(ByteBuffer.wrap(docContent.getBytes())));
        assertThat(document.getDocumentId(), is(docId));
    }

    @Test(expected = ReadOnlyBufferException.class)
    public void notMutablePutTest() {
        Document document = new Document("id", "data", null /* context */);
        document.getData().put("a".getBytes());
    }

    @Test(expected = ReadOnlyBufferException.class)
    public void notMutableCompactTest() {
        Document document = new Document("id", "data", null /* context */);
        document.getData().compact();
    }
}
