// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.test.*;
import com.yahoo.config.*;
import com.yahoo.messagebus.routing.*;
import com.yahoo.messagebus.network.*;
import com.yahoo.messagebus.network.rpc.*;
import com.yahoo.messagebus.network.rpc.test.*;
import java.util.Arrays;
import java.util.logging.*;

public class JavaClient {

    private static Logger log = Logger.getLogger(JavaClient.class.getName());

    public static void main(String[] args) {
        try {
	    RPCMessageBus mb = new RPCMessageBus(
		Arrays.asList((Protocol)new SimpleProtocol()),
                new RPCNetworkParams()
                .setIdentity(new Identity("server/java"))
		.setSlobrokConfigId("file:slobrok.cfg"),
		"file:routing.cfg");

            Receptor src = new Receptor();
            Message msg = null;
            Reply reply = null;

            SourceSession session = mb.getMessageBus().createSourceSession(src, new SourceSessionParams().setTimeout(300));
            for (int i = 0; i < 10; i++) {
                msg = new SimpleMessage("test");
                msg.getTrace().setLevel(9);
                session.send(msg, "test");
                reply = src.getReply(60);
                if (reply == null) {
                    System.err.println("JAVA-CLIENT: no reply");
                } else {
                    System.err.println("JAVA-CLIENT:\n"  + reply.getTrace());
                    if (reply.getNumErrors() == 2) {
                        break;
                    }
                }
                Thread.sleep(1000);
            }
            if (reply == null) {
                System.err.println("JAVA-CLIENT: no reply");
                System.exit(1);
            }
            if (reply.getNumErrors() != 2 ||
                reply.getError(0).getCode() != ErrorCode.APP_FATAL_ERROR + 1 ||
                reply.getError(1).getCode() != ErrorCode.APP_FATAL_ERROR + 2 ||
                !reply.getError(0).getMessage().equals("ERR 1") ||
                !reply.getError(1).getMessage().equals("ERR 2"))
            {
                System.err.printf("JAVA-CLIENT: wrong errors\n");
                System.exit(1);
            }
            session.destroy();
            mb.destroy();
        } catch (Exception e) {
            log.log(Level.SEVERE, "JAVA-CLIENT: Failed", e);
            System.exit(1);
        }
    }
}
