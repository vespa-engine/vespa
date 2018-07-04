// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.perf;

import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.documentapi.messagebus.protocol.*;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.RPCMessageBus;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.vespaxmlparser.VespaXMLFeedReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleFeeder implements ReplyHandler {

    private final static long REPORT_INTERVAL = TimeUnit.SECONDS.toMillis(10);
    private final static long HEADER_INTERVAL = REPORT_INTERVAL * 24;
    private final DocumentTypeManager docTypeMgr = new DocumentTypeManager();
    private final InputStream in;
    private final PrintStream out;
    private final PrintStream err;
    private final RPCMessageBus mbus;
    private final Route route;
    private final SourceSession session;
    private final long startTime = System.currentTimeMillis();
    private volatile Throwable failure;
    private volatile long numReplies = 0;
    private long maxLatency = Long.MIN_VALUE;
    private long minLatency = Long.MAX_VALUE;
    private long nextHeader = startTime + HEADER_INTERVAL;
    private long nextReport = startTime + REPORT_INTERVAL;
    private long numMessages = 0;
    private long sumLatency = 0;

    public static void main(String[] args) throws Throwable {
        new SimpleFeeder(new FeederParams().parseArgs(args)).run().close();
    }

    public SimpleFeeder(FeederParams params) {
        this.in = params.getStdIn();
        this.out = params.getStdOut();
        this.err = params.getStdErr();
        this.route = params.getRoute();
        this.mbus = newMessageBus(docTypeMgr, params.getConfigId());
        this.session = newSession(mbus, this, params.isSerialTransferEnabled());
        this.docTypeMgr.configure(params.getConfigId());
    }

    public SimpleFeeder run() throws Throwable {
        VespaXMLFeedReader reader = new VespaXMLFeedReader(in, docTypeMgr);
        VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
        printHeader();
        while (failure == null) {
            reader.read(op);
            if (op.getType() == VespaXMLFeedReader.OperationType.INVALID) {
                break;
            }
            Message msg = newMessage(op);
            if (msg == null) {
                err.println("ignoring operation; " + op.getType());
                continue; // ignore
            }
            msg.setContext(System.currentTimeMillis());
            msg.setRoute(route);
            Error err = session.sendBlocking(msg).getError();
            if (err != null) {
                throw new IOException(err.toString());
            }
            ++numMessages;
        }
        while (failure == null && numReplies < numMessages) {
            Thread.sleep(100);
        }
        if (failure != null) {
            throw failure;
        }
        printReport();
        return this;
    }

    public void close() {
        session.destroy();
        mbus.destroy();
    }

    private Message newMessage(VespaXMLFeedReader.Operation op) {
        switch (op.getType()) {
        case DOCUMENT: {
            PutDocumentMessage message = new PutDocumentMessage(new DocumentPut(op.getDocument()));
            message.setCondition(op.getCondition());
            return message;
        }
        case REMOVE: {
            RemoveDocumentMessage message = new RemoveDocumentMessage(op.getRemove());
            message.setCondition(op.getCondition());
            return message;
        }
        case UPDATE: {
            UpdateDocumentMessage message = new UpdateDocumentMessage(op.getDocumentUpdate());
            message.setCondition(op.getCondition());
            return message;
        }
        default:
            return null;
        }
    }

    @Override
    public void handleReply(Reply reply) {
        if (failure != null) {
            return;
        }
        if (reply.hasErrors()) {
            failure = new IOException(formatErrors(reply));
            return;
        }
        long now = System.currentTimeMillis();
        long latency = now - (long)reply.getContext();
        minLatency = Math.min(minLatency, latency);
        maxLatency = Math.max(maxLatency, latency);
        sumLatency += latency;
        ++numReplies;
        if (now > nextHeader) {
            printHeader();
            nextHeader += HEADER_INTERVAL;
        }
        if (now > nextReport) {
            printReport();
            nextReport += REPORT_INTERVAL;
        }
    }

    private void printHeader() {
        out.println("total time, num messages, min latency, avg latency, max latency");
    }

    private void printReport() {
        out.format("%10d, %12d, %11d, %11d, %11d\n", System.currentTimeMillis() - startTime,
                   numReplies, minLatency, sumLatency / numReplies, maxLatency);
    }

    private static String formatErrors(Reply reply) {
        StringBuilder out = new StringBuilder();
        out.append(reply.getMessage().toString()).append('\n');
        for (int i = 0, len = reply.getNumErrors(); i < len; ++i) {
            out.append(reply.getError(i).toString()).append('\n');
        }
        return out.toString();
    }

    private static RPCMessageBus newMessageBus(DocumentTypeManager docTypeMgr, String configId) {
        return new RPCMessageBus(new MessageBusParams().addProtocol(new DocumentProtocol(docTypeMgr)),
                                 new RPCNetworkParams().setSlobrokConfigId(configId),
                                 configId);
    }

    private static SourceSession newSession(RPCMessageBus mbus, ReplyHandler replyHandler, boolean serial) {
        SourceSessionParams params = new SourceSessionParams();
        params.setReplyHandler(replyHandler);
        if (serial) {
            params.setThrottlePolicy(new StaticThrottlePolicy().setMaxPendingCount(1));
        }
        return mbus.getMessageBus().createSourceSession(params);
    }
}
