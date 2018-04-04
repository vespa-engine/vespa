// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.communication;

import com.yahoo.vespa.http.client.FeedConnectException;
import com.yahoo.vespa.http.client.FeedEndpointException;
import com.yahoo.vespa.http.client.FeedProtocolException;
import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.V3HttpAPITest;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.Document;
import com.yahoo.vespa.http.client.core.EndpointResult;

import com.yahoo.vespa.http.client.core.ServerResponseException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

public class IOThreadTest {

    private static final Endpoint ENDPOINT = Endpoint.create("myhost");

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

    public IOThreadTest() {
        when(apacheGatewayConnection.getEndpoint()).thenReturn(ENDPOINT);
    }

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

    @Test
    public void requireThatEndpointProtocolExceptionsArePropagated()
            throws IOException, ServerResponseException, InterruptedException, TimeoutException, ExecutionException {
        when(apacheGatewayConnection.connect()).thenReturn(true);
        int errorCode = 403;
        String errorMessage = "Not authorized";
        doThrow(new ServerResponseException(errorCode, errorMessage)).when(apacheGatewayConnection).handshake();
        Future<FeedEndpointException> futureException = endpointErrorCapturer(endpointResultQueue);

        try (IOThread ioThread = new IOThread(
                endpointResultQueue, apacheGatewayConnection, 0, 0, 10, 10L, documentQueue, 0)) {
            ioThread.post(doc1);
            FeedEndpointException reportedException = futureException.get(120, TimeUnit.SECONDS);
            assertThat(reportedException, instanceOf(FeedProtocolException.class));
            FeedProtocolException actualException = (FeedProtocolException) reportedException;
            assertThat(actualException.getHttpStatusCode(), equalTo(errorCode));
            assertThat(actualException.getHttpResponseMessage(), equalTo(errorMessage));
            assertThat(actualException.getEndpoint(), equalTo(ENDPOINT));
            assertThat(actualException.getMessage(), equalTo("Endpoint 'myhost:4080' returned an error on handshake: 403 - Not authorized"));
        }
    }

    @Test
    public void requireThatEndpointConnectExceptionsArePropagated()
        throws IOException, ServerResponseException, InterruptedException, TimeoutException, ExecutionException {
        when(apacheGatewayConnection.connect()).thenReturn(true);
        String errorMessage = "generic error message";
        IOException cause = new IOException(errorMessage);
        doThrow(cause).when(apacheGatewayConnection).handshake();
        Future<FeedEndpointException> futureException = endpointErrorCapturer(endpointResultQueue);

        try (IOThread ioThread = new IOThread(
                endpointResultQueue, apacheGatewayConnection, 0, 0, 10, 10L, documentQueue, 0)) {
            ioThread.post(doc1);
            FeedEndpointException reportedException = futureException.get(120, TimeUnit.SECONDS);
            assertThat(reportedException, instanceOf(FeedConnectException.class));
            FeedConnectException actualException = (FeedConnectException) reportedException;
            assertThat(actualException.getCause(), equalTo(cause));
            assertThat(actualException.getEndpoint(), equalTo(ENDPOINT));
            assertThat(actualException.getMessage(), equalTo("Handshake to endpoint 'myhost:4080' failed: generic error message"));
        }
    }

    private static Future<FeedEndpointException> endpointErrorCapturer(EndpointResultQueue endpointResultQueue) {
        CompletableFuture<FeedEndpointException> futureResult = new CompletableFuture<>();
        doAnswer(invocation -> {
            if (futureResult.isDone()) return null;
            FeedEndpointException reportedException = (FeedEndpointException) invocation.getArguments()[0];
            futureResult.complete(reportedException);
            return null;
        }).when(endpointResultQueue).onEndpointError(any());
        return futureResult;
    }

}
