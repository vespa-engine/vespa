// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.V3HttpAPITest;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.EndpointResult;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

public class IOThreadTest {
    final EndpointResultQueue endpointResultQueue = mock(EndpointResultQueue.class);
    final ApacheGatewayConnection apacheGatewayConnection = mock(ApacheGatewayConnection.class);
    final String exceptionMessage = "SOME EXCEPTION FOO";
    CountDownLatch latch = new CountDownLatch(1);
    String docId1 = V3HttpAPITest.documents.get(0).getDocumentId();
    Document doc1 = new Document(V3HttpAPITest.documents.get(0).getDocumentId(),
            V3HttpAPITest.documents.get(0).getContents(), null /* context */);
    String docId2 = V3HttpAPITest.documents.get(1).getDocumentId();
    Document doc2 = new Document(V3HttpAPITest.documents.get(1).getDocumentId(),
            V3HttpAPITest.documents.get(1).getContents(), null /* context */);
    DocumentQueue documentQueue = new DocumentQueue(4);

    /**
     * Set up mock so that it can handle both failDocument() and resultReceived().
     * @param expectedDocIdFail on failure, this has to be the doc id, or the mock will fail.
     * @param expectedDocIdOk on ok, this has to be the doc id, or the mock will fail.
     * @param isTransient checked on failure, if different, the mock will fail.
     * @param expectedException checked on failure, if exception toString is different, the mock will fail.
     */
    void setupEndpointResultQueueMock(String expectedDocIdFail, String expectedDocIdOk,boolean isTransient, String expectedException) {

        doAnswer(invocation -> {
            EndpointResult endpointResult = (EndpointResult) invocation.getArguments()[0];
            assertThat(endpointResult.getOperationId(), is(expectedDocIdFail));
            assertThat(endpointResult.getDetail().getException().toString(),
                    containsString(expectedException));
            assertThat(endpointResult.getDetail().getResultType(), is(
                    isTransient ? Result.ResultType.TRANSITIVE_ERROR : Result.ResultType.FATAL_ERROR));

            latch.countDown();
            return null;
        }).when(endpointResultQueue).failOperation(anyObject(), eq(0));

        doAnswer(invocation -> {
            EndpointResult endpointResult = (EndpointResult) invocation.getArguments()[0];
            assertThat(endpointResult.getOperationId(), is(expectedDocIdOk));
            assertThat(endpointResult.getDetail().getResultType(), is(Result.ResultType.OPERATION_EXECUTED));
            latch.countDown();
            return null;
        }).when(endpointResultQueue).resultReceived(anyObject(), eq(0));
    }

    @Test
    public void singleDocumentSuccess() throws Exception {
        when(apacheGatewayConnection.connect()).thenReturn(true);
        InputStream serverResponse = new ByteArrayInputStream(
                (docId1 + " OK Doc{20}fed").getBytes(StandardCharsets.UTF_8));
        when(apacheGatewayConnection.writeOperations(anyObject())).thenReturn(serverResponse);
        setupEndpointResultQueueMock( "nope", docId1, true, exceptionMessage);
        try (IOThread ioThread = new IOThread(
                endpointResultQueue, apacheGatewayConnection, 0, 0, 10000, 10000L, documentQueue, 0)) {
            ioThread.post(doc1);
            assert (latch.await(120, TimeUnit.SECONDS));
        }
    }

    @Test
    public void requireThatSingleDocumentWriteErrorIsHandledProperly() throws Exception {
        when(apacheGatewayConnection.connect()).thenReturn(true);
        when(apacheGatewayConnection.writeOperations(anyObject())).thenThrow(new IOException(exceptionMessage));
        setupEndpointResultQueueMock(doc1.getOperationId(), "nope", true, exceptionMessage);
        try (IOThread ioThread = new IOThread(
                endpointResultQueue, apacheGatewayConnection, 0, 0, 10000, 10000L, documentQueue, 0)) {
            ioThread.post(doc1);
            assert (latch.await(120, TimeUnit.SECONDS));
        }
    }

    @Test
    public void requireThatTwoDocumentsFirstWriteErrorSecondOkIsHandledProperly() throws Exception {
        when(apacheGatewayConnection.connect()).thenReturn(true);
        InputStream serverResponse = new ByteArrayInputStream(
                (docId2 + " OK Doc{20}fed").getBytes(StandardCharsets.UTF_8));
        when(apacheGatewayConnection.writeOperations(anyObject()))
                .thenThrow(new IOException(exceptionMessage))
                .thenReturn(serverResponse);
        latch = new CountDownLatch(2);
        setupEndpointResultQueueMock(doc1.getOperationId(), doc2.getDocumentId(), true, exceptionMessage);

        try (IOThread ioThread = new IOThread(
                endpointResultQueue, apacheGatewayConnection, 0, 0, 10000, 10000L, documentQueue, 0)) {
            ioThread.post(doc1);
            ioThread.post(doc2);
            assert (latch.await(120, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testQueueTimeOutNoNoConnectionToServer() throws Exception {
        when(apacheGatewayConnection.connect()).thenReturn(false);
        InputStream serverResponse = new ByteArrayInputStream(
                ("").getBytes(StandardCharsets.UTF_8));
        when(apacheGatewayConnection.writeOperations(anyObject()))
                .thenReturn(serverResponse);
        setupEndpointResultQueueMock(doc1.getOperationId(), "nope", true,
                "java.lang.Exception: Not sending document operation, timed out in queue after");
        try (IOThread ioThread = new IOThread(
                endpointResultQueue, apacheGatewayConnection, 0, 0, 10, 10L, documentQueue, 0)) {
            ioThread.post(doc1);
            assert (latch.await(120, TimeUnit.SECONDS));
        }
    }
}