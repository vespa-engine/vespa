// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.component.Version;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.DestinationSessionParams;
import com.yahoo.messagebus.IntermediateSession;
import com.yahoo.messagebus.IntermediateSessionParams;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.Routable;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import com.yahoo.messagebus.test.SimpleReply;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


/**
 * @author Simon Thoresen Hult
 */
public class SendAdapterTestCase {

    Slobrok slobrok;
    TestServer srcServer, itrServer, dstServer;
    SourceSession srcSession;
    IntermediateSession itrSession;
    DestinationSession dstSession;
    TestProtocol srcProtocol, itrProtocol, dstProtocol;

    @BeforeEach
    public void setUp() throws ListenFailedException, UnknownHostException {
        slobrok = new Slobrok();
        dstServer = new TestServer(
                new MessageBusParams().addProtocol(dstProtocol = new TestProtocol()),
                new RPCNetworkParams().setIdentity(new Identity("dst")).setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        dstSession = dstServer.mb.createDestinationSession(
                new DestinationSessionParams().setName("session").setMessageHandler(new Receptor()));
        itrServer = new TestServer(
                new MessageBusParams().addProtocol(itrProtocol = new TestProtocol()),
                new RPCNetworkParams().setIdentity(new Identity("itr")).setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        itrSession = itrServer.mb.createIntermediateSession(
                new IntermediateSessionParams().setName("session").setMessageHandler(new Receptor()).setReplyHandler(new Receptor()));
        srcServer = new TestServer(
                new MessageBusParams().addProtocol(srcProtocol = new TestProtocol()),
                new RPCNetworkParams().setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        srcSession = srcServer.mb.createSourceSession(
                new SourceSessionParams().setTimeout(600.0).setReplyHandler(new Receptor()));
        assertTrue(srcServer.waitSlobrok("*/session", 2));
    }

    @AfterEach
    public void tearDown() {
        slobrok.stop();
        dstSession.destroy();
        dstServer.destroy();
        itrSession.destroy();
        itrServer.destroy();
        srcSession.destroy();
        srcServer.destroy();
    }

    @Test
    void requireCorrectVersionSelection() {
        assertNull(srcServer.net.getSendAdapter(new Version(4, 999)));
        assertNull(srcServer.net.getSendAdapter(new Version(5, 0)));
        assertNull(srcServer.net.getSendAdapter(new Version(6, 148)));
        assertTrue(srcServer.net.getSendAdapter(new Version(6, 149)) instanceof RPCSendV2);
        assertTrue(srcServer.net.getSendAdapter(new Version(9, 9999)) instanceof RPCSendV2);
    }

    @Test
    void requireThatMessagesCanBeSentAcrossAllSupportedVersions() {
        List<Version> versions = Arrays.asList(
                new Version(6, 149),
                new Version(9, 999)
        );

        for (Version srcVersion : versions) {
            for (Version itrVersion : versions) {
                for (Version dstVersion : versions) {
                    assertVersionedSend(srcVersion, itrVersion, dstVersion);
                }
            }
        }
    }

    private void assertVersionedSend(Version srcVersion, Version itrVersion, Version dstVersion) {
        srcServer.net.setVersion(srcVersion);
        itrServer.net.setVersion(itrVersion);
        dstServer.net.setVersion(dstVersion);

        Message msg = new SimpleMessage("foo");
        msg.getTrace().setLevel(9);
        assertTrue(srcSession.send(msg, Route.parse("itr/session dst/session")).isAccepted());
        assertNotNull(msg = ((Receptor)itrSession.getMessageHandler()).getMessage(300));
        Version minVersion = srcVersion.compareTo(itrVersion) < 0 ? srcVersion : itrVersion;
        assertEquals(minVersion, srcProtocol.lastVersion);

        assertEquals(minVersion, itrProtocol.lastVersion);
        itrSession.forward(msg);
        assertNotNull(msg = ((Receptor)dstSession.getMessageHandler()).getMessage(300));
        minVersion = itrVersion.compareTo(dstVersion) < 0 ? itrVersion : dstVersion;
        assertEquals(minVersion, itrProtocol.lastVersion);

        assertEquals(minVersion, dstProtocol.lastVersion);
        Reply reply = new SimpleReply("bar");
        reply.swapState(msg);
        dstSession.reply(reply);
        assertNotNull(reply = ((Receptor)itrSession.getReplyHandler()).getReply(300));
        assertEquals(minVersion, dstProtocol.lastVersion);

        assertEquals(minVersion, itrProtocol.lastVersion);
        itrSession.forward(reply);
        assertNotNull(((Receptor)srcSession.getReplyHandler()).getReply(300));
        minVersion = srcVersion.compareTo(itrVersion) < 0 ? srcVersion : itrVersion;
        assertEquals(minVersion, itrProtocol.lastVersion);

        assertEquals(minVersion, srcProtocol.lastVersion);
    }

    private static class TestProtocol extends SimpleProtocol {

        Version lastVersion;

        @Override
        public byte[] encode(Version version, Routable routable) {
            lastVersion = version;
            return super.encode(version, routable);
        }

        public Routable decode(Version version, byte[] payload) {
            lastVersion = version;
            return super.decode(version, payload);
        }
    }

}
