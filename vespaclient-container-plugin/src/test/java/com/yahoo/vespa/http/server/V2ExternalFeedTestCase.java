// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.messagebus.SessionCache;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.jdisc.References;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.Result;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.shared.SharedMessageBus;
import com.yahoo.messagebus.shared.SharedSourceSession;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.http.client.config.FeedParams.DataFormat;
import com.yahoo.vespa.http.client.core.Headers;
import com.yahoo.vespa.http.client.core.OperationStatus;
import com.yahoo.vespaxmlparser.MockFeedReaderFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Check FeedHandler APIs.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class V2ExternalFeedTestCase {

    LessConfiguredHandler handler;
    ExecutorService workers;
    Level logLevel;
    Logger logger;
    boolean initUseParentHandlers;
    LogBuffer logChecker;

    @Before
    public void setUp() throws Exception {
        workers = Executors.newCachedThreadPool();
        handler = new LessConfiguredHandler(workers);
        logger = Logger.getLogger(Feeder.class.getName());
        logLevel = logger.getLevel();
        logger.setLevel(Level.ALL);
        initUseParentHandlers = logger.getUseParentHandlers();
        logChecker = new LogBuffer();
        logger.setUseParentHandlers(false);
        logger.addHandler(logChecker);
    }

    @After
    public void tearDown() throws Exception {
        handler.destroy();
        workers.shutdown();
        logger.setLevel(logLevel);
        logger.removeHandler(logChecker);
        logger.setUseParentHandlers(initUseParentHandlers);
    }

    private static class LogBuffer extends Handler {
        public final BlockingQueue<LogRecord> records = new LinkedBlockingQueue<>();

        @Override
        public void publish(LogRecord record) {
            try {
                records.put(record);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }
    }

    private static class LessConfiguredHandler extends FeedHandler {
        volatile DataFormat lastFormatSeen;

        public LessConfiguredHandler(Executor executor) throws Exception {
            super(executor, null, null, new DummyMetric(), AccessLog.voidAccessLog(), null, MetricReceiver.nullImplementation);
        }

        @Override
        protected Feeder createFeeder(HttpRequest request,
                                      InputStream requestInputStream,
                                      BlockingQueue<OperationStatus> operations,
                                      String clientId,
                                      boolean sessionIdWasGeneratedJustNow, int protocolVersion)
                throws Exception {
            LessConfiguredFeeder f = new LessConfiguredFeeder(requestInputStream, operations,
                    popClient(clientId), new FeederSettings(request), clientId, sessionIdWasGeneratedJustNow,
                    sourceSessionParams(request), null, this, this.feedReplyHandler, "ourHostname");
            lastFormatSeen = f.settings.dataFormat;
            return f;
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
                mockReply.addError(new com.yahoo.messagebus.Error(123, "Could not feed this"));
            }
            if (msg instanceof PutDocumentMessage) {
                assert(msg.getTrace().getLevel() == 4);
                assert(((PutDocumentMessage) msg).getPriority().name().equals("LOWEST"));
            }
            handler.handleReply(mockReply);
            return Result.ACCEPTED;
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
    public final void test() throws IOException, InterruptedException {
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
            InputStream in = new MetaStream(new byte[]{1, 3, 2});
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest.createTestRequest(
                    "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                    Method.POST, in);
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.TIMEOUT, "1000000000");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            nalle.getJDiscRequest().headers().add(Headers.PRIORITY, "LOWEST");
            nalle.getJDiscRequest().headers().add(Headers.TRACE_LEVEL, "4");
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "true");
            HttpResponse r = handler.handle(nalle);
            r.render(out);
            assertEquals("id:banana:banana::doc1 OK Document{20}processed. \n"
                         + "id:banana:banana::doc1 OK Document{20}processed. \n"
                         + "id:banana:banana::doc1 OK Document{20}processed. \n",
                         Utf8.toString(out.toByteArray()));
            assertEquals("text/plain", r.getContentType());
            assertEquals(StandardCharsets.US_ASCII.name(), r.getCharacterEncoding());
            assertEquals(7, logChecker.records.size());
            String actualHandshake = logChecker.records.take().getMessage();
            assertThat(actualHandshake, actualHandshake.matches("Handshake completed for client (-?)(.+?)-#(.*?)\\."), is(true));
            assertEquals("Successfully deserialized document id: id:banana:banana::doc1",
                         logChecker.records.take().getMessage());
            assertEquals("Sent message successfully, document id: id:banana:banana::doc1",
                         logChecker.records.take().getMessage());
        }

        //test session ID without #, i.e. something fishy related to VIPs is going on
        sessionId = "something";

        {
            InputStream in = new MetaStream(new byte[]{1, 3, 2});
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest.createTestRequest(
                    "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                    Method.POST, in);
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.TIMEOUT, "1000000000");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "true");
            nalle.getJDiscRequest().headers().add(Headers.PRIORITY, "LOWEST");
            nalle.getJDiscRequest().headers().add(Headers.TRACE_LEVEL, "4");
            nalle.getJDiscRequest().headers().add(Headers.TRACE_LEVEL, "2");

            HttpResponse r = handler.handle(nalle);
            r.render(out);
            String expectedErrorMsg = "Got request from client with id 'something', but found no session for this client.";
	    assertThat(Utf8.toString(out.toByteArray()), containsString(expectedErrorMsg));
            assertEquals("text/plain", r.getContentType());
            assertEquals(StandardCharsets.UTF_8.name(), r.getCharacterEncoding());
        }

        //test session ID with trailing # but no hostname
        sessionId = "something#";

        {
            InputStream in = new MetaStream(new byte[]{1, 3, 2});
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest.createTestRequest(
                    "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                    Method.POST, in);
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.TIMEOUT, "1000000000");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "true");
            nalle.getJDiscRequest().headers().add(Headers.PRIORITY, "LOWEST");
            nalle.getJDiscRequest().headers().add(Headers.TRACE_LEVEL, "4");
            HttpResponse r = handler.handle(nalle);
            r.render(out);
            String expectedErrorMsg = "Got request from client with id 'something#', but found no session for this client.";
            assertThat(Utf8.toString(out.toByteArray()), containsString(expectedErrorMsg));
            assertEquals("text/plain", r.getContentType());
            assertEquals(StandardCharsets.UTF_8.name(), r.getCharacterEncoding());
        }

        //test session ID with trailing # and some unknown hostname at the end
        sessionId = "something#thisHostnameDoesNotExistAnywhere";

        {
            InputStream in = new MetaStream(new byte[]{1, 3, 2});
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest.createTestRequest(
                    "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                    Method.POST, in);
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.TIMEOUT, "1000000000");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "true");
            nalle.getJDiscRequest().headers().add(Headers.PRIORITY, "LOWEST");
            nalle.getJDiscRequest().headers().add(Headers.TRACE_LEVEL, "4");
            HttpResponse r = handler.handle(nalle);
            r.render(out);
            String expectedErrorMsg = "Got request from client with id 'something#thisHostnameDoesNotExistAnywhere', " +
                                      "but found no session for this client. Session was originally established " +
                                      "towards host thisHostnameDoesNotExistAnywhere, but our hostname is " +
                                      "ourHostname.";
            assertThat(Utf8.toString(out.toByteArray()), containsString(expectedErrorMsg));
            assertEquals("text/plain", r.getContentType());
            assertEquals(StandardCharsets.UTF_8.name(), r.getCharacterEncoding());
        }

        //test session ID with trailing # and some unknown hostname at the end
        sessionId = "something#ourHostname";

        {
            InputStream in = new MetaStream(new byte[]{1, 3, 2});
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest.createTestRequest(
                    "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                    Method.POST, in);
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.TIMEOUT, "1000000000");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            nalle.getJDiscRequest().headers().add(Headers.PRIORITY, "LOWEST");
            nalle.getJDiscRequest().headers().add(Headers.TRACE_LEVEL, "4");
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "true");
            HttpResponse r = handler.handle(nalle);
            r.render(out);
            assertEquals("id:banana:banana::doc1 OK Document{20}processed. \n" +
                         "id:banana:banana::doc1 OK Document{20}processed. \n" +
                         "id:banana:banana::doc1 OK Document{20}processed. \n",
                         Utf8.toString(out.toByteArray()));
            assertEquals("text/plain", r.getContentType());
            assertEquals(StandardCharsets.US_ASCII.name(), r.getCharacterEncoding());
            Thread.sleep(1000);
        }
    }

    @Test
    public final void testFailedReading() throws IOException {
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
            InputStream in = new MetaStream(new byte[] { 4 });
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest.createTestRequest(
                    "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                    Method.POST, in);
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "true");
            HttpResponse r = handler.handle(nalle);
            r.render(out);
            assertEquals("id:banana:banana::doc1 ERROR Could{20}not{20}feed{20}this \n",
                         Utf8.toString(out.toByteArray()));
        }
    }

    @Test
    public final void testCleaningDoesNotBlowUp() throws IOException {
        InputStream in = new MetaStream(new byte[] { 1 });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpRequest nalle = HttpRequest.createTestRequest(
                "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                Method.POST, in);
        nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
        nalle.getJDiscRequest().headers().add(Headers.DRAIN, "false");
        HttpResponse r = handler.handle(nalle);
        r.render(out);
        assertEquals("",
                Utf8.toString(out.toByteArray()));
        handler.forceRunCleanClients();
    }

    @Test
    public final void testMockNetworkDoesNotBlowUp() {
        Network n = new MockNetwork();
        n.registerSession(null);
        n.unregisterSession(null);
        assertTrue(n.allocServiceAddress(null));
        n.freeServiceAddress(null);
        n.send(null, null);
        assertNull(n.getConnectionSpec());
        assertNull(n.getMirror());
    }

    @Test
    public final void testMockReplyDoesNotBlowUp() {
        MockReply r = new MockReply(null);
        assertNull(r.getProtocol());
        assertEquals(0, r.getType());
        assertFalse(r.hasFatalErrors());
    }

    @Test
    public final void testFlush() throws IOException {
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
            InputStream in = new MetaStream(new byte[] { 1, 1, 1, 1, 1, 1, 1});
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest.createTestRequest(
                    "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                    Method.POST, in);
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            nalle.getJDiscRequest().headers().add(Headers.PRIORITY, "LOWEST");
            nalle.getJDiscRequest().headers().add(Headers.TRACE_LEVEL, "4");
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "true");
            HttpResponse r = handler.handle(nalle);
            r.render(out);
            assertEquals("id:banana:banana::doc1 OK Document{20}processed. \n"
                         + "id:banana:banana::doc1 OK Document{20}processed. \n"
                         + "id:banana:banana::doc1 OK Document{20}processed. \n"
                         + "id:banana:banana::doc1 OK Document{20}processed. \n"
                         + "id:banana:banana::doc1 OK Document{20}processed. \n"
                         + "id:banana:banana::doc1 OK Document{20}processed. \n"
                         + "id:banana:banana::doc1 OK Document{20}processed. \n",
                         Utf8.toString(out.toByteArray()));
        }
    }

    @Test
    public final void testIllegalVersion() throws IOException {
        InputStream in = new MetaStream(new byte[] { 1 });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HttpRequest nalle = HttpRequest.createTestRequest(
                "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                Method.POST, in);
        nalle.getJDiscRequest().headers()
                .add(Headers.VERSION, Integer.toString(Integer.MAX_VALUE));
        HttpResponse r = handler.handle(nalle);
        r.render(out);
        assertEquals(Headers.HTTP_NOT_ACCEPTABLE, r.getStatus());
    }

    @Test
    public final void testSettings() {
        HttpRequest nalle = HttpRequest.createTestRequest(
                "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                Method.POST);
        nalle.getJDiscRequest().headers().add(Headers.DRAIN, "false");
        nalle.getJDiscRequest().headers().add(Headers.ROUTE, "bamse brakar");
        nalle.getJDiscRequest().headers().add(Headers.DENY_IF_BUSY, "true");
        FeederSettings settings = new FeederSettings(nalle);
        assertEquals(false, settings.drain);
        assertEquals(2, settings.route.getNumHops());
        assertEquals(true, settings.denyIfBusy);
    }

    @Test
    public final void testJsonInputFormat() throws IOException, InterruptedException {
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
            InputStream in = new MetaStream(new byte[]{1, 3, 2});
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HttpRequest nalle = HttpRequest.createTestRequest(
                    "http://test4-steinar:19020/reserved-for-internal-use/feedapi",
                    Method.POST, in);
            nalle.getJDiscRequest().headers().add(Headers.VERSION, "2");
            nalle.getJDiscRequest().headers().add(Headers.TIMEOUT, "1000000000");
            nalle.getJDiscRequest().headers().add(Headers.SESSION_ID, sessionId);
            nalle.getJDiscRequest().headers().add(Headers.DATA_FORMAT, DataFormat.JSON_UTF8.name());
            nalle.getJDiscRequest().headers().add(Headers.PRIORITY, "LOWEST");
            nalle.getJDiscRequest().headers().add(Headers.TRACE_LEVEL, "4");
            nalle.getJDiscRequest().headers().add(Headers.DRAIN, "true");
            HttpResponse r = handler.handle(nalle);
            r.render(out);
            assertEquals("id:banana:banana::doc1 OK Document{20}processed. \n"
                         + "id:banana:banana::doc1 OK Document{20}processed. \n"
                         + "id:banana:banana::doc1 OK Document{20}processed. \n",
                         Utf8.toString(out.toByteArray()));
            assertEquals("text/plain", r.getContentType());
            assertEquals(StandardCharsets.US_ASCII.name(), r.getCharacterEncoding());
            assertEquals(7, logChecker.records.size());
            String actualHandshake = logChecker.records.take().getMessage();
            assertThat(actualHandshake, actualHandshake.matches("Handshake completed for client (-?)(.+?)-#(.*?)\\."), is(true));
            assertEquals("Successfully deserialized document id: id:banana:banana::doc1",
                         logChecker.records.take().getMessage());
            assertEquals("Sent message successfully, document id: id:banana:banana::doc1",
                         logChecker.records.take().getMessage());
            assertSame(DataFormat.JSON_UTF8, handler.lastFormatSeen);
        }
    }

}
