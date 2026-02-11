// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.test.*;
import com.yahoo.config.*;
import com.yahoo.messagebus.routing.*;
import com.yahoo.messagebus.network.*;
import com.yahoo.messagebus.network.rpc.*;
import java.util.Arrays;
import java.util.logging.*;

public class JavaServer implements MessageHandler, ReplyHandler {

    private static Logger log = Logger.getLogger(JavaServer.class.getName());

    private IntermediateSession session;
    private String              name;

    public JavaServer(RPCMessageBus mb, String name) {
        session = mb.getMessageBus().createIntermediateSession("session", true, this, this);
        this.name = name;
    }

    public void handleMessage(Message msg) {
        msg.getTrace().trace(1, name + " (message)", false);
        if (msg.getRoute() == null || !msg.getRoute().hasHops()) {
            System.out.println("**** Server '" + name + "' replying.");
            Reply reply = new EmptyReply();
            msg.swapState(reply);
            handleReply(reply);
        } else {
            System.out.println("**** Server '" + name + "' forwarding message.");
            session.forward(msg);
        }
    }

    public void handleReply(Reply reply) {
        reply.getTrace().trace(1, name + " (reply)", false);
        session.forward(reply);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: JavaServer <service prefix>");
            System.exit(1);
        }
        String name = args[0];
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("All", new SimpleProtocol.PolicyFactory() {
                @Override
                public RoutingPolicy create(String param) {
                    return new AllPolicy();
                }
            });
        try {
	    RPCMessageBus mb = new RPCMessageBus(
                Arrays.<Protocol>asList(protocol),
                new RPCNetworkParams()
                .setIdentity(new Identity(name))
		.setSlobrokConfigId("file:slobrok.cfg"),
		"file:routing.cfg");
            JavaServer server = new JavaServer(mb, name);
            System.out.printf("java server started name=%s\n", name);
            while (true) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "JAVA-SERVER: Failed", e);
            System.exit(1);
        }
    }

    private static class AllPolicy implements RoutingPolicy {

        @Override
        public void select(RoutingContext ctx) {
            ctx.addChildren(ctx.getMatchedRecipients());
        }

        @Override
        public void merge(RoutingContext ctx) {
            EmptyReply ret = new EmptyReply();
            for (RoutingNodeIterator it = ctx.getChildIterator();
                 it.isValid(); it.next()) {
                Reply reply = it.getReplyRef();
                for (int i = 0; i < reply.getNumErrors(); ++i) {
                    ret.addError(reply.getError(i));
                }
            }
            ctx.setReply(ret);
        }

        @Override
        public void destroy() {

        }
    }
}
