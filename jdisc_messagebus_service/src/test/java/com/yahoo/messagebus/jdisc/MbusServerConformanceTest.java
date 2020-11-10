// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.yahoo.jdisc.test.ServerProviderConformanceTest;
import com.yahoo.messagebus.DestinationSessionParams;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.network.local.LocalNetwork;
import com.yahoo.messagebus.network.local.LocalWire;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.shared.ServerSession;
import com.yahoo.messagebus.shared.SharedMessageBus;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import org.hamcrest.Matcher;
import org.junit.Ignore;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static com.yahoo.messagebus.ErrorCode.APP_FATAL_ERROR;
import static com.yahoo.messagebus.ErrorCode.SESSION_BUSY;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Simon Thoresen Hult
 */
public class MbusServerConformanceTest extends ServerProviderConformanceTest {

    /* Many of the "success" expectations here (may) seem odd. But this is the current behavior of the
     * messagebus server. We should probably look into whether the behavior is correct in all cases.
     */

    @Override
    @Test
    public void testContainerNotReadyException() throws Throwable {
        new TestRunner().setRequestTimeout(100, TimeUnit.MILLISECONDS)
                        .expectError(is(SESSION_BUSY))
                        .executeAndClose();
    }

    @Override
    @Test
    public void testBindingSetNotFoundException() throws Throwable {
        new TestRunner().expectError(is(APP_FATAL_ERROR))
                        .executeAndClose();
    }

    @Override
    @Test
    public void testNoBindingSetSelectedException() throws Throwable {
        new TestRunner().expectError(is(APP_FATAL_ERROR))
                        .executeAndClose();
    }

    @Override
    @Test
    public void testBindingNotFoundException() throws Throwable {
        new TestRunner().expectError(is(APP_FATAL_ERROR))
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestHandlerWithSyncCloseResponse() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestHandlerWithSyncWriteResponse() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestHandlerWithSyncHandleResponse() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestHandlerWithAsyncHandleResponse() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestException() throws Throwable {
        new TestRunner().expectError(is(APP_FATAL_ERROR))
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestExceptionWithSyncCloseResponse() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestExceptionWithSyncWriteResponse() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestNondeterministicExceptionWithSyncHandleResponse() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestExceptionBeforeResponseWriteWithSyncHandleResponse() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestExceptionAfterResponseWriteWithSyncHandleResponse() throws Throwable {
    }

    @Override
    @Test
    public void testRequestNondeterministicExceptionWithAsyncHandleResponse() throws Throwable {
        new TestRunner().executeAndClose();
    }

    @Override
    @Test
    public void testRequestExceptionBeforeResponseWriteWithAsyncHandleResponse() throws Throwable {
        new TestRunner().expectError(is(APP_FATAL_ERROR))
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestExceptionAfterResponseCloseNoContentWithAsyncHandleResponse() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestExceptionAfterResponseWriteWithAsyncHandleResponse() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteWithSyncCompletion() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteWithAsyncCompletion() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteWithNondeterministicSyncFailure() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteWithSyncFailureBeforeResponseWrite() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteWithSyncFailureAfterResponseWrite() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteWithNondeterministicAsyncFailure() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteWithAsyncFailureBeforeResponseWrite() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteWithAsyncFailureAfterResponseWrite() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteWithAsyncFailureAfterResponseCloseNoContent() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteNondeterministicException() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionBeforeResponseWrite() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionAfterResponseWrite() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionAfterResponseCloseNoContent() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteNondeterministicExceptionWithSyncCompletion() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionBeforeResponseWriteWithSyncCompletion() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionAfterResponseWriteWithSyncCompletion() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionAfterResponseCloseNoContentWithSyncCompletion() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteNondeterministicExceptionWithAsyncCompletion() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionBeforeResponseWriteWithAsyncCompletion() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionAfterResponseWriteWithAsyncCompletion() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionAfterResponseCloseNoContentWithAsyncCompletion() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionWithNondeterministicSyncFailure() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionWithSyncFailureBeforeResponseWrite() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionWithSyncFailureAfterResponseWrite() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionWithSyncFailureAfterResponseCloseNoContent() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionWithNondeterministicAsyncFailure() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionWithAsyncFailureBeforeResponseWrite() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionWithAsyncFailureAfterResponseWrite() throws Throwable {
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentWriteExceptionWithAsyncFailureAfterResponseCloseNoContent() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseWithSyncCompletion() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseWithAsyncCompletion() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseWithNondeterministicSyncFailure() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseWithSyncFailureBeforeResponseWrite() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentCloseWithSyncFailureAfterResponseWrite() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseWithSyncFailureAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseWithNondeterministicAsyncFailure() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseWithAsyncFailureBeforeResponseWrite() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentCloseWithAsyncFailureAfterResponseWrite() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseWithAsyncFailureAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicException() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWrite() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentCloseExceptionAfterResponseWrite() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContent() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithSyncCompletion() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithSyncCompletion() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentCloseExceptionAfterResponseWriteWithSyncCompletion() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithSyncCompletion() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithAsyncCompletion() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithAsyncCompletion() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentCloseExceptionAfterResponseWriteWithAsyncCompletion() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithAsyncCompletion() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithSyncFailure() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithSyncFailure() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentCloseExceptionAfterResponseWriteWithSyncFailure() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithSyncFailure() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseNondeterministicExceptionWithAsyncFailure() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionBeforeResponseWriteWithAsyncFailure() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testRequestContentCloseExceptionAfterResponseWriteWithAsyncFailure() throws Throwable {
    }

    @Override
    @Test
    public void testRequestContentCloseExceptionAfterResponseCloseNoContentWithAsyncFailure() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    @Ignore // N/A: The messagebus protocol does not have content.
    public void testResponseWriteCompletionException() throws Throwable {
    }

    @Override
    @Test
    public void testResponseCloseCompletionException() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    @Override
    @Test
    public void testResponseCloseCompletionExceptionNoContent() throws Throwable {
        new TestRunner().expectSuccess()
                        .executeAndClose();
    }

    private class TestRunner implements Adapter<MbusServer, MyClient, Reply> {

        final LocalWire wire = new LocalWire();
        final SharedMessageBus mbus;
        final ServerSession session;
        Matcher<Integer> expectedError = null;
        boolean successExpected = false;
        long timeoutMillis = TimeUnit.SECONDS.toMillis(60);

        TestRunner() {
            this(new MessageBusParams().addProtocol(new SimpleProtocol()),
                 new DestinationSessionParams());
        }

        TestRunner(MessageBusParams mbusParams, DestinationSessionParams sessionParams) {
            this.mbus = new SharedMessageBus(new MessageBus(new LocalNetwork(wire), mbusParams));
            this.session = mbus.newDestinationSession(sessionParams);
        }

        TestRunner setRequestTimeout(long timeout, TimeUnit unit) {
            timeoutMillis = unit.toMillis(timeout);
            return this;
        }

        TestRunner expectError(Matcher<Integer> matcher) {
            assertThat(successExpected, is(false));
            expectedError = matcher;
            return this;
        }

        TestRunner expectSuccess() {
            assertThat(expectedError, is(nullValue()));
            successExpected = true;
            return this;
        }

        @Override
        public Module newConfigModule() {
            return new AbstractModule() {

                @Override
                protected void configure() {
                    bind(ServerSession.class).toInstance(session);
                }
            };
        }

        @Override
        public Class<MbusServer> getServerProviderClass() {
            return MbusServer.class;
        }

        @Override
        public MyClient newClient(MbusServer server) throws Throwable {
            return new MyClient(wire, server.connectionSpec());
        }

        @Override
        public Reply executeRequest(MyClient client, boolean withRequestContent) throws Throwable {
            // This protocol doesn't have the concept of "request content", so if we are asked to send any, it's a bug.
            assertThat(withRequestContent, is(false));

            final SimpleMessage msg = new SimpleMessage("foo");
            msg.getTrace().setLevel(9);
            msg.setRoute(client.route);
            msg.setTimeRemaining(timeoutMillis);
            assertThat("client.session.send(msg).isAccepted()",
                       client.session.send(msg).isAccepted(), is(true));

            final Reply reply = client.replies.poll(60, TimeUnit.SECONDS);
            assertThat("reply != null", reply, notNullValue());
            return reply;
        }

        @Override
        public Iterable<ByteBuffer> newResponseContent() {
            return Collections.emptyList();
        }

        @Override
        public void validateResponse(Reply reply) throws Throwable {
            final String trace = String.valueOf(reply.getTrace());
            if (expectedError != null) {
                assertThat(reply.hasErrors(), is(true));
                final int error = reply.getError(0).getCode();
                assertThat(trace, error, expectedError);
            }
            if (successExpected) {
                assertThat(trace, reply.hasErrors(), is(false));
            }
        }

        void executeAndClose() throws Throwable {
            runTest(this);
            session.release();
            mbus.release();
        }
    }

    public static class MyClient implements Closeable, ReplyHandler {

        final BlockingDeque<Reply> replies = new LinkedBlockingDeque<>();
        final MessageBus mbus;
        final Route route;
        final SourceSession session;

        MyClient(LocalWire wire, String connectionSpec) {
            this.mbus = new MessageBus(new LocalNetwork(wire),
                                       new MessageBusParams().addProtocol(new SimpleProtocol()));
            this.session = mbus.createSourceSession(new SourceSessionParams().setReplyHandler(this));
            this.route = Route.parse(connectionSpec);
        }

        @Override
        public void close() throws IOException {
            session.destroy();
            mbus.destroy();
        }

        @Override
        public void handleReply(Reply reply) {
            replies.addLast(reply);
        }
    }
}
