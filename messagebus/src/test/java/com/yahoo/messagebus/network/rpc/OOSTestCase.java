// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.test.OOSServer;
import com.yahoo.messagebus.network.rpc.test.OOSState;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;

import java.net.UnknownHostException;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class OOSTestCase extends junit.framework.TestCase {

    private static class MyServer extends TestServer implements MessageHandler {
        DestinationSession session;

        public MyServer(String name, Slobrok slobrok, String oosServerPattern)
                throws ListenFailedException, UnknownHostException
        {
            super(new MessageBusParams().setRetryPolicy(null).addProtocol(new SimpleProtocol()),
                  new RPCNetworkParams()
                          .setIdentity(new Identity(name))
                          .setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok))
                          .setOOSServerPattern(oosServerPattern));
            session = mb.createDestinationSession("session", true, this);
        }

        public boolean destroy() {
            session.destroy();
            return super.destroy();
        }

        public void handleMessage(Message msg) {
            session.acknowledge(msg);
        }
    }

    private static void assertError(SourceSession src, String dst, int error) {
        Message msg = new SimpleMessage("msg");
        msg.getTrace().setLevel(9);
        assertTrue(src.send(msg, Route.parse(dst)).isAccepted());
        Reply reply = ((Receptor) src.getReplyHandler()).getReply(60);
        assertNotNull(reply);
        System.out.println(reply.getTrace());
        if (error == ErrorCode.NONE) {
            assertFalse(reply.hasErrors());
        } else {
            assertTrue(reply.hasErrors());
            assertEquals(error, reply.getError(0).getCode());
        }
    }

    public void testOOS() throws ListenFailedException, UnknownHostException {
        Slobrok slobrok = new Slobrok();
        TestServer srcServer = new TestServer("src", null, slobrok, "oos/*", null);
        SourceSession srcSession = srcServer.mb.createSourceSession(new Receptor());

        MyServer dst1 = new MyServer("dst1", slobrok, null);
        MyServer dst2 = new MyServer("dst2", slobrok, null);
        MyServer dst3 = new MyServer("dst3", slobrok, null);
        MyServer dst4 = new MyServer("dst4", slobrok, null);
        MyServer dst5 = new MyServer("dst5", slobrok, null);
        assertTrue(srcServer.waitSlobrok("*/session", 5));

        // Ensure that normal sending is ok.
        assertError(srcSession, "dst1/session", ErrorCode.NONE);
        assertError(srcSession, "dst2/session", ErrorCode.NONE);
        assertError(srcSession, "dst3/session", ErrorCode.NONE);
        assertError(srcSession, "dst4/session", ErrorCode.NONE);
        assertError(srcSession, "dst5/session", ErrorCode.NONE);

        // Ensure that 2 OOS services report properly.
        OOSServer oosServer = new OOSServer(slobrok, "oos/1", new OOSState()
                .add("dst2/session", true)
                .add("dst3/session", true));
        assertTrue(srcServer.waitSlobrok("oos/*", 1));
        assertTrue(srcServer.waitState(new OOSState()
                .add("dst2/session", true)
                .add("dst3/session", true)));
        assertError(srcSession, "dst1/session", ErrorCode.NONE);
        assertError(srcSession, "dst2/session", ErrorCode.SERVICE_OOS);
        assertError(srcSession, "dst3/session", ErrorCode.SERVICE_OOS);
        assertError(srcSession, "dst4/session", ErrorCode.NONE);
        assertError(srcSession, "dst5/session", ErrorCode.NONE);

        // Ensure that 1 OOS service may come up while other stays down.
        oosServer.setState(new OOSState().add("dst2/session", true));
        assertTrue(srcServer.waitState(new OOSState()
                .add("dst2/session", true)
                .add("dst3/session", false)));
        assertError(srcSession, "dst1/session", ErrorCode.NONE);
        assertError(srcSession, "dst2/session", ErrorCode.SERVICE_OOS);
        assertError(srcSession, "dst3/session", ErrorCode.NONE);
        assertError(srcSession, "dst4/session", ErrorCode.NONE);
        assertError(srcSession, "dst5/session", ErrorCode.NONE);

        // Add another OOS server and make sure that it works properly.
        OOSServer oosServer2 = new OOSServer(slobrok, "oos/2", new OOSState()
                .add("dst4/session", true)
                .add("dst5/session", true));
        assertTrue(srcServer.waitSlobrok("oos/*", 2));
        assertTrue(srcServer.waitState(new OOSState()
                .add("dst2/session", true)
                .add("dst4/session", true)
                .add("dst5/session", true)));
        assertError(srcSession, "dst1/session", ErrorCode.NONE);
        assertError(srcSession, "dst2/session", ErrorCode.SERVICE_OOS);
        assertError(srcSession, "dst3/session", ErrorCode.NONE);
        assertError(srcSession, "dst4/session", ErrorCode.SERVICE_OOS);
        assertError(srcSession, "dst5/session", ErrorCode.SERVICE_OOS);
        oosServer2.shutdown();

        // Ensure that shutting down one OOS server will properly propagate.
        assertTrue(srcServer.waitSlobrok("oos/*", 1));
        assertTrue(srcServer.waitState(new OOSState()
                .add("dst1/session", false)
                .add("dst2/session", true)
                .add("dst3/session", false)
                .add("dst4/session", false)
                .add("dst5/session", false)));
        assertError(srcSession, "dst1/session", ErrorCode.NONE);
        assertError(srcSession, "dst2/session", ErrorCode.SERVICE_OOS);
        assertError(srcSession, "dst3/session", ErrorCode.NONE);
        assertError(srcSession, "dst4/session", ErrorCode.NONE);
        assertError(srcSession, "dst5/session", ErrorCode.NONE);

        // Now add two new OOS servers and make sure that works too.
        OOSServer oosServer3 = new OOSServer(slobrok, "oos/3", new OOSState()
                .add("dst2/session", true)
                .add("dst4/session", true));
        OOSServer oosServer4 = new OOSServer(slobrok, "oos/4", new OOSState()
                .add("dst2/session", true)
                .add("dst3/session", true)
                .add("dst5/session", true));
        assertTrue(srcServer.waitSlobrok("oos/*", 3));
        assertTrue(srcServer.waitState(new OOSState()
                .add("dst2/session", true)
                .add("dst3/session", true)
                .add("dst4/session", true)
                .add("dst5/session", true)));
        assertError(srcSession, "dst1/session", ErrorCode.NONE);
        assertError(srcSession, "dst2/session", ErrorCode.SERVICE_OOS);
        assertError(srcSession, "dst3/session", ErrorCode.SERVICE_OOS);
        assertError(srcSession, "dst4/session", ErrorCode.SERVICE_OOS);
        assertError(srcSession, "dst5/session", ErrorCode.SERVICE_OOS);

        // Modify the state of the two new servers and make sure it propagates.
        oosServer3.setState(new OOSState()
                .add("dst2/session", true));
        oosServer4.setState(new OOSState()
                .add("dst1/session", true));
        assertTrue(srcServer.waitState(new OOSState()
                .add("dst1/session", true)
                .add("dst2/session", true)
                .add("dst3/session", false)
                .add("dst4/session", false)
                .add("dst5/session", false)));
        assertError(srcSession, "dst1/session", ErrorCode.SERVICE_OOS);
        assertError(srcSession, "dst2/session", ErrorCode.SERVICE_OOS);
        assertError(srcSession, "dst3/session", ErrorCode.NONE);
        assertError(srcSession, "dst4/session", ErrorCode.NONE);
        assertError(srcSession, "dst5/session", ErrorCode.NONE);
        oosServer3.shutdown();
        oosServer4.shutdown();

        // Ensure that shutting down the two latest OOS servers works properly.
        assertTrue(srcServer.waitSlobrok("oos/*", 1));
        assertTrue(srcServer.waitState(new OOSState()
                .add("dst1/session", false)
                .add("dst2/session", true)
                .add("dst3/session", false)
                .add("dst4/session", false)
                .add("dst5/session", false)));
        assertError(srcSession, "dst1/session", ErrorCode.NONE);
        assertError(srcSession, "dst2/session", ErrorCode.SERVICE_OOS);
        assertError(srcSession, "dst3/session", ErrorCode.NONE);
        assertError(srcSession, "dst4/session", ErrorCode.NONE);
        assertError(srcSession, "dst5/session", ErrorCode.NONE);

        dst2.destroy();
        assertTrue(srcServer.waitSlobrok("*/session", 4));
        assertError(srcSession, "dst2/session", ErrorCode.SERVICE_OOS);

        srcSession.destroy();
        dst1.destroy();
        dst2.destroy();
        dst3.destroy();
        dst4.destroy();
        dst5.destroy();
    }
}
