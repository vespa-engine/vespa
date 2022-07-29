// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class TimeoutTestCase {

    private final Slobrok slobrok;
    private final TestServer srcServer, dstServer;
    private final SourceSession srcSession;
    private final DestinationSession dstSession;

    public TimeoutTestCase() throws ListenFailedException, UnknownHostException {
        slobrok = new Slobrok();
        dstServer = new TestServer(new MessageBusParams().addProtocol(new SimpleProtocol()),
                                   new RPCNetworkParams().setIdentity(new Identity("dst"))
                                                         .setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        dstSession = dstServer.mb.createDestinationSession(new DestinationSessionParams()
                                                                   .setName("session")
                                                                   .setMessageHandler(new Receptor()));
        srcServer = new TestServer(new MessageBusParams().addProtocol(new SimpleProtocol()),
                                   new RPCNetworkParams().setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        srcSession = srcServer.mb.createSourceSession(
                new SourceSessionParams().setTimeout(600.0).setReplyHandler(new Receptor()));
    }

    @BeforeEach
    public void waitForSlobrokRegistration() {
        assertTrue(srcServer.waitSlobrok("dst/session", 1));
    }

    @AfterEach
    public void destroyResources() {
        slobrok.stop();
        dstSession.destroy();
        dstServer.destroy();
        srcSession.destroy();
        srcServer.destroy();

        Message msg = ((Receptor)dstSession.getMessageHandler()).getMessage(0);
        if (msg != null) {
            msg.discard();
        }
    }

    @Test
    void requireThatMessageCanTimeout() throws ListenFailedException, UnknownHostException {
        srcSession.setTimeout(1);
        assertSend(srcSession, newMessage(), "dst/session");
        assertTimeout(((Receptor) srcSession.getReplyHandler()).getReply(60));
    }

    @Test
    void requireThatZeroTimeoutMeansImmediateTimeout() throws ListenFailedException, UnknownHostException {
        srcSession.setTimeout(0);
        assertSend(srcSession, newMessage(), "dst/session");
        assertTimeout(((Receptor) srcSession.getReplyHandler()).getReply(60));
    }

    private static void assertSend(SourceSession session, Message msg, String route) {
        assertTrue(session.send(msg, Route.parse(route)).isAccepted());
    }

    private static void assertTimeout(Reply reply) {
        assertNotNull(reply);
        assertTrue(hasError(reply, ErrorCode.TIMEOUT), reply.getTrace().toString());
    }

    private static Message newMessage() {
        Message msg = new SimpleMessage("msg");
        msg.getTrace().setLevel(9);
        return msg;
    }

    private static boolean hasError(Reply reply, int errorCode) {
        for (int i = 0; i < reply.getNumErrors(); ++i) {
            if (reply.getError(i).getCode() == errorCode) {
                return true;
            }
        }
        return false;
    }
}
