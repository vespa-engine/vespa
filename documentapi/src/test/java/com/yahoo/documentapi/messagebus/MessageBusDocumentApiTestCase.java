// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentOperationParameters;
import com.yahoo.documentapi.Response;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;

import com.yahoo.documentapi.test.AbstractDocumentApiTestCase;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.AllPassThrottlePolicy;
import com.yahoo.messagebus.DynamicThrottlePolicy;
import com.yahoo.messagebus.ThrottlePolicy;
import com.yahoo.messagebus.network.Identity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author Einar M R Rosenvinge
 */
public class MessageBusDocumentApiTestCase extends AbstractDocumentApiTestCase {

    private Slobrok slobrok;
    private Destination destination;
    private DocumentAccess access;

    @Override
    protected DocumentAccess access() {
        return access;
    }

    @Before
    public void setUp() throws ListenFailedException {
        slobrok = new Slobrok();
        String slobrokConfigId =
                "raw:slobrok[1]\n" + "slobrok[0].connectionspec tcp/localhost:" + slobrok.port() + "\n";

        MessageBusParams params = new MessageBusParams();
        params.getRPCNetworkParams().setIdentity(new Identity("test/feeder"));
        params.getRPCNetworkParams().setSlobrokConfigId(slobrokConfigId);
        params.setDocumentManagerConfigId("file:src/test/cfg/documentmanager.cfg");
        params.setRouteName("Route");
        params.setRouteNameForGet("Route");
        params.setRoutingConfigId("file:src/test/cfg/messagebus.cfg");
        params.setTraceLevel(9);
        access = new MessageBusDocumentAccess(params);

        destination = new Destination(slobrokConfigId, params.getDocumentManagerConfigId());
    }

    @After
    public void tearDown() {
        access.shutdown();
        destination.shutdown();
        slobrok.stop();
    }

    @Test
    public void requireThatVisitorSessionWorksWithMessageBus() throws ParseException, InterruptedException {
        VisitorParameters parameters = new VisitorParameters("id.user==1234");
        parameters.setRoute("Route");
        VisitorSession session = ((MessageBusDocumentAccess)access).createVisitorSession(parameters);
        boolean ok = session.waitUntilDone(60*5*1000);
        assertTrue(ok);
        session.destroy();

        // TODO(vekterli): test remote-to-local message sending as well?
        // TODO(vekterli): test DocumentAccess shutdown during active ession?
    }

    @Test
    public void requireThatTimeoutWorks() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Response> response = new AtomicReference<>();
        AsyncSession session = access().createAsyncSession(new AsyncParameters());
        DocumentType type = access().getDocumentTypeManager().getDocumentType("music");
        Document doc1 = new Document(type, new DocumentId("id:ns:music::1"));

        destination.discard.set(true);
        assertTrue(session.put(new DocumentPut(doc1),
                               DocumentOperationParameters.parameters()
                                                          .withResponseHandler(result -> {
                                                              response.set(result);
                                                              latch.countDown();
                                                          })
                                                          .withDeadline(Instant.now().plusMillis(100)))
                          .isSuccess());
        assertTrue(latch.await(60, TimeUnit.SECONDS));
        assertEquals(Response.Outcome.TIMEOUT, response.get().outcome());
        session.destroy();
    }

    @Test
    public void requireThatDefaultThrottlePolicyIsDynamicAndShared() {
        MessageBusAsyncSession mbusSessionA = (MessageBusAsyncSession) access().createAsyncSession(new AsyncParameters());
        assertTrue(mbusSessionA.getThrottlePolicy() instanceof DynamicThrottlePolicy);
        MessageBusAsyncSession mbusSessionB = (MessageBusAsyncSession) access().createAsyncSession(new AsyncParameters());
        assertSame(mbusSessionA.getThrottlePolicy(), mbusSessionB.getThrottlePolicy());
        mbusSessionB.destroy();
        mbusSessionA.destroy();
    }

    @Test
    public void requireThatThrottlePolicyCanBeConfigured() {
        var asyncParams = new AsyncParameters();
        ThrottlePolicy allPass = new AllPassThrottlePolicy();
        asyncParams.setThrottlePolicy(allPass);
        MessageBusAsyncSession mbusSession = (MessageBusAsyncSession) access().createAsyncSession(asyncParams);
        assertSame(allPass, mbusSession.getThrottlePolicy());
        mbusSession.destroy();
    }

}
