// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.collections.Tuple2;
import com.yahoo.container.jdisc.messagebus.SessionCache;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.UpdateDocumentMessage;
import com.yahoo.documentapi.metrics.DocumentOperationType;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.Result;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.routing.ErrorDirective;
import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.shared.SharedSourceSession;
import com.yahoo.yolean.Exceptions;
import com.yahoo.text.Utf8String;
import com.yahoo.vespa.http.client.core.Encoder;
import com.yahoo.vespa.http.client.core.ErrorCode;
import com.yahoo.vespa.http.client.core.OperationStatus;
import com.yahoo.vespa.http.server.util.ByteLimitedInputStream;
import com.yahoo.vespaxmlparser.FeedReader;
import com.yahoo.vespaxmlparser.VespaXMLFeedReader;
import com.yahoo.vespaxmlparser.VespaXMLFeedReader.Operation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.yahoo.messagebus.ErrorCode.SEND_QUEUE_FULL;

/**
 * Read documents from client, and send them through message bus.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class Feeder implements Runnable {

    protected static final Logger log = Logger.getLogger(Feeder.class.getName());

    final InputStream requestInputStream;
    final DocumentTypeManager docTypeManager;
    final BlockingQueue<OperationStatus> operations;
    final BlockingQueue<OperationStatus> feedReplies;
    int numPending;
    final FeederSettings settings;
    final String clientId;
    final ReferencedResource<SharedSourceSession> sourceSession;
    final FeedHandler handler;
    final Metric metric;
    final Metric.Context metricContext;
    private long prevOpsPerSecTime; // previous measurement time of OPS
    private double operationsForOpsPerSec;
    private final ReplyHandler feedReplyHandler;
    protected final static String EOF = "End of stream";
    protected final boolean sessionIdWasGeneratedJustNow;
    private final CountDownLatch requestReceived = new CountDownLatch(1);
    private final FeedReaderFactory feedReaderFactory;

    public Feeder(InputStream requestInputStream,
                  FeedReaderFactory feedReaderFactory,
                  DocumentTypeManager docTypeManager,
                  BlockingQueue<OperationStatus> operations,
                  ClientState storedState,
                  FeederSettings settings,
                  String clientId, boolean sessionIdWasGeneratedJustNow, SourceSessionParams sessionParams,
                  SessionCache sessionCache,
                  FeedHandler handler, Metric metric, ReplyHandler feedReplyHandler,
                  String localHostname) throws Exception {
        super();
        this.feedReaderFactory = feedReaderFactory;
        if (storedState == null) {
            if (!sessionIdWasGeneratedJustNow) {
                // We do not have a stored state, BUT the session ID came in with the request.
                // Possible session timeout, server restart, server reconfig, or VIP usage. Examine.
                examineClientId(clientId, localHostname);
            }
            numPending = 0;
            feedReplies = new LinkedBlockingQueue<>();
            sourceSession = retainSession(sessionParams, sessionCache);
            metricContext = createClientMetricContext(metric, clientId);
            prevOpsPerSecTime = System.currentTimeMillis();
            operationsForOpsPerSec = 0.0;
        } else {
            //we have a stored state, and the session ID was obviously not generated now. All OK.
            numPending = storedState.pending;
            feedReplies = storedState.feedReplies;
            sourceSession = storedState.sourceSession;
            metricContext = storedState.metricContext;
            prevOpsPerSecTime = storedState.prevOpsPerSecTime;
            operationsForOpsPerSec = storedState.operationsForOpsPerSec;
        }
        this.clientId = clientId;
        this.sessionIdWasGeneratedJustNow = sessionIdWasGeneratedJustNow;
        this.requestInputStream = requestInputStream;
        this.docTypeManager = docTypeManager;
        this.operations = operations;
        this.settings = settings;
        this.handler = handler;
        this.metric = metric;
        this.feedReplyHandler = feedReplyHandler;
    }
    protected void examineClientId(String clientId, String localHostname) {
        if (!clientId.contains("#")) {
            throw new UnknownClientException("Got request from client with id '" + clientId +
                                             "', but found no session for this client. " +
                                             "This is expected during upgrades of gateways and infrastructure nodes.");
        }
        int hashPos = clientId.indexOf("#");
        String supposedHostname = clientId.substring(hashPos + 1, clientId.length());
        if (supposedHostname.isEmpty()) {
            throw new UnknownClientException("Got request from client with id '" + clientId +
                                             "', but found no session for this client. Possible session " +
                                             "timeout due to inactivity, server restart or reconfig, " +
                                             "or bad VIP usage. " +
                                             "This is expected during upgrades of gateways and infrastructure nodes.");
        }

        if (!supposedHostname.equals(localHostname)) {
            throw new UnknownClientException("Got request from client with id '" + clientId +
                                             "', but found no session for this client. " +
                                             "Session was originally established towards host " +
                                             supposedHostname + ", but our hostname is " +
                                             localHostname + ". " +
                                             "If using VIP rotation, this could be due to a session was rotated from one server to another. " +
                                             "Configure VIP with persistence=enabled. " +
                                             "This is expected during upgrades of gateways and infrastructure nodes.");
        }
        log.log(LogLevel.DEBUG, "Client '" + clientId + "' reconnected after session inactivity, or server restart " +
                               "or reconfig. Re-establishing session.");
    }



    private static Metric.Context createClientMetricContext(Metric metric, String clientId) {
        // No real value in separate metric dimensions per client.
        return null;
    }

    /**
     * Exposed for creating mocks.
     */
    protected ReferencedResource<SharedSourceSession> retainSession(
            SourceSessionParams sessionParams, SessionCache sessionCache) {
        return sessionCache.retainSource(sessionParams);
    }

    @Override
    public void run() {
        try {
            if (handshake()) {
                return;  //will putClient in finally block below
            }
            flushResponseQueue();
            feed();
            requestReceived.countDown();
            drain();
        } catch (InterruptedException e) {
            // NOP, just terminate
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Unhandled exception while feeding: "
                    + Exceptions.toMessageString(e), e);
        } catch (Throwable e) {
            log.log(LogLevel.WARNING, "Unhandled error while feeding: "
                    + Exceptions.toMessageString(e), e);
            throw e;
        } finally {
            requestReceived.countDown();
            putClient();
            try {
                enqueue("-", "-", ErrorCode.END_OF_FEED, false, null);
            } catch (InterruptedException e) {
                // NOP, we are already exiting the thread
            }
        }
    }

    protected boolean handshake() throws IOException {
        if (sessionIdWasGeneratedJustNow) {
            if (log.isLoggable(LogLevel.DEBUG)) {
                log.log(LogLevel.DEBUG, "Handshake completed for client " + clientId + ".");
            }
            requestInputStream.close();
            return true;
        }
        return false;
    }

    void feed() throws InterruptedException {
        while (true) {
            Result result;
            String operationId;
            try {
                operationId = getNextOperationId();
            } catch (IOException ioe) {
                if (log.isLoggable(LogLevel.DEBUG)) {
                    log.log(LogLevel.DEBUG, Exceptions.toMessageString(ioe), ioe);
                }
                break;
            }

            //noinspection StringEquality
            if (operationId == EOF) {
                break;
            }

            Tuple2<String, Message> msg;
            try {
                msg = getNextMessage(operationId);
                setRoute(msg);
            } catch (Exception e) {
                if (log.isLoggable(LogLevel.DEBUG)) {
                    log.log(LogLevel.DEBUG, Exceptions.toMessageString(e), e);
                }
                //noinspection StringEquality
                if (operationId != null) {  //v1 always returns null, all others return something useful, or throw an exception above
                    msg = newErrorMessage(operationId, e);
                } else {
                    break;
                }
            }

            if (msg == null) {
                break;
            }

            setMessageParameters(msg);

            while (true) {
                try {
                    msg.second.pushHandler(feedReplyHandler);
                    if (settings.denyIfBusy) {
                        result = sourceSession.getResource().sendMessage(msg.second);
                    } else {
                        result = sourceSession.getResource().sendMessageBlocking(msg.second);
                    }
                } catch (RuntimeException e) {
                    enqueue(msg.first, Exceptions.toMessageString(e),
                            ErrorCode.ERROR, false, msg.second);
                    return;
                }
                if (result.isAccepted() || result.getError().getCode() != SEND_QUEUE_FULL) {
                    break;
                }
                if (settings.denyIfBusy) {
                    break;
                } else {
                    //This will never happen
                    Thread.sleep(100);
                }
            }

            if (result.isAccepted()) {
                ++numPending;
                updateMetrics(msg.second);
                updateOpsPerSec();
                log(LogLevel.DEBUG, "Sent message successfully, document id: ", msg.first);
            } else if (!result.getError().isFatal()) {
                enqueue(msg.first, result.getError().getMessage(), ErrorCode.TRANSIENT_ERROR, false, msg.second);
                break;
            } else {
                // should probably not happen, but everybody knows stuff that
                // shouldn't happen, happens all the time
                boolean isConditionNotMet = result.getError().getCode() == DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED;
                enqueue(msg.first, result.getError().getMessage(), ErrorCode.ERROR, isConditionNotMet, msg.second);
                break;
            }
        }
    }

    private Tuple2<String, Message> newErrorMessage(String operationId, Exception e) {
        Message m = new FeedErrorMessage(operationId);
        Tuple2<String, Message> msg = new Tuple2<>(operationId, m);
        Hop hop = new Hop();
        hop.addDirective(new ErrorDirective(Exceptions.toMessageString(e)));
        Route route = new Route();
        route.addHop(hop);
        m.setRoute(route);
        return msg;
    }

    private void updateMetrics(Message m) {
        metric.set(
                MetricNames.PENDING,
                Double.valueOf(sourceSession.getResource().session().getPendingCount()),
                null);

        metric.add(MetricNames.NUM_OPERATIONS, 1, metricContext);

        if (m instanceof PutDocumentMessage) {
            metric.add(MetricNames.NUM_PUTS, 1, metricContext);
        } else if (m instanceof RemoveDocumentMessage) {
            metric.add(MetricNames.NUM_REMOVES, 1, metricContext);
        } else if (m instanceof UpdateDocumentMessage) {
            metric.add(MetricNames.NUM_UPDATES, 1, metricContext);
        }
    }

    private void updateOpsPerSec() {
        long now = System.currentTimeMillis();
        if ((now - prevOpsPerSecTime) >= 1000) {  //every second
            double ms = (double) (now - prevOpsPerSecTime);
            final double opsPerSec = operationsForOpsPerSec / (ms / 1000);
            metric.set(MetricNames.OPERATIONS_PER_SEC, opsPerSec, metricContext);
            operationsForOpsPerSec = 1.0d;
            prevOpsPerSecTime = now;
        } else {
            operationsForOpsPerSec += 1.0d;
        }
    }

    private Tuple2<String, Message> getNextMessage(String operationId) throws Exception {
        VespaXMLFeedReader.Operation op = new VespaXMLFeedReader.Operation();
        Tuple2<String, Message> msg;

        getNextOperation(op);

        switch (op.getType()) {
            case DOCUMENT:
                msg = newPutMessage(op, operationId);
                break;
            case REMOVE:
                msg = newRemoveMessage(op, operationId);
                break;
            case UPDATE:
                msg = newUpdateMessage(op, operationId);
                break;
            default:
                // typical end of feed
                return null;
        }
        log(LogLevel.DEBUG, "Successfully deserialized document id: ", msg.first);
        return msg;
    }

    private void setMessageParameters(Tuple2<String, Message> msg) {
        msg.second.setContext(new ReplyContext(msg.first, feedReplies, DocumentOperationType.fromMessage(msg.second)));
        if (settings.traceLevel != null) {
            msg.second.getTrace().setLevel(settings.traceLevel);
        }
        if (settings.priority != null) {
            try {
                DocumentProtocol.Priority priority = DocumentProtocol.Priority.valueOf(settings.priority);
                if (msg.second instanceof DocumentMessage) {
                    ((DocumentMessage) msg.second).setPriority(priority);
                }
            }
            catch (IllegalArgumentException i) {
                log.severe(i.getMessage());
            }
        }
    }

    private void setRoute(Tuple2<String, Message> msg) {
        if (settings.route != null) {
            msg.second.setRoute(settings.route);
        }
    }

    protected void getNextOperation(VespaXMLFeedReader.Operation op) throws Exception {
        int length = readByteLength();

        try (InputStream limitedInputStream = new ByteLimitedInputStream(requestInputStream, length)){
            FeedReader reader = feedReaderFactory.createReader(limitedInputStream, docTypeManager, settings.dataFormat);
            reader.read(op);
        }
    }

    protected String getNextOperationId() throws IOException {
        return readOperationId();
    }

    private String readOperationId() throws IOException {
        StringBuilder idBuf = new StringBuilder(100);
        int c;
        while ((c = requestInputStream.read()) != -1) {
            if (c == 32) {
                break;
            }
            idBuf.append((char) c);  //it's ASCII
        }
        if (idBuf.length() == 0) {
            return EOF;
        }
        return Encoder.decode(idBuf.toString(), new StringBuilder(idBuf.length())).toString();
    }

    private int readByteLength() throws IOException {
        StringBuilder lenBuf = new StringBuilder(8);
        int c;
        while ((c = requestInputStream.read()) != -1) {
            if (c == 10) {
                break;
            }
            lenBuf.append((char) c);  //it's ASCII
        }
        if (lenBuf.length() == 0) {
            throw new IllegalStateException("Operation length missing.");
        }
        return Integer.valueOf(lenBuf.toString(), 16);
    }

    protected final void log(LogLevel level, Object... msgParts) {
        StringBuilder s;

        if (!log.isLoggable(level)) {
            return;
        }

        s = new StringBuilder();
        for (Object part : msgParts) {
            s.append(part.toString());
        }

        log.log(level, s.toString());
    }

    private Tuple2<String, Message> newUpdateMessage(Operation op, String operationId) {
        DocumentUpdate update = op.getDocumentUpdate();
        update.setCondition(op.getCondition());
        Message msg = new UpdateDocumentMessage(update);

        String id = (operationId == null) ? update.getId().toString() : operationId;
        return new Tuple2<>(id, msg);
    }

    private Tuple2<String, Message> newRemoveMessage(Operation op, String operationId) {
        DocumentRemove remove = new DocumentRemove(op.getRemove());
        remove.setCondition(op.getCondition());
        Message msg = new RemoveDocumentMessage(remove);

        String id = (operationId == null) ? remove.getId().toString() : operationId;
        return new Tuple2<>(id, msg);
    }

    private Tuple2<String, Message> newPutMessage(Operation op, String operationId) {
        DocumentPut put = new DocumentPut(op.getDocument());
        put.setCondition(op.getCondition());
        Message msg = new PutDocumentMessage(put);

        String id = (operationId == null) ? put.getId().toString() : operationId;
        return new Tuple2<>(id, msg);
    }


    void flushResponseQueue() throws InterruptedException {
        OperationStatus status = feedReplies.poll();
        while (status != null) {
            decreasePending(status);
            status = feedReplies.poll();
        }
    }

    void putClient() {
        handler.putClient(clientId,
                          new ClientState(numPending,
                                          feedReplies, sourceSession, metricContext,
                                          prevOpsPerSecTime, operationsForOpsPerSec));
    }

    void drain() throws InterruptedException {
        if (settings.drain) {
            while (numPending > 0) {
                OperationStatus o = feedReplies.take();
                decreasePending(o);
            }
        }
    }

    private void decreasePending(OperationStatus o) throws InterruptedException {
        operations.put(o);
        --numPending;
    }

    private void enqueue(String id, String message, ErrorCode code, boolean isConditionalNotMet, Message msg)
            throws InterruptedException {
        String traceMessage = msg != null && msg.getTrace() != null &&  msg.getTrace().getLevel() > 0
                ? msg.getTrace().toString()
                : "";
        operations.put(new OperationStatus(message, id, code, isConditionalNotMet, traceMessage));
    }

    public void waitForRequestReceived() throws InterruptedException {
        requestReceived.await(1, TimeUnit.HOURS);
    }

    public class FeedErrorMessage extends Message {
        private long sequenceId;

        private FeedErrorMessage(String operationId) {
            try {
                DocumentId id = new DocumentId(operationId);
                sequenceId =  Arrays.hashCode(id.getGlobalId());
            } catch (Exception e) {
                sequenceId = 0;
            }
        }

        @Override
        public Utf8String getProtocol() {
            return new Utf8String("vespa-feed-handler-internal-bogus-protocol");
        }

        @Override
        public int getType() {
            return 1234;
        }

        @Override
        public boolean hasSequenceId() {
            return true;
        }

        @Override
        public long getSequenceId() {
            return sequenceId;
        }
    }

}
