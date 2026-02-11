// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.RPCMessageBus;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.test.SimpleProtocol;

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
        Reply reply = new EmptyReply();
        msg.swapState(reply);
        reply.addError(new com.yahoo.messagebus.Error(ErrorCode.APP_FATAL_ERROR + 1, "ERR 1"));
        reply.addError(new com.yahoo.messagebus.Error(ErrorCode.APP_FATAL_ERROR + 2, "ERR 2"));
        session.reply(reply);
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
