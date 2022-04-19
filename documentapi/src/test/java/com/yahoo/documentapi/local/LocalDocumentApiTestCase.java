// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.local;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.fieldset.DocIdOnly;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentAccessParams;
import com.yahoo.documentapi.DocumentResponse;
import com.yahoo.documentapi.DumpVisitorDataHandler;
import com.yahoo.documentapi.Response;
import com.yahoo.documentapi.Result;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.SyncSession;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.test.AbstractDocumentApiTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Runs the superclass tests on this implementation
 *
 * @author bratseth
 */
public class LocalDocumentApiTestCase extends AbstractDocumentApiTestCase {

    protected LocalDocumentAccess access;

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
    public void testAsyncFetch() throws InterruptedException, ExecutionException, TimeoutException {
        LocalAsyncSession session = access.createAsyncSession(new AsyncParameters());
        List<DocumentId> ids = new ArrayList<>();
        ids.add(new DocumentId("id:music:music::1"));
        ids.add(new DocumentId("id:music:music::2"));
        ids.add(new DocumentId("id:music:music::3"));
        for (DocumentId id : ids)
            session.put(new Document(access.getDocumentTypeManager().getDocumentType("music"), id));

        // Let all async operations wait for a signal from the test thread before sending their responses, and let test
        // thread wait for all responses to be delivered afterwards.
        Phaser phaser = new Phaser(1);
        access.setPhaser(phaser);

        long startTime = System.currentTimeMillis();
        int timeoutMillis = 1000;
        Set<Long> outstandingRequests = new HashSet<>();
        for (DocumentId id : ids) {
            Result result = session.get(id);
            if ( ! result.isSuccess())
                throw new IllegalStateException("Failed requesting document " + id + ": " + result.error().toString());
            outstandingRequests.add(result.getRequestId());
        }

        // Wait for responses in separate thread.
        Future<?> futureWithAssertions = Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<Document> documents = new ArrayList<>();
                while ( ! outstandingRequests.isEmpty()) {
                    int timeSinceStart = (int) (System.currentTimeMillis() - startTime);
                    Response response = session.getNext(timeoutMillis - timeSinceStart);
                    if (response == null)
                        throw new RuntimeException("Timed out waiting for documents"); // or return what you have
                    if ( ! outstandingRequests.contains(response.getRequestId())) continue; // Stale: Ignore

                    if (response.isSuccess())
                        documents.add(((DocumentResponse) response).getDocument());
                    outstandingRequests.remove(response.getRequestId());
                }
                assertEquals(3, documents.size());
                for (Document document : documents)
                    assertNotNull(document);
            }
            catch (InterruptedException e) {
                throw new IllegalArgumentException("Interrupted while waiting for responses");
            }
        });

        // All operations, and receiver, now waiting for this thread to arrive.
        assertEquals(4, phaser.getRegisteredParties());
        assertEquals(0, phaser.getPhase());
        phaser.arriveAndAwaitAdvance();
        assertEquals(1, phaser.getPhase());
        phaser.awaitAdvance(phaser.arriveAndDeregister());

        futureWithAssertions.get(1000, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testFeedingAndVisiting() throws InterruptedException, ParseException {
        DocumentType musicType = access().getDocumentTypeManager().getDocumentType("music");
        Document doc1 = new Document(musicType, "id:ns:music::1"); doc1.setFieldValue("artist", "one");
        Document doc2 = new Document(musicType, "id:ns:music::2"); doc2.setFieldValue("artist", "two");
        Document doc3 = new Document(musicType, "id:ns:music::3");

        // Select all music documents where the "artist" field is set
        VisitorParameters parameters = new VisitorParameters("music.artist");
        parameters.setFieldSet("music:artist");
        VisitorControlHandler control = new VisitorControlHandler();
        parameters.setControlHandler(control);
        Set<Document> received = new ConcurrentSkipListSet<>();
        parameters.setLocalDataHandler(new DumpVisitorDataHandler() {
            @Override public void onDocument(Document doc, long timeStamp) {
                received.add(doc);
            }
            @Override public void onRemove(DocumentId id) {
                throw new IllegalStateException("Not supposed to get here");
            }
        });

        // Visit when there are no documents completes immediately
        access.createVisitorSession(parameters).waitUntilDone(0);
        assertSame(VisitorControlHandler.CompletionCode.SUCCESS,
                   control.getResult().getCode());
        assertEquals(Set.of(),
                     received);

        // Sync-put some documents
        SyncSession out = access.createSyncSession(new SyncParameters.Builder().build());
        out.put(new DocumentPut(doc1));
        out.put(new DocumentPut(doc2));
        out.put(new DocumentPut(doc3));
        assertEquals(Map.of(doc1.getId(), doc1,
                            doc2.getId(), doc2,
                            doc3.getId(), doc3),
                     access.documents);

        // Expect a subset of documents to be returned, based on the selection
        access.createVisitorSession(parameters).waitUntilDone(0);
        assertSame(VisitorControlHandler.CompletionCode.SUCCESS,
                   control.getResult().getCode());
        assertEquals(Set.of(doc1, doc2),
                     received);

        // Remove doc2 and set artist for doc3, to see changes are reflected in subsequent visits
        out.remove(new DocumentRemove(doc2.getId()));
        out.update(new DocumentUpdate(musicType, doc3.getId()).addFieldUpdate(FieldUpdate.createAssign(musicType.getField("artist"),
                                                                                                       new StringFieldValue("three"))));
        assertEquals(Map.of(doc1.getId(), doc1,
                            doc3.getId(), doc3),
                     access.documents);
        assertEquals("three",
                     ((StringFieldValue) doc3.getFieldValue("artist")).getString());

        // Visit the documents again, retrieving none of the document fields
        parameters.setFieldSet(DocIdOnly.NAME);
        received.clear();
        access.createVisitorSession(parameters).waitUntilDone(0);
        assertSame(VisitorControlHandler.CompletionCode.SUCCESS,
                   control.getResult().getCode());
        assertEquals(Set.of(new Document(musicType, doc1.getId()), new Document(musicType, doc3.getId())),
                     received);

        // Visit the documents again, throwing an exception in the data handler on doc3
        received.clear();
        parameters.setLocalDataHandler(new DumpVisitorDataHandler() {
            @Override public void onDocument(Document doc, long timeStamp) {
                if (doc3.getId().equals(doc.getId()))
                    throw new RuntimeException("SEGFAULT");
                received.add(doc);
            }
            @Override public void onRemove(DocumentId id) {
                throw new IllegalStateException("Not supposed to get here");
            }
        });
        access.createVisitorSession(parameters).waitUntilDone(0);
        assertSame(VisitorControlHandler.CompletionCode.FAILURE,
                   control.getResult().getCode());
        assertEquals("SEGFAULT",
                     control.getResult().getMessage());
        assertEquals(Set.of(new Document(musicType, doc1.getId())),
                     received);

        // Visit the documents again, aborting after the first document
        received.clear();
        CountDownLatch visitLatch = new CountDownLatch(1);
        CountDownLatch abortLatch = new CountDownLatch(1);
        parameters.setLocalDataHandler(new DumpVisitorDataHandler() {
            @Override public void onDocument(Document doc, long timeStamp) {
                received.add(doc);
                abortLatch.countDown();
                try {
                    visitLatch.await();
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            @Override public void onRemove(DocumentId id) { throw new IllegalStateException("Not supposed to get here"); }
        });
        VisitorSession visit = access.createVisitorSession(parameters);
        abortLatch.await();
        control.abort();
        visitLatch.countDown();
        visit.waitUntilDone(0);
        assertSame(VisitorControlHandler.CompletionCode.ABORTED,
                   control.getResult().getCode());
        assertEquals(Set.of(new Document(musicType, doc1.getId())),
                     received);
    }

}
