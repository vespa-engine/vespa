// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.google.common.base.Splitter;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.messagebus.SessionCache;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.metrics.DocumentApiMetrics;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.messagebus.Result;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.shared.SharedSourceSession;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.http.client.config.FeedParams;
import com.yahoo.vespa.http.client.core.ErrorCode;
import com.yahoo.vespa.http.client.core.Headers;
import com.yahoo.vespa.http.client.core.OperationStatus;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FeedHandlerV3Test {
    final CollectingMetric metric = new CollectingMetric();
    private final Executor simpleThreadpool = Executors.newCachedThreadPool();

    @Test
    public void feedOneDocument() throws Exception {
        final FeedHandlerV3 feedHandlerV3 = setupFeederHandler(simpleThreadpool);
        HttpResponse httpResponse = feedHandlerV3.handle(createRequest(1));
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        httpResponse.render(outStream);
        assertThat(httpResponse.getContentType(), is("text/plain"));
        assertThat(Utf8.toString(outStream.toByteArray()), is("1230 OK message trace\n"));
    }

    @Test
    public void feedOneBrokenDocument() throws Exception {
        final FeedHandlerV3 feedHandlerV3 = setupFeederHandler(simpleThreadpool);
        HttpResponse httpResponse = feedHandlerV3.handle(createBrokenRequest());
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        httpResponse.render(outStream);
        assertThat(httpResponse.getContentType(), is("text/plain"));
        assertThat(Utf8.toString(outStream.toByteArray()), startsWith("1230 ERROR "));
        assertThat(metric.get(MetricNames.PARSE_ERROR), is(1L));
    }

    @Test
    public void feedManyDocument() throws Exception {
        final FeedHandlerV3 feedHandlerV3 = setupFeederHandler(simpleThreadpool);
        HttpResponse httpResponse = feedHandlerV3.handle(createRequest(100));
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        httpResponse.render(outStream);
        assertThat(httpResponse.getContentType(), is("text/plain"));
        String result = Utf8.toString(outStream.toByteArray());
        assertThat(Splitter.on("\n").splitToList(result).size(), is(101));
    }

    private static DocumentTypeManager createDoctypeManager() {
        DocumentTypeManager docTypeManager = new DocumentTypeManager();
        DocumentType documentType = new DocumentType("testdocument");
        documentType.addField("title", DataType.STRING);
        documentType.addField("body", DataType.STRING);
        docTypeManager.registerDocumentType(documentType);
        return docTypeManager;
    }

    static HttpRequest createRequest(int numberOfDocs) {
        StringBuilder wireData = new StringBuilder();
        for (int x = 0; x < numberOfDocs; x++) {
            String docData = "[{\"put\": \"id:testdocument:testdocument::c\", \"fields\": { \"title\": \"fooKey\", \"body\": \"value\"}}]";
            String operationId = "123" + x;
            wireData.append(operationId + " " + Integer.toHexString(docData.length()) + "\n" + docData);
        }
        return createRequestWithPayload(wireData.toString());
    }

    private static HttpRequest createBrokenRequest() {
        String docData = "[{\"put oops I broke it]";
        String wireData = "1230 " + Integer.toHexString(docData.length()) + "\n" + docData;
        return createRequestWithPayload(wireData);
    }

    static HttpRequest createRequestWithPayload(String payload) {
        InputStream inputStream = new ByteArrayInputStream(payload.getBytes());
        HttpRequest request = HttpRequest.createTestRequest("http://dummyhostname:19020/reserved-for-internal-use/feedapi",
                com.yahoo.jdisc.http.HttpRequest.Method.POST, inputStream);
        request.getJDiscRequest().headers().add(Headers.VERSION, "3");
        request.getJDiscRequest().headers().add(Headers.DATA_FORMAT, FeedParams.DataFormat.JSON_UTF8.name());
        request.getJDiscRequest().headers().add(Headers.TIMEOUT, "1000000000");
        request.getJDiscRequest().headers().add(Headers.CLIENT_ID, "client123");
        request.getJDiscRequest().headers().add(Headers.PRIORITY, "LOWEST");
        request.getJDiscRequest().headers().add(Headers.TRACE_LEVEL, "4");
        request.getJDiscRequest().headers().add(Headers.DRAIN, "true");
        return request;
    }

    private FeedHandlerV3 setupFeederHandler(Executor threadPool) {
        DocumentmanagerConfig docMan = new DocumentmanagerConfig(new DocumentmanagerConfig.Builder().enablecompression(true));
        FeedHandlerV3 feedHandlerV3 = new FeedHandlerV3(
                threadPool,
                metric,
                docMan,
                null /* session cache */,
                new DocumentApiMetrics(MetricReceiver.nullImplementation, "test")) {
            @Override
            protected ReferencedResource<SharedSourceSession> retainSource(
                    SessionCache sessionCache, SourceSessionParams sessionParams)  {
                SharedSourceSession sharedSourceSession = mock(SharedSourceSession.class);

                try {
                    when(sharedSourceSession.sendMessageBlocking(any())).thenAnswer((Answer<?>) invocation -> {
                        Object[] args = invocation.getArguments();
                        PutDocumentMessage putDocumentMessage = (PutDocumentMessage) args[0];
                        ReplyContext replyContext = (ReplyContext) putDocumentMessage.getContext();
                        replyContext.feedReplies.add(new OperationStatus("message", replyContext.docId, ErrorCode.OK, false, "trace"));
                        Result result = mock(Result.class);
                        when(result.isAccepted()).thenReturn(true);
                        return result;
                    });
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                Result result = mock(Result.class);
                when(result.isAccepted()).thenReturn(true);
                ReferencedResource<SharedSourceSession> refSharedSessopn =
                        new ReferencedResource<>(sharedSourceSession, () -> {});
                return refSharedSessopn;
            }
        };
        feedHandlerV3.injectDocumentManangerForTests(createDoctypeManager());
        return feedHandlerV3;
    }

}
