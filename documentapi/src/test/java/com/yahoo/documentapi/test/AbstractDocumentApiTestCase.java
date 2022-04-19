// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.test;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentResponse;
import com.yahoo.documentapi.Response;
import com.yahoo.documentapi.ResponseHandler;
import com.yahoo.documentapi.Result;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.SyncSession;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * These tests should work with all implementations (who choose to implement these features) To test a certain
 * implementation subclass this test and make the subclass setup assign the implementation class to the
 * <code>access</code> member variable (make sure it also calls super.setUp()). Override tests of nonsupported features
 * to do nothing.
 *
 * @author bratseth
 */
public abstract class AbstractDocumentApiTestCase {

    protected abstract DocumentAccess access();

    @Test
    public void requireThatSyncSessionWorks() {
        SyncSession session = access().createSyncSession(new SyncParameters.Builder().build());

        DocumentType type = access().getDocumentTypeManager().getDocumentType("music");
        Document doc1 = new Document(type, new DocumentId("id:ns:music::1"));
        Document doc2 = new Document(type, new DocumentId("id:ns:music::2"));

        session.put(new DocumentPut(doc1));
        session.put(new DocumentPut(doc2));
        assertEquals(doc1, session.get(new DocumentId("id:ns:music::1")));
        assertEquals(doc2, session.get(new DocumentId("id:ns:music::2")));

        session.remove(new DocumentRemove(new DocumentId("id:ns:music::1")));
        assertNull(session.get(new DocumentId("id:ns:music::1")));
        assertEquals(doc2, session.get(new DocumentId("id:ns:music::2")));

        session.remove(new DocumentRemove(new DocumentId("id:ns:music::2")));
        assertNull(session.get(new DocumentId("id:ns:music::1")));
        assertNull(session.get(new DocumentId("id:ns:music::2")));

        session.destroy();
    }

    @Test
    public void requireThatAsyncSessionWorks() throws InterruptedException {
        AsyncSession session = access().createAsyncSession(new AsyncParameters());
        HashMap<Long, Response> results = new LinkedHashMap<>();
        Result result;
        DocumentType type = access().getDocumentTypeManager().getDocumentType("music");
        Document doc1 = new Document(type, new DocumentId("id:ns:music::1"));
        Document doc2 = new Document(type, new DocumentId("id:ns:music::2"));

        result = session.put(doc1);
        assertTrue(result.isSuccess());
        results.put(result.getRequestId(), new Response(result.getRequestId()));
        result = session.put(doc2);
        assertTrue(result.isSuccess());
        results.put(result.getRequestId(), new Response(result.getRequestId()));

        List<Response> responses = new ArrayList<>();
        waitForAcks(session, 2, responses);

        result = session.get(new DocumentId("id:ns:music::1"));
        assertTrue(result.isSuccess());
        results.put(result.getRequestId(), new DocumentResponse(result.getRequestId(), doc1));
        result = session.get(new DocumentId("id:ns:music::2"));
        assertTrue(result.isSuccess());
        results.put(result.getRequestId(), new DocumentResponse(result.getRequestId(), doc2));
        // These Gets shall observe the ACKed Puts sent for the same document IDs.
        waitForAcks(session, 2, responses);

        result = session.remove(new DocumentId("id:ns:music::1"));
        assertTrue(result.isSuccess());
        results.put(result.getRequestId(), new Response(result.getRequestId()));
        waitForAcks(session, 1, responses);

        result = session.get(new DocumentId("id:ns:music::1"));
        assertTrue(result.isSuccess());
        results.put(result.getRequestId(), new DocumentResponse(result.getRequestId()));
        result = session.get(new DocumentId("id:ns:music::2"));
        assertTrue(result.isSuccess());
        results.put(result.getRequestId(), new DocumentResponse(result.getRequestId(), doc2));
        waitForAcks(session, 2, responses);

        result = session.remove(new DocumentId("id:ns:music::2"));
        assertTrue(result.isSuccess());
        results.put(result.getRequestId(), new Response(result.getRequestId()));
        waitForAcks(session, 1, responses);

        result = session.get(new DocumentId("id:ns:music::1"));
        assertTrue(result.isSuccess());
        results.put(result.getRequestId(), new DocumentResponse(result.getRequestId()));
        result = session.get(new DocumentId("id:ns:music::2"));
        assertTrue(result.isSuccess());
        results.put(result.getRequestId(), new DocumentResponse(result.getRequestId()));
        waitForAcks(session, 2, responses);

        for (Response response : responses) {
            assertTrue(response.isSuccess());
            assertEquals(results.get(response.getRequestId()), response);
        }

        session.destroy();
    }

    @Test
    public void requireThatAsyncHandlerWorks() throws InterruptedException {
        MyHandler handler = new MyHandler();
        AsyncSession session = access().createAsyncSession(new AsyncParameters().setResponseHandler(handler));
        DocumentType type = access().getDocumentTypeManager().getDocumentType("music");
        Document doc1 = new Document(type, new DocumentId("id:ns:music::1"));
        assertTrue(session.put(doc1).isSuccess());
        assertTrue(handler.latch.await(60, TimeUnit.SECONDS));
        assertNotNull(handler.response);
        session.destroy();
    }

    private static void waitForAcks(AsyncSession session, int n, List<Response> responsesOut) throws InterruptedException {
        for (int i = 0; i < n; ++i) {
            Response response;
            if (i % 2 == 0) {
                response = pollNext(session);
            } else {
                response = session.getNext(60000);
            }
            responsesOut.add(response);
        }
    }

    private static Response pollNext(AsyncSession session) throws InterruptedException {
        for (int i = 0; i < 600; ++i) {
            Response response = session.getNext();
            if (response != null) {
                return response;
            }
            Thread.sleep(100);
        }
        return null;
    }

    private static class MyHandler implements ResponseHandler {

        final CountDownLatch latch = new CountDownLatch(1);
        Response response = null;

        @Override
        public void handleResponse(Response response) {
            this.response = response;
            latch.countDown();
        }
    }

}
