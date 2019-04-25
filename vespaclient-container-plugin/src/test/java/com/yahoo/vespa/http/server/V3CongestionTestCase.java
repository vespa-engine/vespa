// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.documentapi.metrics.DocumentApiMetrics;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.jdisc.References;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.Result;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.shared.SharedMessageBus;
import com.yahoo.messagebus.shared.SharedSourceSession;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.http.client.core.Headers;
import com.yahoo.vespaxmlparser.FeedOperation;
import com.yahoo.vespaxmlparser.MockFeedReaderFactory;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;


import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;


public class V3CongestionTestCase {
    AtomicInteger threadsAvail = new AtomicInteger(10);
    AtomicInteger requests = new AtomicInteger(0);


    static class ClientFeederWithMocks extends ClientFeederV3 {

        private final DocumentOperationMessageV3 docOp;

        ClientFeederWithMocks(ReferencedResource<SharedSourceSession> sourceSession, FeedReaderFactory feedReaderFactory, DocumentTypeManager docTypeManager, String clientId, Metric metric, ReplyHandler feedReplyHandler, AtomicInteger threadsAvailableForFeeding) {
            super(sourceSession, feedReaderFactory, docTypeManager, clientId, metric, feedReplyHandler, threadsAvailableForFeeding);
            // The operation to return from the client feeder.
            docOp = DocumentOperationMessageV3.newRemoveMessage(FeedOperation.INVALID, "operation id");

        }

        @Override
        protected DocumentOperationMessageV3 getNextMessage(
                String operationId, InputStream requestInputStream, FeederSettings settings) throws Exception {
            while (true) {
                int data = requestInputStream.read();
                if (data == -1 || data == (char)'\n') {
                    break;
                }
            }
            return docOp;
        }
    }

    final static int NUMBER_OF_QUEUE_FULL_RESPONSES = 5;

    ClientFeederV3 clientFeederV3;
    HttpRequest request;

    @Before
    public void setup() {
        // Set up a request to be used from the tests.
        InputStream in = new MetaStream(new byte[] { 1 });
        request = HttpRequest
                .createTestRequest(
                        "http://foo.bar:19020/reserved-for-internal-use/feedapi",
                        com.yahoo.jdisc.http.HttpRequest.Method.POST, in);
        request.getJDiscRequest().headers().add(Headers.VERSION, "3");
        request.getJDiscRequest().headers().add(Headers.CLIENT_ID, "clientId");


        // Create a mock that does not parse the message, only reads the rest of the line. Makes it easier
        // to write tests. It uses a mock for message bus.
        clientFeederV3 = new ClientFeederWithMocks(
                retainMockSession(new SourceSessionParams(), requests),
                new MockFeedReaderFactory(),
                null /*DocTypeManager*/,
                "clientID",
                null/*metric*/,
                new FeedReplyReader(null/*metric*/, new DocumentApiMetrics(MetricReceiver.nullImplementation, "tester")),
                threadsAvail);
    }

    // A mock for message bus that can simulate blocking requests.
    private static class MockSharedSession extends SharedSourceSession {
        boolean queuFull = true;
        AtomicInteger requests;

        public MockSharedSession(SourceSessionParams params, AtomicInteger requests) {
            super(new SharedMessageBus(new MessageBus(new MockNetwork(),
                    new MessageBusParams())), params);
            this.requests = requests;
        }

        @Override
        public Result sendMessageBlocking(Message msg) throws InterruptedException {
            return sendMessage(msg);
        }

        @Override
        public Result sendMessage(Message msg) {
            ReplyHandler handler = msg.popHandler();
            if (queuFull) {
                requests.incrementAndGet();
                // Disable queue full after some attempts
                if (requests.get() == NUMBER_OF_QUEUE_FULL_RESPONSES) {
                    queuFull = false;
                }
                Result r = new Result(ErrorCode.SEND_QUEUE_FULL, "queue full");
                return r;
            }

            handler.handleReply(new MockReply(msg.getContext()));
            return Result.ACCEPTED;
        }
    }

    ReferencedResource<SharedSourceSession> retainMockSession(
            SourceSessionParams sessionParams,
            AtomicInteger requests) {
        final SharedSourceSession session = new MockSharedSession(sessionParams, requests);
        return new ReferencedResource<>(session, References.fromResource(session));
    }

    @Test
    public void testRetriesWhenThreadsAvailable() throws IOException {
        request.getJDiscRequest().headers().add(Headers.DENY_IF_BUSY, "true");
        threadsAvail.set(10);

        clientFeederV3.handleRequest(request);
        assertTrue(requests.get() == NUMBER_OF_QUEUE_FULL_RESPONSES);
    }

    @Test
    public void testNoRetriesWhenNoThreadsAvailable() throws IOException {
        request.getJDiscRequest().headers().add(Headers.DENY_IF_BUSY, "true");
        threadsAvail.set(0);

        clientFeederV3.handleRequest(request);
        assertTrue(requests.get() == 1);
    }

    @Test
    public void testRetriesWhenNoThreadsAvailableButNoDenyIfBusy() throws IOException {
        request.getJDiscRequest().headers().add(Headers.DENY_IF_BUSY, "false");
        threadsAvail.set(0);

        clientFeederV3.handleRequest(request);
        assertTrue(requests.get() == NUMBER_OF_QUEUE_FULL_RESPONSES);
    }
}
