// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.component.Vtag;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.network.rpc.test.TestServer;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.test.Receptor;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import com.yahoo.messagebus.test.SimpleReply;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen
 */
public class SendProxyTestCase {

    Slobrok slobrok;
    TestServer srcServer, dstServer;
    SourceSession srcSession;
    DestinationSession dstSession;

    @Before
    public void setUp() throws ListenFailedException {
        slobrok = new Slobrok();
        dstServer = new TestServer(new MessageBusParams().addProtocol(new SimpleProtocol()),
                                   new RPCNetworkParams().setIdentity(new Identity("dst")).setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        dstSession = dstServer.mb.createDestinationSession(new DestinationSessionParams().setName("session").setMessageHandler(new Receptor()));
        srcServer = new TestServer(new MessageBusParams().addProtocol(new SimpleProtocol()),
                                   new RPCNetworkParams().setSlobrokConfigId(TestServer.getSlobrokConfig(slobrok)));
        srcSession = srcServer.mb.createSourceSession(
                new SourceSessionParams().setTimeout(600.0).setThrottlePolicy(null).setReplyHandler(new Receptor()));
        assertTrue(srcServer.waitSlobrok("dst/session", 1));
    }

    @After
    public void tearDown() {
        slobrok.stop();
        dstSession.destroy();
        dstServer.destroy();
        srcSession.destroy();
        srcServer.destroy();
    }

    @Test
    public void testTraceByLogLevel() {
        Logger log = Logger.getLogger(SendProxy.class.getName());
        LogHandler logHandler = new LogHandler();
        log.addHandler(logHandler);

        log.setLevel(LogLevel.INFO);
        sendMessage(0, null);
        assertNull(logHandler.trace);

        log.setLevel(LogLevel.DEBUG);
        sendMessage(0, null);
        assertNull(logHandler.trace);

        sendMessage(1, new Error(ErrorCode.FATAL_ERROR, "err"));
        assertNull(logHandler.trace);

        sendMessage(0, new Error(ErrorCode.FATAL_ERROR, "err"));
        assertEquals("Trace for reply with error(s):\n" +
                     "<trace>\n" +
                     "    <trace>\n" +
                     "        Sending message (version ${VERSION}) from client to 'dst/session' with x seconds timeout.\n" +
                     "        <trace>\n" +
                     "            Message (type 1) received at 'dst' for session 'session'.\n" +
                     "            [FATAL_ERROR @ localhost]: err\n" +
                     "            Sending reply (version ${VERSION}) from 'dst'.\n" +
                     "        </trace>\n" +
                     "        Reply (type 2) received at client.\n" +
                     "    </trace>\n" +
                     "</trace>\n", logHandler.trace);
        logHandler.trace = null;

        log.setLevel(LogLevel.SPAM);
        sendMessage(1, null);
        assertNull(logHandler.trace);

        sendMessage(0, null);
        assertEquals("Trace for reply:\n" +
                     "<trace>\n" +
                     "    <trace>\n" +
                     "        Sending message (version ${VERSION}) from client to 'dst/session' with x seconds timeout.\n" +
                     "        <trace>\n" +
                     "            Message (type 1) received at 'dst' for session 'session'.\n" +
                     "            Sending reply (version ${VERSION}) from 'dst'.\n" +
                     "        </trace>\n" +
                     "        Reply (type 0) received at client.\n" +
                     "    </trace>\n" +
                     "</trace>\n", logHandler.trace);
        logHandler.trace = null;

        sendMessage(1, new Error(ErrorCode.FATAL_ERROR, "err"));
        assertNull(logHandler.trace);

        sendMessage(0, new Error(ErrorCode.FATAL_ERROR, "err"));
        assertEquals("Trace for reply with error(s):\n" +
                     "<trace>\n" +
                     "    <trace>\n" +
                     "        Sending message (version ${VERSION}) from client to 'dst/session' with x seconds timeout.\n" +
                     "        <trace>\n" +
                     "            Message (type 1) received at 'dst' for session 'session'.\n" +
                     "            [FATAL_ERROR @ localhost]: err\n" +
                     "            Sending reply (version ${VERSION}) from 'dst'.\n" +
                     "        </trace>\n" +
                     "        Reply (type 2) received at client.\n" +
                     "    </trace>\n" +
                     "</trace>\n", logHandler.trace);
        logHandler.trace = null;
    }

    private void sendMessage(int traceLevel, Error err) {
        Message msg = new SimpleMessage("foo");
        msg.getTrace().setLevel(traceLevel);
        assertTrue(srcSession.send(msg, Route.parse("dst/session")).isAccepted());
        assertNotNull(msg = ((Receptor)dstSession.getMessageHandler()).getMessage(60));
        if (err != null) {
            Reply reply = new SimpleReply("bar");
            reply.swapState(msg);
            reply.addError(err);
            dstSession.reply(reply);
        } else {
            dstSession.acknowledge(msg);
        }
        Reply reply = ((Receptor)srcSession.getReplyHandler()).getReply(60);
        assertNotNull(reply);
    }

    private static class LogHandler extends Handler {

        String trace = null;

        @Override
        public void publish(LogRecord record) {
            String msg = record.getMessage();
            if (msg.startsWith("Trace ")) {
                msg = msg.replaceAll("\\[.*\\] ", "");
                msg = msg.replaceAll("[0-9]+\\.[0-9]+ seconds", "x seconds");

                String ver = Vtag.currentVersion.toString();
                for (int i = msg.indexOf(ver); i >= 0; i = msg.indexOf(ver, i)) {
                    msg = msg.substring(0, i) + "${VERSION}" + msg.substring(i + ver.length());
                }
                trace = msg;
            }
        }

        @Override
        public void flush() {
            // empty
        }

        @Override
        public void close() throws SecurityException {
            // empty
        }
    }

}
