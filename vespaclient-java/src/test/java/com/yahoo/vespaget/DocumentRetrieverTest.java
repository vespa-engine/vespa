// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaget;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.fieldset.DocumentOnly;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusSyncSession;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.GetDocumentReply;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.Reply;
import com.yahoo.vespaclient.ClusterDef;
import com.yahoo.vespaclient.ClusterList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class for {@link DocumentRetriever}
 *
 * @author bjorncs
 */
public class DocumentRetrieverTest {

    public static final String DOC_ID_1 = "id:storage_test:document::1";
    public static final String DOC_ID_2 = "id:storage_test:document::2";
    public static final String DOC_ID_3 = "id:storage_test:document::3";

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

    private DocumentAccessFactory mockedFactory;
    private MessageBusDocumentAccess mockedDocumentAccess;
    private MessageBusSyncSession mockedSession;
    private PrintStream oldOut;
    private PrintStream oldErr;

    @BeforeEach
    public void setUpStreams() {
        oldOut = System.out;
        oldErr = System.err;
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @BeforeEach
    public void prepareMessageBusMocks() {
        this.mockedFactory = mock(DocumentAccessFactory.class);
        this.mockedDocumentAccess = mock(MessageBusDocumentAccess.class);
        this.mockedSession = mock(MessageBusSyncSession.class);
        when(mockedFactory.createDocumentAccess(any())).thenReturn(mockedDocumentAccess);
        when(mockedDocumentAccess.createSyncSession(any())).thenReturn(mockedSession);
    }

    @AfterEach
    public void cleanUpStreams() {
        System.setOut(oldOut);
        System.setErr(oldErr);
        outContent.reset();
        errContent.reset();
    }

    private static ClientParameters.Builder createParameters() {
        return new ClientParameters.Builder()
                .setPriority(DocumentProtocol.Priority.NORMAL_2)
                .setCluster("")
                .setRoute("default")
                .setConfigId("client")
                .setFieldSet(DocumentOnly.NAME)
                .setPrintIdsOnly(false)
                .setHelp(false)
                .setShowDocSize(false)
                .setNoRetry(false)
                .setTraceLevel(0)
                .setTimeout(0)
                .setDocumentIds(Collections.emptyIterator());
    }

    private static Iterator<String> asIterator(String... docIds) {
        return Arrays.asList(docIds).iterator();
    }

    private static Reply createDocumentReply(String docId) {
        return new GetDocumentReply(new Document(DataType.DOCUMENT, new DocumentId(docId)));
    }

    private void assertContainsDocument(String documentId) {
        assertTrue(outContent.toString().contains(String.format(
                "{\"id\":\"%s\"", documentId)));
    }

    private DocumentRetriever createDocumentRetriever(ClientParameters params) {
        return createDocumentRetriever(params, new ClusterList());
    }

    private DocumentRetriever createDocumentRetriever(ClientParameters params, ClusterList clusterList) {
        return new DocumentRetriever(
                clusterList,
                mockedFactory,
                params);
    }

    // TODO: Remove on Vespa 9
    @Test
    @SuppressWarnings("removal")
    void testSendSingleMessage() throws DocumentRetrieverException {
        ClientParameters params = createParameters()
                .setDocumentIds(asIterator(DOC_ID_1))
                .setPriority(DocumentProtocol.Priority.HIGH_1)
                .setNoRetry(true)
                .build();

        when(mockedSession.syncSend(any())).thenReturn(createDocumentReply(DOC_ID_1));

        DocumentRetriever documentRetriever = new DocumentRetriever(
                new ClusterList(),
                mockedFactory,
                params);
        documentRetriever.retrieveDocuments();

        verify(mockedSession, times(1)).syncSend(argThat((ArgumentMatcher<GetDocumentMessage>) o ->
                o.getPriority().equals(DocumentProtocol.Priority.HIGH_1) && // TODO remove on Vespa 9
                        !o.getRetryEnabled()));
        assertContainsDocument(DOC_ID_1);
    }

    @Test
    void testMultipleMessages() throws DocumentRetrieverException {
        ClientParameters params = createParameters()
                .setDocumentIds(asIterator(DOC_ID_1, DOC_ID_2, DOC_ID_3))
                .build();

        when(mockedSession.syncSend(any())).thenReturn(
                createDocumentReply(DOC_ID_1),
                createDocumentReply(DOC_ID_2),
                createDocumentReply(DOC_ID_3));

        DocumentRetriever documentRetriever = createDocumentRetriever(params);
        documentRetriever.retrieveDocuments();

        verify(mockedSession, times(3)).syncSend(any());
        assertContainsDocument(DOC_ID_1);
        assertContainsDocument(DOC_ID_2);
        assertContainsDocument(DOC_ID_3);
    }

    @Test
    void testJsonOutput() throws DocumentRetrieverException, IOException {
        ClientParameters params = createParameters()
                .setDocumentIds(asIterator(DOC_ID_1, DOC_ID_2, DOC_ID_3))
                .setJsonOutput(true)
                .build();

        when(mockedSession.syncSend(any())).thenReturn(
                createDocumentReply(DOC_ID_1),
                createDocumentReply(DOC_ID_2),
                createDocumentReply(DOC_ID_3));

        DocumentRetriever documentRetriever = createDocumentRetriever(params);
        documentRetriever.retrieveDocuments();

        verify(mockedSession, times(3)).syncSend(any());
        ObjectMapper m = new ObjectMapper();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> feed = m.readValue(outContent.toByteArray(), List.class);
        assertEquals(DOC_ID_1, feed.get(0).get("id"));
        assertEquals(DOC_ID_2, feed.get(1).get("id"));
        assertEquals(DOC_ID_3, feed.get(2).get("id"));
    }

    @Test
    void testShutdownHook() throws DocumentRetrieverException {
        ClientParameters params = createParameters()
                .setDocumentIds(asIterator(DOC_ID_1))
                .build();

        when(mockedSession.syncSend(any())).thenReturn(createDocumentReply(DOC_ID_1));

        DocumentRetriever documentRetriever = createDocumentRetriever(params);
        documentRetriever.retrieveDocuments();
        documentRetriever.shutdown();

        verify(mockedSession, times(1)).destroy();
        verify(mockedDocumentAccess, times(1)).shutdown();
    }

    @Test
    void testClusterLookup() throws DocumentRetrieverException {
        final String cluster = "storage",
                expectedRoute = "[Content:cluster=storage]";

        ClientParameters params = createParameters()
                .setCluster(cluster)
                .build();

        ClusterList clusterList = new ClusterList(Collections.singletonList(new ClusterDef(cluster)));

        DocumentRetriever documentRetriever = createDocumentRetriever(params, clusterList);
        documentRetriever.retrieveDocuments();

        verify(mockedFactory).createDocumentAccess(argThat(o -> o.getRoute().equals(expectedRoute)));
    }

    @Test
    void testInvalidClusterName() throws DocumentRetrieverException {
        Throwable exception = assertThrows(DocumentRetrieverException.class, () -> {

            ClientParameters params = createParameters()
                    .setCluster("invalidclustername")
                    .build();

            ClusterList clusterList = new ClusterList(Collections.singletonList(new ClusterDef("storage")));

            DocumentRetriever documentRetriever = createDocumentRetriever(params, clusterList);
            documentRetriever.retrieveDocuments();
        });
        assertTrue(exception.getMessage().contains("The Vespa cluster contains the content clusters storage, not invalidclustername. Please select a valid vespa cluster."));
    }

    @Test
    void testEmptyClusterList() throws DocumentRetrieverException {
        Throwable exception = assertThrows(DocumentRetrieverException.class, () -> {

            ClientParameters params = createParameters()
                    .setCluster("invalidclustername")
                    .build();

            DocumentRetriever documentRetriever = createDocumentRetriever(params);
            documentRetriever.retrieveDocuments();
        });
        assertTrue(exception.getMessage().contains("The Vespa cluster does not have any content clusters declared."));
    }

    @Test
    void testHandlingErrorFromMessageBus() throws DocumentRetrieverException {
        ClientParameters params = createParameters()
                .setDocumentIds(asIterator(DOC_ID_1))
                .build();

        Reply r = new GetDocumentReply(null);
        r.addError(new Error(0, "Error message"));
        when(mockedSession.syncSend(any())).thenReturn(r);

        DocumentRetriever documentRetriever = createDocumentRetriever(params);
        documentRetriever.retrieveDocuments();

        assertTrue(errContent.toString().contains("Request failed"));
    }

    @Test
    void testShowDocSize() throws DocumentRetrieverException {
        ClientParameters params = createParameters()
                .setDocumentIds(asIterator(DOC_ID_1))
                .setShowDocSize(true)
                .build();

        Document document = new Document(DataType.DOCUMENT, new DocumentId(DOC_ID_1));
        when(mockedSession.syncSend(any())).thenReturn(new GetDocumentReply(document));

        DocumentRetriever documentRetriever = createDocumentRetriever(params);
        documentRetriever.retrieveDocuments();

        assertTrue(outContent.toString().contains(String.format("Document size: %d bytes", document.getSerializedSize())));
    }

    @Test
    void testPrintIdOnly() throws DocumentRetrieverException {
        ClientParameters params = createParameters()
                .setDocumentIds(asIterator(DOC_ID_1))
                .setPrintIdsOnly(true)
                .build();

        when(mockedSession.syncSend(any())).thenReturn(createDocumentReply(DOC_ID_1));

        DocumentRetriever documentRetriever = createDocumentRetriever(params);
        documentRetriever.retrieveDocuments();

        assertEquals(DOC_ID_1 + "\n", outContent.toString());
    }

    @Test
    void testDocumentNotFound() throws DocumentRetrieverException {
        ClientParameters params = createParameters()
                .setDocumentIds(asIterator(DOC_ID_1))
                .setPrintIdsOnly(true)
                .build();

        when(mockedSession.syncSend(any())).thenReturn(new GetDocumentReply(null));

        DocumentRetriever documentRetriever = createDocumentRetriever(params);
        documentRetriever.retrieveDocuments();

        verify(mockedSession, times(1)).syncSend(any());
        assertEquals(outContent.toString(), "Document not found.\n");
    }

    @Test
    void testTrace() throws DocumentRetrieverException {
        final int traceLevel = 9;
        ClientParameters params = createParameters()
                .setDocumentIds(asIterator(DOC_ID_1))
                .setTraceLevel(traceLevel)
                .build();

        GetDocumentReply reply = new GetDocumentReply(new Document(DataType.DOCUMENT, new DocumentId(DOC_ID_1)));
        reply.getTrace().getRoot().addChild("childnode");
        when(mockedSession.syncSend(any())).thenReturn(reply);

        DocumentRetriever documentRetriever = createDocumentRetriever(params);
        documentRetriever.retrieveDocuments();

        verify(mockedSession, times(1)).setTraceLevel(traceLevel);
        assertTrue(outContent.toString().contains("<trace>"));
    }

}
