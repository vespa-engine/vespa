// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.perf;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.json.JsonFeedReader;
import com.yahoo.document.json.JsonWriter;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
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
import com.yahoo.vespaxmlparser.FeedReader;
import com.yahoo.vespaxmlparser.VespaXMLFeedReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleFeeder implements ReplyHandler {

    private final static long REPORT_INTERVAL = TimeUnit.SECONDS.toMillis(10);
    private final static long HEADER_INTERVAL = REPORT_INTERVAL * 24;
    private final DocumentTypeManager docTypeMgr = new DocumentTypeManager();
    private final InputStream in;
    private final PrintStream out;
    private final RPCMessageBus mbus;
    private final SourceSession session;
    private final long startTime = System.currentTimeMillis();
    private final AtomicReference<Throwable> failure = new AtomicReference<>(null);
    private final AtomicLong numReplies = new AtomicLong(0);
    private long maxLatency = Long.MIN_VALUE;
    private long minLatency = Long.MAX_VALUE;
    private long nextHeader = startTime + HEADER_INTERVAL;
    private long nextReport = startTime + REPORT_INTERVAL;
    private long sumLatency = 0;
    private final int numThreads;
    private final Destination destination;

    public static void main(String[] args) throws Throwable {
        new SimpleFeeder(new FeederParams().parseArgs(args)).run().close();
    }

    private interface Destination {
        void send(VespaXMLFeedReader.Operation op);
        void close() throws Exception;
    }

    private static class MbusDestination implements Destination {
        private final PrintStream err;
        private final Route route;
        private final SourceSession session;
        private final AtomicReference<Throwable> failure;
        MbusDestination(SourceSession session, Route route, AtomicReference<Throwable> failure, PrintStream err) {
            this.route = route;
            this.err = err;
            this.session = session;
            this.failure = failure;
        }
        public void send(VespaXMLFeedReader.Operation op) {
            Message msg = newMessage(op);
            if (msg == null) {
                err.println("ignoring operation; " + op.getType());
                return;
            }
            msg.setContext(System.currentTimeMillis());
            msg.setRoute(route);
            try {
                Error err = session.sendBlocking(msg).getError();
                if (err != null) {
                    failure.set(new IOException(err.toString()));
                }
            } catch (InterruptedException e) {}
        }
        public void close() throws Exception {
            session.destroy();
        }
    }

    private static class JsonDestination implements Destination {
        private final OutputStream outputStream;
        private final JsonWriter writer;
        private final AtomicLong numReplies;
        private final AtomicReference<Throwable> failure;
        private boolean isFirst = true;
        JsonDestination(OutputStream outputStream, AtomicReference<Throwable> failure, AtomicLong numReplies) {
            this.outputStream = outputStream;
            writer = new JsonWriter(outputStream);
            this.numReplies = numReplies;
            this.failure = failure;
            try {
                outputStream.write('[');
                outputStream.write('\n');
            } catch (IOException e) {
                failure.set(e);
            }
        }
        public void send(VespaXMLFeedReader.Operation op) {
            if (op.getType() == VespaXMLFeedReader.OperationType.DOCUMENT) {
                if (!isFirst) {
                    try {
                        outputStream.write(',');
                        outputStream.write('\n');
                    } catch (IOException e) {
                        failure.set(e);
                    }
                } else {
                    isFirst = false;
                }
                writer.write(op.getDocument());
            }
            numReplies.incrementAndGet();
        }
        public void close() throws Exception {
            outputStream.write('\n');
            outputStream.write(']');
            outputStream.close();
        }

    }

    SimpleFeeder(FeederParams params) {
        in = params.getStdIn();
        out = params.getStdOut();
        numThreads = params.getNumDispatchThreads();
        mbus = newMessageBus(docTypeMgr, params.getConfigId());
        session = newSession(mbus, this, params.isSerialTransferEnabled());
        docTypeMgr.configure(params.getConfigId());
        destination = (params.getDumpStream() != null)
                ? new JsonDestination(params.getDumpStream(), failure, numReplies)
                : new MbusDestination(session, params.getRoute(), failure, params.getStdErr());
    }

    private void sendOperation(VespaXMLFeedReader.Operation op) {
        destination.send(op);
    }

    SourceSession getSourceSession() { return session; }
    private FeedReader createFeedReader() throws Exception {
        in.mark(8);
        byte [] b = new byte[1];
        int numRead = in.read(b);
        in.reset();
        if (numRead != b.length) {
            throw new IllegalArgumentException("Need to read " + b.length + " bytes to detect format. Got " + numRead + " bytes.");
        }
        if (b[0] == '[') {
            return new JsonFeedReader(in, docTypeMgr);
        } else {
             return new VespaXMLFeedReader(in, docTypeMgr);
        }
    }

    SimpleFeeder run() throws Throwable {
        ExecutorService executor = (numThreads > 1)
                ? new ThreadPoolExecutor(numThreads, numThreads, 0L, TimeUnit.SECONDS,
                                         new SynchronousQueue<>(false),
                                         ThreadFactoryFactory.getDaemonThreadFactory("perf-feeder"),
                                         new ThreadPoolExecutor.CallerRunsPolicy())
                : null;
        FeedReader reader = createFeedReader();

        printHeader();
        long numMessages = 0;
        while (failure.get() == null) {
            VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
            reader.read(op);
            if (op.getType() == VespaXMLFeedReader.OperationType.INVALID) {
                break;
            }
            if (executor != null) {
                executor.execute(() -> sendOperation(op));
            } else {
                sendOperation(op);
            }
            ++numMessages;
        }
        while (failure.get() == null && numReplies.get() < numMessages) {
            Thread.sleep(100);
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        printReport();
        return this;
    }

    void close() throws Exception {
        destination.close();
        mbus.destroy();
    }

    private static Message newMessage(VespaXMLFeedReader.Operation op) {
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
        if (failure.get() != null) {
            return;
        }
        if (reply.hasErrors()) {
            failure.compareAndSet(null, new IOException(formatErrors(reply)));
            return;
        }
        long now = System.currentTimeMillis();
        long latency = now - (long) reply.getContext();
        numReplies.incrementAndGet();
        accumulateReplies(now, latency);
    }
    private synchronized void accumulateReplies(long now, long latency) {
        minLatency = Math.min(minLatency, latency);
        maxLatency = Math.max(maxLatency, latency);
        sumLatency += latency;
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
                   numReplies.get(), minLatency, sumLatency / numReplies.get(), maxLatency);
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
