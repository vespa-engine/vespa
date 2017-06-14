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
import com.yahoo.messagebus.shared.SharedMessageBus;
import com.yahoo.messagebus.shared.SharedSourceSession;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.http.client.core.Headers;
import com.yahoo.vespa.http.client.core.OperationStatus;
import com.yahoo.vespaxmlparser.MockFeedReaderFactory;

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
public class V2FailingMessagebusTestCase {

    LessConfiguredHandler handler;
    ExecutorService workers;
    int mbus;

    @Before
    public void setUp() throws Exception {
        workers = Executors.newCachedThreadPool();
        handler = new LessConfiguredHandler(workers);
        mbus = 0;
    }

    @After
    public void tearDown() throws Exception {
        handler.destroy();
        workers.shutdown();
        mbus = 0;
    }

    private class LessConfiguredHandler extends FeedHandler {

        public LessConfiguredHandler(Executor executor) throws Exception {
            super(executor, null, null, new DummyMetric(), AccessLog.voidAccessLog(), null, MetricReceiver.nullImplementation);
        }

        @Override
        protected Feeder createFeeder(HttpRequest request,
                                      InputStream requestInputStream,
                                      BlockingQueue<OperationStatus> operations,
                                      String clientId,
                                      boolean sessionIdWasGeneratedJustNow, int protocolVersion) throws Exception {
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

    private class MockSharedSession extends SharedSourceSession {

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

            switch (mbus) {
            case 0:
                throw new RuntimeException("boom");
            case 1:
                Result r = new Result(ErrorCode.SEND_QUEUE_FULL, "tralala");
                mbus = 2;
                return r;
            case 2:
                handler.handleReply(new MockReply(msg.getContext()));
                return Result.ACCEPTED;
            default:
                throw new IllegalStateException("WTF?!");
            }
        }
    }

    private class LessConfiguredFeeder extends Feeder {

        public LessConfiguredFeeder(InputStream inputStream,
                                    BlockingQueue<OperationStatus> operations,
                                    ClientState storedState, FeederSettings settings,
                                    String clientId, boolean sessionIdWasGeneratedJustNow, SourceSessionParams sessionParams,
                                    SessionCache sessionCache, FeedHandler handler, ReplyHandler feedReplyHandler,
                                    String localHostname) throws Exception {
            super(inputStream, new MockFeedReaderFactory(), null, operations, storedState, settings, clientId, sessionIdWasGeneratedJustNow,
                    sessionParams, sessionCache, handler, new DummyMetric(), feedReplyHandler, localHostname);
        }

        protected ReferencedResource<SharedSourceSession> retainSession(
                SourceSessionParams sessionParams, SessionCache sessionCache) {
            final SharedSourceSession session = new MockSharedSession(sessionParams);
            return new ReferencedResource<>(session, References.fromResource(session));
        }
    }

    @Test
    public final void testFailingMbus() throws IOException {
        String sessionId;
        {
            InputStream in = new MetaStream(new byte[]{1});
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
            InputStream in = new MetaStream(new byte[]{1});
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest.createTestRequest(
                    "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                    Method.POST, in);
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            HttpResponse r = handler.handle(nalle);
            r.render(out);
            assertEquals("id:banana:banana::doc1 ERROR boom \n",
                         Utf8.toString(out.toByteArray()));
        }
    }

    @Test
    public final void testBusyMbus() throws IOException {
        String sessionId;
        {
            InputStream in = new MetaStream(new byte[]{1});
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest
                    .createTestRequest(
                            "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                            Method.POST, in);
            mbus = 2;
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
            HttpRequest nalle = HttpRequest
                    .createTestRequest(
                            "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                            Method.POST, in);
            mbus = 1;
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "true");
            nalle.getJDiscRequest().headers()
                    .add(Headers.DENY_IF_BUSY, "false");
            HttpResponse r = handler.handle(nalle);
            r.render(out);
            assertEquals("id:banana:banana::doc1 OK Document{20}processed. \n",
                    Utf8.toString(out.toByteArray()));
        }
        {
            InputStream in = new MetaStream(new byte[] { 1 });
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest
                    .createTestRequest(
                            "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                            Method.POST, in);
            mbus = 1;
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "true");
            nalle.getJDiscRequest().headers().add(Headers.DENY_IF_BUSY, "true");
            HttpResponse r = handler.handle(nalle);
            r.render(out);
            assertEquals("id:banana:banana::doc1 TRANSIENT_ERROR tralala \n",
                    Utf8.toString(out.toByteArray()));
        }
    }

}
