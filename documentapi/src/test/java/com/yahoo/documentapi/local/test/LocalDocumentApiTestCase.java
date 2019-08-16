// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.local.test;

import com.yahoo.document.*;
import com.yahoo.documentapi.*;
import com.yahoo.documentapi.local.LocalDocumentAccess;
import com.yahoo.documentapi.test.AbstractDocumentApiTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Runs the superclass tests on this implementation
 *
 * @author bratseth
 */
public class LocalDocumentApiTestCase extends AbstractDocumentApiTestCase {

    protected DocumentAccess access;

    @Override
    protected DocumentAccess access() {
        return access;
    }

    @Before
    public void setUp() {
        DocumentAccessParams params = new DocumentAccessParams();
        params.setDocumentManagerConfigId("file:src/test/cfg/documentmanager.cfg");
        access = new LocalDocumentAccess(params);
    }

    @After
    public void shutdownAccess() {
        access.shutdown();
    }

    @Test
    public void testNoExceptionFromAsync() {
        AsyncSession session = access.createAsyncSession(new AsyncParameters());

        DocumentType type = access.getDocumentTypeManager().getDocumentType("music");
        DocumentUpdate docUp = new DocumentUpdate(type, new DocumentId("id:ns:music::2"));

        Result result = session.update(docUp);
        assertTrue(result.isSuccess());
        Response response = session.getNext();
        assertEquals(result.getRequestId(), response.getRequestId());
        assertFalse(response.isSuccess());
        session.destroy();
    }

    @Test
    public void testAsyncFetch() {
        AsyncSession session = access.createAsyncSession(new AsyncParameters());
        List<DocumentId> ids = new ArrayList<>();
        ids.add(new DocumentId("id:music:music::1"));
        ids.add(new DocumentId("id:music:music::2"));
        ids.add(new DocumentId("id:music:music::3"));
        for (DocumentId id : ids)
            session.put(new Document(access.getDocumentTypeManager().getDocumentType("music"), id));
        int timeout = 100;

        long startTime = System.currentTimeMillis();
        Set<Long> outstandingRequests = new HashSet<>();
        for (DocumentId id : ids) {
            Result result = session.get(id);
            if ( ! result.isSuccess())
                throw new IllegalStateException("Failed requesting document " + id, result.getError().getCause());
            outstandingRequests.add(result.getRequestId());
        }

        List<Document> documents = new ArrayList<>();
        try {
            while ( ! outstandingRequests.isEmpty()) {
                int timeSinceStart = (int)(System.currentTimeMillis() - startTime);
                Response response = session.getNext(timeout - timeSinceStart);
                if (response == null)
                    throw new RuntimeException("Timed out waiting for documents"); // or return what you have
                if ( ! outstandingRequests.contains(response.getRequestId())) continue; // Stale: Ignore

                if (response.isSuccess())
                    documents.add(((DocumentResponse)response).getDocument());
                outstandingRequests.remove(response.getRequestId());
            }
        }
        catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for documents", e);
        }

        assertEquals(3, documents.size());
        for (Document document : documents)
            assertNotNull(document);
    }

}
