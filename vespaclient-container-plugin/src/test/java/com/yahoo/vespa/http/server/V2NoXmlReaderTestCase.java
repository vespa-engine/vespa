// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.messagebus.SessionCache;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.jdisc.References;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.shared.SharedMessageBus;
import com.yahoo.messagebus.shared.SharedSourceSession;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.http.client.core.Headers;
import com.yahoo.vespa.http.client.core.OperationStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;

/**
 * Check FeedHandler APIs.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class V2NoXmlReaderTestCase {

    LessConfiguredHandler handler;
    ExecutorService workers;

    @Before
    public void setUp() throws Exception {
        workers = Executors.newCachedThreadPool();
        handler = new LessConfiguredHandler(workers);
    }

    @After
    public void tearDown() throws Exception {
        handler.destroy();
        workers.shutdown();
    }

    private static class LessConfiguredHandler extends FeedHandler {

        public LessConfiguredHandler(Executor executor) throws Exception {
            super(executor, null, null, new DummyMetric(), AccessLog.voidAccessLog(), null, MetricReceiver.nullImplementation);
        }


        @Override
        protected Feeder createFeeder(HttpRequest request, InputStream requestInputStream,
                                      BlockingQueue<OperationStatus> operations, String clientId,
                                      boolean sessionIdWasGeneratedJustNow, int protocolVersion)
                throws Exception {
            return new LessConfiguredFeeder(requestInputStream, operations,
                    popClient(clientId), new FeederSettings(request), clientId, sessionIdWasGeneratedJustNow,
                    sourceSessionParams(request), null, this, this.feedReplyHandler, "");
        }

        @Override
        protected DocumentTypeManager createDocumentManager(
                DocumentmanagerConfig documentManagerConfig) {
            return null;
        }
    }

    private static class MockSharedSession extends SharedSourceSession {

        public MockSharedSession(SourceSessionParams params) {
            super(new SharedMessageBus(new MessageBus(new MockNetwork(),
                    new MessageBusParams())), params);
        }

        @Override
        public Result sendMessageBlocking(Message msg) throws InterruptedException {
            return sendMessage(msg);
        }

        @Override
        public Result sendMessage(Message msg) {
            ReplyHandler handler = msg.popHandler();
            MockReply mockReply = new MockReply(msg.getContext());
            if (msg instanceof Feeder.FeedErrorMessage) {
                mockReply.addError(new Error(123, "Could not feed this"));
            }
            handler.handleReply(mockReply);
            return Result.ACCEPTED;
        }

    }

    private static class LessConfiguredFeeder extends Feeder {

        public LessConfiguredFeeder(InputStream inputStream,
                                    BlockingQueue<OperationStatus> operations,
                                    ClientState storedState, FeederSettings settings,
                                    String clientId, boolean sessionIdWasGeneratedJustNow, SourceSessionParams sessionParams,
                                    SessionCache sessionCache, FeedHandler handler, ReplyHandler feedReplyHandler,
                                    String localHostname) throws Exception {
            super(inputStream, null, null, operations, storedState, settings, clientId, sessionIdWasGeneratedJustNow,
                    sessionParams, sessionCache, handler, new DummyMetric(), feedReplyHandler, localHostname);
        }

        protected ReferencedResource<SharedSourceSession> retainSession(
                SourceSessionParams sessionParams, SessionCache sessionCache) {
            final SharedSourceSession session = new MockSharedSession(sessionParams);
            return new ReferencedResource<>(session, References.fromResource(session));
        }
    }

    @Test
    public final void test() throws IOException {
        String sessionId;
        {
            InputStream in = new MetaStream(new byte[] { 1 });
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest
                    .createTestRequest(
                            "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                            Method.POST, in);
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "false");
            HttpResponse r = handler.handle(nalle);
            sessionId = r.headers().getFirst(Headers.SESSION_ID);
            r.render(out);
            assertEquals("",
                         Utf8.toString(out.toByteArray()));
        }
        {
            InputStream in = new MetaStream(new byte[] { 1 });
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest.createTestRequest(
                    "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                    Method.POST, in);
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "true");
            HttpResponse r = handler.handle(nalle);
            r.render(out);
            //This is different from v1. If we cannot parse XML, we will still get response code 200, but with a sensible
            //error message in the response.
            assertEquals(200, r.getStatus());
            assertEquals("id:banana:banana::doc1 ERROR Could{20}not{20}feed{20}this \n",
                         Utf8.toString(out.toByteArray()));
        }
    }

}
