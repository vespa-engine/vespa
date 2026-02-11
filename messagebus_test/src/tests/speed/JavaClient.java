// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.test.*;
import com.yahoo.config.*;
import com.yahoo.messagebus.routing.*;
import com.yahoo.messagebus.network.*;
import com.yahoo.messagebus.network.rpc.*;
import java.util.Arrays;
import java.util.logging.*;

public class JavaClient implements ReplyHandler {

    private static Logger log = Logger.getLogger(JavaClient.class.getName());

    private static class Counts {
        public int okCnt = 0;
        public int failCnt = 0;
        Counts() {}
        Counts(int okCnt, int failCnt) {
            this.okCnt = okCnt;
            this.failCnt = failCnt;
        }
    }

    private SourceSession session;
    private Counts        counts = new Counts();
    private static long   mySeq  = 100000;

    public JavaClient(RPCMessageBus mb) {
        session = mb.getMessageBus().createSourceSession(this, new SourceSessionParams().setTimeout(30));
    }

    public synchronized Counts sample() {
        return new Counts(counts.okCnt, counts.failCnt);
    }

    public void send() {
        send(++mySeq);
    }

    public void send(long seq) {
        session.send(new MyMessage(seq), "test");
    }

    public void handleReply(Reply reply) {
        if ((reply.getProtocol() == SimpleProtocol.NAME)
            && (reply.getType() == SimpleProtocol.REPLY)
            && (((SimpleReply)reply).getValue().equals("OK")))
        {
            synchronized (this) {
                counts.okCnt++;
            }
        } else {
            synchronized (this) {
                counts.failCnt++;
            }
        }
        try {
            send();
        } catch (IllegalStateException ignore) {} // handle paranoia for shutdown source sessions
    }

    public void shutdown() {
        session.destroy();
    }

    public static void main(String[] args) {
        try {
	    RPCMessageBus mb = new RPCMessageBus(
                new MessageBusParams()
                .setRetryPolicy(new RetryTransientErrorsPolicy().setBaseDelay(0.1))
                .addProtocol(new SimpleProtocol()),
                new RPCNetworkParams()
                .setIdentity(new Identity("server/java"))
                .setSlobrokConfigId("file:slobrok.cfg"),
		"file:routing.cfg");
            JavaClient client = new JavaClient(mb);

            // let the system 'warm up'
            Thread.sleep(5000);

            // inject messages into the feedback loop
            for (int i = 0; i < 1024; ++i) {
                client.send(i);
            }

            // let the system 'warm up'
            Thread.sleep(5000);

            long start;
            long stop;
            Counts before;
            Counts after;

            start = System.currentTimeMillis();
            before = client.sample();
            Thread.sleep(10000); // Benchmark time
            stop = System.currentTimeMillis();
            after = client.sample();
            stop -= start;
            double time = (double)stop;
            double msgCnt = (double)(after.okCnt - before.okCnt);
            double throughput = (msgCnt / time) * 1000.0;
            System.out.printf("JAVA-CLIENT: %g msg/s\n", throughput);
            client.shutdown();
            mb.destroy();
            if (after.failCnt > before.failCnt) {
                System.err.printf("JAVA-CLIENT: FAILED (%d -> %d)\n",
                                  before.failCnt, after.failCnt);
                System.exit(1);
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "JAVA-CLIENT: Failed", e);
            System.exit(1);
        }
    }

    private static class MyMessage extends SimpleMessage {

        final long seqId;

        MyMessage(long seqId) {
            super("message");
            this.seqId = seqId;
        }

        @Override
        public boolean hasSequenceId() {
            return true;
        }

        @Override
        public long getSequenceId() {
            return seqId;
        }
    }
}
