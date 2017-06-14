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
import com.yahoo.text.Utf8String;
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
public class V2ErrorsInResultTestCase {

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
        int count;

        public MockSharedSession(SourceSessionParams params) {
            super(new SharedMessageBus(new MessageBus(new MockNetwork(),
                    new MessageBusParams())), params);
            count = 0;
        }

        @Override
        public Result sendMessageBlocking(Message msg) throws InterruptedException {
            return sendMessage(msg);
        }

        @Override
        public Result sendMessage(Message msg) {
            Result r;
            ReplyHandler handler = msg.popHandler();

            switch (count++) {
            case 0:
                r = new Result(ErrorCode.FATAL_ERROR,
                        "boom");
                break;
            case 1:
                r = new Result(ErrorCode.TRANSIENT_ERROR,
                        "transient boom");
                break;
            case 2:
                final FailedReply reply = new FailedReply(msg.getContext());
                reply.addError(new Error(
                        ErrorCode.FATAL_ERROR,
                        "bad mojo, dude"));
                handler.handleReply(reply);
                r = Result.ACCEPTED;
                break;
            default:
                handler.handleReply(new MockReply(msg.getContext()));
                r = Result.ACCEPTED;
            }
            return r;
        }

    }

    private static class FailedReply extends Reply {
        Object context;

        public FailedReply(Object context) {
            this.context = context;
        }

        @Override
        public Utf8String getProtocol() {
            return null;
        }

        @Override
        public int getType() {
            return 0;
        }

        @Override
        public Object getContext() {
            return context;
        }
    }

    private static class LessConfiguredFeeder extends Feeder {

        public LessConfiguredFeeder(InputStream stream,
                                    BlockingQueue<OperationStatus> operations,
                                    ClientState storedState, FeederSettings settings,
                                    String clientId, boolean sessionIdWasGeneratedJustNow, SourceSessionParams sessionParams,
                                    SessionCache sessionCache, FeedHandler handler, ReplyHandler feedReplyHandler,
                                    String localHostname) throws Exception {
            super(stream, new MockFeedReaderFactory(), null, operations, storedState, settings, clientId, sessionIdWasGeneratedJustNow,
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
            HttpRequest nalle = HttpRequest
                    .createTestRequest(
                            "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                            Method.POST, in);
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "false");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            HttpResponse r = handler.handle(nalle);
            r.render(out);
            assertEquals("id:banana:banana::doc1 ERROR boom \n",
                         Utf8.toString(out.toByteArray()));
        }
        {
            InputStream in = new MetaStream(new byte[] { 1 });
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest
                    .createTestRequest(
                            "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                            Method.POST, in);
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "false");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            HttpResponse r = handler.handle(nalle);
            r.render(out);
            assertEquals("id:banana:banana::doc1 TRANSIENT_ERROR transient{20}boom \n",
                    Utf8.toString(out.toByteArray()));
        }
        {
            InputStream in = new MetaStream(new byte[] { 1 });
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest
                    .createTestRequest(
                            "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                            Method.POST, in);
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "true");
            HttpResponse r = handler.handle(nalle);
            r.render(out);
            assertEquals("id:banana:banana::doc1 ERROR bad{20}mojo,{20}dude \n",
                    Utf8.toString(out.toByteArray()));
        }

    }

}
