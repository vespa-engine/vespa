// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.perf;

import com.yahoo.document.DocumentTypeManager;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.DestinationSessionParams;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.RPCNetwork;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleServer {

    private final DocumentTypeManager documentMgr;
    private final Slobrok slobrok;
    private final MessageBus mbus;
    private final DestinationSession session;

    @SuppressWarnings("deprecation")
    public SimpleServer(String configDir, MessageHandler msgHandler) throws IOException, ListenFailedException {
        slobrok = new Slobrok();
        documentMgr = DocumentTypeManager.fromFile(configDir + "/documentmanager.cfg");
        mbus = new MessageBus(new RPCNetwork(new RPCNetworkParams()
                                                     .setSlobrokConfigId(slobrok.configId())
                                                     .setIdentity(new Identity("server"))),
                              new MessageBusParams().addProtocol(new DocumentProtocol(documentMgr)));
        session = mbus.createDestinationSession(new DestinationSessionParams().setMessageHandler(msgHandler));

        PrintWriter writer = new PrintWriter(new FileWriter(configDir + "/messagebus.cfg"));
        writer.println("routingtable[1]\n" +
                       "routingtable[0].protocol \"document\"\n" +
                       "routingtable[0].hop[0]\n" +
                       "routingtable[0].route[1]\n" +
                       "routingtable[0].route[0].name \"default\"\n" +
                       "routingtable[0].route[0].hop[1]\n" +
                       "routingtable[0].route[0].hop[0] \"" + session.getConnectionSpec() + "\"");
        writer.close();

        writer = new PrintWriter(new FileWriter(configDir + "/slobroks.cfg"));
        writer.println(slobrok.configId().substring(4));
        writer.close();
    }

    @SuppressWarnings("deprecation")
    public final void close() {
        session.destroy();
        mbus.destroy();
        slobrok.stop();
    }

}
