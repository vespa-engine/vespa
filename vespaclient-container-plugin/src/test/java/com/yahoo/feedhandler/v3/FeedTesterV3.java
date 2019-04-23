// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedhandler.v3;

import com.google.common.base.Splitter;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.messagebus.SessionCache;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.metrics.DocumentApiMetrics;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.shared.SharedSourceSession;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.http.client.config.FeedParams;
import com.yahoo.vespa.http.client.core.ErrorCode;
import com.yahoo.vespa.http.client.core.Headers;
import com.yahoo.vespa.http.client.core.OperationStatus;
import com.yahoo.vespa.http.server.ReplyContext;
import com.yahoo.vespa.http.server.FeedHandlerV3;
import org.junit.Test;
import com.yahoo.container.jdisc.HttpRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.yahoo.messagebus.Result;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public class FeedTesterV3 {

    @Test
    public void feedOneDocument() throws Exception {
        final FeedHandlerV3 feedHandlerV3 = setupFeederHandler();
        HttpResponse httpResponse = feedHandlerV3.handle(createRequest(1));
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        httpResponse.render(outStream);
        assertThat(httpResponse.getContentType(), is("text/plain"));
        assertThat(Utf8.toString(outStream.toByteArray()), is("1230 OK message trace\n"));

    }

    @Test
    public void feedManyDocument() throws Exception {
        final FeedHandlerV3 feedHandlerV3 = setupFeederHandler();
        HttpResponse httpResponse = feedHandlerV3.handle(createRequest(100));
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        httpResponse.render(outStream);
        assertThat(httpResponse.getContentType(), is("text/plain"));
        String result = Utf8.toString(outStream.toByteArray());
        assertThat(Splitter.on("\n").splitToList(result).size(), is(101));
    }

    DocumentTypeManager createDoctypeManager() {
        DocumentTypeManager docTypeManager = new DocumentTypeManager();
        DocumentType documentType = new DocumentType("testdocument");
        documentType.addField("title", DataType.STRING);
        documentType.addField("body", DataType.STRING);
        docTypeManager.registerDocumentType(documentType);
        return docTypeManager;
    }

    HttpRequest createRequest(int numberOfDocs) {
        String clientId = "client123";
        StringBuilder wireData = new StringBuilder();
        for (int x = 0; x < numberOfDocs; x++) {
            String docData = "[{\"put\": \"id:testdocument:testdocument::c\", \"fields\": { \"title\": \"fooKey\", \"body\": \"value\"}}]";
            String operationId = "123" + x;
            wireData.append(operationId + " " + Integer.toHexString(docData.length()) + "\n" + docData);
        }
        InputStream inputStream =  new ByteArrayInputStream(wireData.toString().getBytes());
        HttpRequest request = HttpRequest.createTestRequest(
                "http://dummyhostname:19020/reserved-for-internal-use/feedapi",
                com.yahoo.jdisc.http.HttpRequest.Method.POST,
                inputStream);
        request.getJDiscRequest().headers().add(Headers.VERSION, "3");
        request.getJDiscRequest().headers().add(Headers.DATA_FORMAT, FeedParams.DataFormat.JSON_UTF8.name());
        request.getJDiscRequest().headers().add(Headers.TIMEOUT, "1000000000");
        request.getJDiscRequest().headers().add(Headers.CLIENT_ID, clientId);
        request.getJDiscRequest().headers().add(Headers.PRIORITY, "LOWEST");
        request.getJDiscRequest().headers().add(Headers.TRACE_LEVEL, "4");
        request.getJDiscRequest().headers().add(Headers.DRAIN, "true");
        return request;
    }

    FeedHandlerV3 setupFeederHandler() throws Exception {
        Executor threadPool = Executors.newCachedThreadPool();
        DocumentmanagerConfig docMan = new DocumentmanagerConfig(new DocumentmanagerConfig.Builder().enablecompression(true));
        FeedHandlerV3 feedHandlerV3 = new FeedHandlerV3(
                new FeedHandlerV3.Context(threadPool, AccessLog.voidAccessLog(), new NullFeedMetric(true)),
                docMan,
                null /* session cache */,
                null /* thread pool config */, 
                new DocumentApiMetrics(MetricReceiver.nullImplementation, "test")) {
            @Override
            protected ReferencedResource<SharedSourceSession> retainSource(
                    SessionCache sessionCache, SourceSessionParams sessionParams)  {
                SharedSourceSession sharedSourceSession = mock(SharedSourceSession.class);

                try {
                    Mockito.stub(sharedSourceSession.sendMessageBlocking(anyObject())).toAnswer((Answer) invocation -> {
                        Object[] args = invocation.getArguments();
                        PutDocumentMessage putDocumentMessage = (PutDocumentMessage) args[0];
                        ReplyContext replyContext = (ReplyContext)putDocumentMessage.getContext();
                        replyContext.feedReplies.add(new OperationStatus("message", replyContext.docId, ErrorCode.OK, false, "trace"));
                        Result result = mock(Result.class);
                        when(result.isAccepted()).thenReturn(true);
                        return result;
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
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
