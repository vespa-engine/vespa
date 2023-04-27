// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.perf;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.json.JsonFeedReader;
import com.yahoo.document.json.JsonWriter;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentDeserializerFactory;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import com.yahoo.document.serialization.DocumentWriter;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.messagebus.DynamicThrottlePolicy;
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
import com.yahoo.vespaxmlparser.FeedOperation;
import com.yahoo.vespaxmlparser.RemoveFeedOperation;
import com.yahoo.vespaxmlparser.VespaXMLFeedReader;
import net.jpountz.xxhash.XXHashFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleFeeder implements ReplyHandler {

    private final DocumentTypeManager docTypeMgr = new DocumentTypeManager();
    private final ConfigSubscriber documentTypeConfigSubscriber;
    private final List<InputStream> inputStreams;
    private final PrintStream out;
    private final RPCMessageBus mbus;
    private final SourceSession session;
    private final int numThreads;
    private final long numMessagesToSend;
    private final Destination destination;
    private final boolean benchmarkMode;
    private final static long REPORT_INTERVAL = TimeUnit.SECONDS.toMillis(10);
    private final long startTime = System.currentTimeMillis();
    private final AtomicReference<Throwable> failure = new AtomicReference<>(null);
    private final AtomicLong numReplies = new AtomicLong(0);
    private long maxLatency = Long.MIN_VALUE;
    private long minLatency = Long.MAX_VALUE;
    private long nextReport = startTime + REPORT_INTERVAL;
    private long sumLatency = 0;

    static class Metrics {

        private final Destination destination;
        private final FeedReader reader;
        private final Executor executor;
        private final long messagesToSend;
        private final AtomicReference<Throwable> failure;

        Metrics(Destination destination, FeedReader reader, Executor executor, AtomicReference<Throwable> failure, long messagesToSend) {
            this.destination = destination;
            this.reader = reader;
            this.executor = executor;
            this.messagesToSend = messagesToSend;
            this.failure = failure;
        }

        long feed() throws Throwable {
            long numMessages = 0;
            while ((failure.get() == null) && (numMessages < messagesToSend)) {
                FeedOperation op = reader.read();
                if (op.getType() == FeedOperation.Type.INVALID) {
                    break;
                }
                if (executor != null) {
                    executor.execute(() -> sendOperation(op));
                } else {
                    sendOperation(op);
                }
                ++numMessages;
            }
            return numMessages;
        }
        private void sendOperation(FeedOperation op) {
            destination.send(op);
        }
    }


    public static void main(String[] args) throws Throwable {
        Logger.getLogger("").setLevel(Level.WARNING);
        new SimpleFeeder(new FeederParams().parseArgs(args)).run().close();
    }

    private interface Destination {
        void send(FeedOperation op);
        void close() throws Exception;
    }

    private static class MbusDestination implements Destination {
        private final PrintStream err;
        private final Route route;
        private final SourceSession session;
        private final long timeoutMS;
        private final AtomicReference<Throwable> failure;
        MbusDestination(SourceSession session, Route route, double timeoutS, AtomicReference<Throwable> failure, PrintStream err) {
            this.route = route;
            this.err = err;
            this.session = session;
            this.timeoutMS = (long)(timeoutS * 1000.0);
            this.failure = failure;
        }
        public void send(FeedOperation op) {
            Message msg = newMessage(op);
            if (msg == null) {
                err.println("ignoring operation; " + op.getType());
                return;
            }
            msg.setTimeRemaining(timeoutMS);
            msg.setContext(System.currentTimeMillis());
            msg.setRoute(route);
            try {
                Error err = session.sendBlocking(msg).getError();
                if (err != null) {
                    failure.set(new IOException(err.toString()));
                }
            } catch (InterruptedException e) {}
        }
        public void close() {
            session.destroy();
        }
    }

    private static class JsonDestination implements Destination {
        private final OutputStream outputStream;
        private final DocumentWriter writer;
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
        public void send(FeedOperation op) {
            if (op.getType() == FeedOperation.Type.DOCUMENT) {
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
                writer.write(op.getDocumentPut().getDocument());
            }
            numReplies.incrementAndGet();
        }
        public void close() throws Exception {
            outputStream.write('\n');
            outputStream.write(']');
            outputStream.close();
        }
    }

    static private final int NONE = 0;
    static private final int DOCUMENT = 1;
    static private final int UPDATE = 2;
    static private final int REMOVE = 3;
    private static class VespaV1Destination implements Destination {
        private final OutputStream outputStream;
        GrowableByteBuffer buffer = new GrowableByteBuffer(16384);
        ByteBuffer header = ByteBuffer.allocate(16);
        private final AtomicLong numReplies;
        private final AtomicReference<Throwable> failure;
        VespaV1Destination(OutputStream outputStream, AtomicReference<Throwable> failure, AtomicLong numReplies) {
            this.outputStream = outputStream;
            this.numReplies = numReplies;
            this.failure = failure;
            try {
                outputStream.write('V');
                outputStream.write('1');
            } catch (IOException e) {
                failure.set(e);
            }
        }
        public void send(FeedOperation op) {
            TestAndSetCondition cond = op.getCondition();
            buffer.putUtf8String(cond.getSelection());
            DocumentSerializer writer = DocumentSerializerFactory.createHead(buffer);
            int type = NONE;
            if (op.getType() == FeedOperation.Type.DOCUMENT) {
                writer.write(op.getDocumentPut().getDocument());
                type = DOCUMENT;
            } else if (op.getType() == FeedOperation.Type.UPDATE) {
                writer.write(op.getDocumentUpdate());
                type = UPDATE;
            } else if (op.getType() == FeedOperation.Type.REMOVE) {
                writer.write(op.getDocumentRemove().getId());
                type = REMOVE;
            }
            int sz = buffer.position();
            long hash = hash(buffer.array(), sz);
            try {
                header.putInt(sz);
                header.putInt(type);
                header.putLong(hash);
                outputStream.write(header.array(), 0, header.position());
                outputStream.write(buffer.array(), 0, buffer.position());
                header.clear();
                buffer.clear();
            } catch (IOException e) {
                failure.set(e);
            }
            numReplies.incrementAndGet();
        }
        public void close() throws Exception {
            outputStream.close();
        }
        static long hash(byte [] buf, int length) {
            return XXHashFactory.fastestJavaInstance().hash64().hash(buf, 0, length, 0);
        }
    }

    private static int readExact(InputStream in, byte [] buf) throws IOException {
        return in.readNBytes(buf, 0, buf.length);
    }

    static class VespaV1FeedReader implements FeedReader {
        private final InputStream in;
        private final DocumentTypeManager mgr;
        private final byte[] prefix = new byte[16];
        VespaV1FeedReader(InputStream in, DocumentTypeManager mgr) throws IOException {
            this.in = in;
            this.mgr = mgr;
            byte [] header = new byte[2];
            int read = readExact(in, header);
            if ((read != header.length) || (header[0] != 'V') || (header[1] != '1')) {
                throw new IllegalArgumentException("Invalid Header " + Arrays.toString(header));
            }
        }

        static class LazyDocumentOperation extends FeedOperation {
            private final DocumentDeserializer deserializer;
            private final TestAndSetCondition condition;
            LazyDocumentOperation(DocumentDeserializer deserializer, TestAndSetCondition condition) {
                super(Type.DOCUMENT);
                this.deserializer = deserializer;
                this.condition = condition;
            }

            @Override
            public DocumentPut getDocumentPut() {
                DocumentPut put = new DocumentPut(new Document(deserializer));
                put.setCondition(condition);
                return put;
            }
            @Override
            public TestAndSetCondition getCondition() {
                return condition;
            }
        }
        static class LazyUpdateOperation extends FeedOperation {
            private final DocumentDeserializer deserializer;
            private final TestAndSetCondition condition;
            LazyUpdateOperation(DocumentDeserializer deserializer, TestAndSetCondition condition) {
                super(Type.UPDATE);
                this.deserializer = deserializer;
                this.condition = condition;
            }

            @Override
            public DocumentUpdate getDocumentUpdate() {
                DocumentUpdate update = new DocumentUpdate(deserializer);
                update.setCondition(condition);
                return update;
            }
            @Override
            public TestAndSetCondition getCondition() {
                return condition;
            }
        }

        @Override
        public FeedOperation read() throws Exception {
            int read = readExact(in, prefix);
            if (read != prefix.length) {
                return FeedOperation.INVALID;
            }
            ByteBuffer header = ByteBuffer.wrap(prefix);
            int sz = header.getInt();
            int type = header.getInt();
            long hash = header.getLong();
            byte [] blob = new byte[sz];
            read = readExact(in, blob);
            if (read != blob.length) {
                throw new IllegalArgumentException("Underflow, failed reading " + blob.length + "bytes. Got " + read);
            }
            long computedHash = VespaV1Destination.hash(blob, blob.length);
            if (computedHash != hash) {
                throw new IllegalArgumentException("Hash mismatch, expected " + hash + ", got " + computedHash);
            }
            GrowableByteBuffer buf = GrowableByteBuffer.wrap(blob);
            String condition = buf.getUtf8String();
            DocumentDeserializer deser = DocumentDeserializerFactory.createHead(mgr, buf);
            TestAndSetCondition testAndSetCondition = condition.isEmpty()
                    ? TestAndSetCondition.NOT_PRESENT_CONDITION
                    : new TestAndSetCondition(condition);
            if (type == DOCUMENT) {
                return new LazyDocumentOperation(deser, testAndSetCondition);
            } else if (type == UPDATE) {
                return new LazyUpdateOperation(deser, testAndSetCondition);
            } else if (type == REMOVE) {
                var remove = new DocumentRemove(new DocumentId(deser));
                remove.setCondition(testAndSetCondition);
                return new RemoveFeedOperation(remove);
            } else {
                throw new IllegalArgumentException("Unknown operation " + type);
            }
        }
    }

    private Destination createDumper(FeederParams params) {
        if (params.getDumpFormat() == FeederParams.DumpFormat.VESPA) {
            return new VespaV1Destination(params.getDumpStream(), failure, numReplies);
        }
        return new JsonDestination(params.getDumpStream(), failure, numReplies);
    }

    SimpleFeeder(FeederParams params) {
        inputStreams = params.getInputStreams();
        out = params.getStdOut();
        numThreads = params.getNumDispatchThreads();
        numMessagesToSend = params.getNumMessagesToSend();
        mbus = newMessageBus(docTypeMgr, params);
        session = newSession(mbus, this, params);
        documentTypeConfigSubscriber = DocumentTypeManagerConfigurer.configure(docTypeMgr, params.getConfigId());
        benchmarkMode = params.isBenchmarkMode();
        destination = (params.getDumpStream() != null)
                ? createDumper(params)
                : new MbusDestination(session, params.getRoute(), params.getTimeout(), failure, params.getStdErr());
    }

    SourceSession getSourceSession() { return session; }
    private FeedReader createFeedReader(InputStream in) throws Exception {
        in.mark(8);
        byte [] b = new byte[2];
        int numRead = readExact(in, b);
        in.reset();
        if (numRead != b.length) {
            throw new IllegalArgumentException("Need to read " + b.length + " bytes to detect format. Got " + numRead + " bytes.");
        }
        if (b[0] == '[') {
            return new JsonFeedReader(in, docTypeMgr);
        } else if ((b[0] == 'V') && (b[1] == '1')) {
            return new VespaV1FeedReader(in, docTypeMgr);
        } else {
             return new VespaXMLFeedReader(in, docTypeMgr);
        }
    }


    static class RetryExecutionHandler implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            try {
                executor.getQueue().put(r);
            } catch (InterruptedException e) {}
        }
    }

    SimpleFeeder run() throws Throwable {
        ExecutorService executor = (numThreads > 1)
                ? new ThreadPoolExecutor(numThreads, numThreads, 0L, TimeUnit.SECONDS,
                                         new ArrayBlockingQueue<>(numThreads*100),
                                         ThreadFactoryFactory.getDaemonThreadFactory("perf-feeder"),
                                         new RetryExecutionHandler())
                : null;
        printHeader(out);
        long numMessagesSent = 0;
        for (InputStream in : inputStreams) {
            Metrics m = new Metrics(destination, createFeedReader(in), executor, failure, numMessagesToSend);
            numMessagesSent += m.feed();
        }
        while (failure.get() == null && numReplies.get() < numMessagesSent) {
            Thread.sleep(100);
        }
        if (failure.get() != null) {
            throw failure.get();
        }
        printReport(out);
        return this;
    }

    void close() throws Exception {
        destination.close();
        mbus.destroy();
    }

    private static Message newMessage(FeedOperation op) {
        return switch (op.getType()) {
            case DOCUMENT -> new PutDocumentMessage(op.getDocumentPut());
            case REMOVE -> new RemoveDocumentMessage(op.getDocumentRemove());
            case UPDATE -> new UpdateDocumentMessage(op.getDocumentUpdate());
            default -> null;
        };
    }

    private static boolean containsFatalErrors(Stream<Error> errors) {
        return errors.anyMatch(e -> e.getCode() != DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED);
    }

    @Override
    public void handleReply(Reply reply) {
        if (failure.get() != null) {
            return;
        }
        if (containsFatalErrors(reply.getErrors())) {
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
        if (benchmarkMode) { return; }
        if (now > nextReport) {
            printReport(out);
            nextReport += REPORT_INTERVAL;
        }
    }
    private static void printHeader(PrintStream out) {
        out.println("# Time used, num ok, num error, min latency, max latency, average latency");
    }

    private synchronized void printReport(PrintStream out) {
        // Errors will stop feed so we just fake the num errors = 0
        out.format("%10d, %12d, 0, %11d, %11d, %11d\n", System.currentTimeMillis() - startTime,
                numReplies.get(), minLatency, maxLatency, sumLatency / Long.max(1, numReplies.get()));
    }

    private static String formatErrors(Reply reply) {
        StringBuilder out = new StringBuilder();
        out.append(reply.getMessage().toString()).append('\n');
        for (int i = 0, len = reply.getNumErrors(); i < len; ++i) {
            out.append(reply.getError(i).toString()).append('\n');
        }
        return out.toString();
    }

    private static RPCMessageBus newMessageBus(DocumentTypeManager docTypeMgr, FeederParams params) {
        return new RPCMessageBus(new MessageBusParams().addProtocol(new DocumentProtocol(docTypeMgr)),
                                 new RPCNetworkParams().setSlobrokConfigId(params.getConfigId())
                                                       .setNumTargetsPerSpec(params.getNumConnectionsPerTarget()),
                                 params.getConfigId());
    }

    private static SourceSession newSession(RPCMessageBus mbus, ReplyHandler replyHandler, FeederParams feederParams ) {
        SourceSessionParams params = new SourceSessionParams();
        params.setReplyHandler(replyHandler);
        if (feederParams.getMaxPending() > 0) {
            params.setThrottlePolicy(new StaticThrottlePolicy().setMaxPendingCount(feederParams.getMaxPending()));
        } else {
            DynamicThrottlePolicy throttlePolicy = new DynamicThrottlePolicy()
                    .setWindowSizeIncrement(feederParams.getWindowIncrementSize())
                    .setResizeRate(feederParams.getWindowResizeRate())
                    .setWindowSizeDecrementFactor(feederParams.getWindowDecrementFactor())
                    .setWindowSizeBackOff(feederParams.getWindowSizeBackOff());
            params.setThrottlePolicy(throttlePolicy);
        }
        return mbus.getMessageBus().createSourceSession(params);
    }
}
