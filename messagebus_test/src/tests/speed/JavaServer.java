// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.test.*;
import com.yahoo.config.*;
import com.yahoo.messagebus.routing.*;
import com.yahoo.messagebus.network.*;
import com.yahoo.messagebus.network.rpc.*;
import java.util.Arrays;
import java.util.logging.*;

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
		Arrays.asList((Protocol)new SimpleProtocol()),
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
