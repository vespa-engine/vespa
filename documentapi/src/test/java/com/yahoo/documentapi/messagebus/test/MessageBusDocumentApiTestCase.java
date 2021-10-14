// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.test;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentOperationParameters;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.Response;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusParams;
import com.yahoo.documentapi.messagebus.protocol.CreateVisitorReply;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.test.AbstractDocumentApiTestCase;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.network.Identity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
        params.setSourceSessionParams(new SourceSessionParams().setThrottlePolicy(null));
        access = new MessageBusDocumentAccess(params);

        destination = new VisitableDestination(slobrokConfigId, params.getDocumentManagerConfigId());
    }

    @After
    public void tearDown() {
        access.shutdown();
        destination.shutdown();
        slobrok.stop();
    }

    private static class VisitableDestination extends Destination {
        private VisitableDestination(String slobrokConfigId, String documentManagerConfigId) {
            super(slobrokConfigId, documentManagerConfigId);
        }

        public void handleMessage(Message msg) {
            if (msg.getType() == DocumentProtocol.MESSAGE_CREATEVISITOR) {
                Reply reply = ((DocumentMessage)msg).createReply();
                msg.swapState(reply);
                CreateVisitorReply visitorReply = (CreateVisitorReply)reply;
                visitorReply.setLastBucket(ProgressToken.FINISHED_BUCKET);
                sendReply(reply);
            } else {
                super.handleMessage(msg);
            }
        }
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
        assertTrue(session.put(new DocumentPut(doc1),
                               DocumentOperationParameters.parameters()
                                                          .withResponseHandler(result -> {
                                                              response.set(result);
                                                              latch.countDown();
                                                          })
                                                          .withDeadline(Instant.now().minusSeconds(1)))
                          .isSuccess());
        assertTrue(latch.await(60, TimeUnit.SECONDS));
        assertNotNull(response.get());
        assertEquals(Response.Outcome.TIMEOUT, response.get().outcome());
        session.destroy();
    }

}
