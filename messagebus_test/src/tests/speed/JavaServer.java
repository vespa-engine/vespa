// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.RPCMessageBus;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import com.yahoo.messagebus.test.SimpleReply;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaServer implements MessageHandler {

    private static Logger log = Logger.getLogger(JavaServer.class.getName());

    private DestinationSession session;

    public JavaServer(RPCMessageBus mb) {
        session = mb.getMessageBus().createDestinationSession("session", true, this);
    }

    public void handleMessage(Message msg) {
        if ((msg.getProtocol() == SimpleProtocol.NAME)
            && (msg.getType() == SimpleProtocol.MESSAGE)
            && (((SimpleMessage)msg).getValue().equals("message")))
        {
            Reply reply = new SimpleReply("OK");
            msg.swapState(reply);
            session.reply(reply);
        } else {
            Reply reply = new SimpleReply("FAIL");
            msg.swapState(reply);
            session.reply(reply);
        }
    }

    public static void main(String[] args) {
        try {
	    RPCMessageBus mb = new RPCMessageBus(
		List.of(new SimpleProtocol()),
		new RPCNetworkParams()
                .setIdentity(new Identity("server/java"))
		.setSlobrokConfigId("file:slobrok.cfg"),
		"file:routing.cfg");
            JavaServer server = new JavaServer(mb);
            System.out.println("java server started");
            while (true) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "JAVA-SERVER: Failed", e);
            System.exit(1);
        }
    }
}
