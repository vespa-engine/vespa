// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.metrics.DocumentOperationType;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.Result;
import com.yahoo.messagebus.shared.SharedSourceSession;
import com.yahoo.net.HostName;
import com.yahoo.vespa.http.client.core.ErrorCode;
import com.yahoo.vespa.http.client.core.Headers;
import com.yahoo.vespa.http.client.core.OperationStatus;
import com.yahoo.vespaxmlparser.VespaXMLFeedReader;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static com.yahoo.messagebus.ErrorCode.SEND_QUEUE_FULL;

/**
 * An instance of this class handles all requests from one client using VespaHttpClient.
 *
 * The implementation is based on the code from V2, but the object model is rewritten to simplify the logic and
 * avoid using a threadpool that has no effect with all the extra that comes with it. V2 has one instance per thread
 * on the client, while this is one instance for all threads.
 */
class ClientFeederV3 {

    protected static final Logger log = Logger.getLogger(ClientFeederV3.class.getName());
    // This is for all clients on this gateway, for load balancing from client.
    private final static AtomicInteger outstandingOperations = new AtomicInteger(0);
    private final BlockingQueue<OperationStatus> feedReplies = new LinkedBlockingQueue<>();
    private final ReferencedResource<SharedSourceSession> sourceSession;
    private final String clientId;
    private final ReplyHandler feedReplyHandler;
    private final Metric metric;
    private Instant prevOpsPerSecTime = Instant.now();
    private double operationsForOpsPerSec = 0d;

    private final Object monitor = new Object();
    private final StreamReaderV3 streamReaderV3;
    private final AtomicInteger ongoingRequests = new AtomicInteger(0);
    private String hostName;
    private AtomicInteger threadsAvailableForFeeding;

    ClientFeederV3(
            ReferencedResource<SharedSourceSession> sourceSession,
            FeedReaderFactory feedReaderFactory,
            DocumentTypeManager docTypeManager,
            String clientId,
            Metric metric,
            ReplyHandler feedReplyHandler,
            AtomicInteger threadsAvailableForFeeding) {
        this.sourceSession = sourceSession;
        this.clientId = clientId;
        this.feedReplyHandler = feedReplyHandler;
        this.metric = metric;
        this.threadsAvailableForFeeding = threadsAvailableForFeeding;
        this.streamReaderV3 = new StreamReaderV3(feedReaderFactory, docTypeManager);
        this.hostName = HostName.getLocalhost();
    }

    public boolean timedOut() {
        synchronized (monitor) {
            return Instant.now().isAfter(prevOpsPerSecTime.plusSeconds(6000)) && ongoingRequests.get() == 0;
        }
    }

    public void kill() {
        // No new requests should be sent to this object, but there can be old one, even though this is very unlikely.
        while (ongoingRequests.get() > 0) {
            try {
                ongoingRequests.wait(100);
            } catch (InterruptedException e) {
                break;
            }
        }
        sourceSession.getReference().close();
    }

    private void transferPreviousRepliesToResponse(BlockingQueue<OperationStatus> operations) throws InterruptedException {
        OperationStatus status = feedReplies.poll();
        while (status != null) {
            outstandingOperations.decrementAndGet();
            operations.put(status);
            status = feedReplies.poll();
        }
    }

    public HttpResponse handleRequest(HttpRequest request) throws IOException {
        threadsAvailableForFeeding.decrementAndGet();
        ongoingRequests.incrementAndGet();
        try {
            FeederSettings feederSettings = new FeederSettings(request);
            /**
             * The gateway handle overload from clients in different ways.
             *
             * If the backend is overloaded, but not the gateway, it will fill the backend, messagebus throttler
             * will start to block new documents and finally all threadsAvailableForFeeding will be blocking.
             * However, as more threads are added, the gateway will not block on messagebus but return
             * transitive errors on the documents that can not be processed. These errors will cause the client(s) to
             * back off a bit.
             *
             * However, we can also have the case that the gateway becomes the bottleneck (e.g. CPU). In this case
             * we need to stop processing of new messages as early as possible and reject the request. This
             * will cause the client(s) to back off for a while. We want some slack before we enter this mode.
             * If we can simply transitively fail each document, it is nicer. Therefor we allow some threads to be
             * busy processing requests with transitive errors before entering this mode. Since we already
             * have flooded the backend, have several threads hanging and waiting for capacity, the number should
             * not be very large. Too much slack can lead to too many threads handling feed and impacting query traffic.
             * We try 10 for now. This should only kick in with very massive feeding to few gateway nodes.
             */
            if (feederSettings.denyIfBusy && threadsAvailableForFeeding.get() < -10) {
                return new ErrorHttpResponse(getOverloadReturnCode(request), "Gateway overloaded");
            }

            InputStream inputStream = StreamReaderV3.unzipStreamIfNeeded(request);
            final BlockingQueue<OperationStatus> replies = new LinkedBlockingQueue<>();
            try {
                feed(feederSettings, inputStream, replies, threadsAvailableForFeeding);
                synchronized (monitor) {
                    // Handshake requests do not have DATA_FORMAT, we do not want to give responses to
                    // handshakes as it won't be processed by the client.
                    if (request.getJDiscRequest().headers().get(Headers.DATA_FORMAT) != null) {
                        transferPreviousRepliesToResponse(replies);
                    }
                }
            } catch (InterruptedException e) {
                // NOP, just terminate
            } catch (Throwable e) {
                log.log(LogLevel.WARNING, "Unhandled exception while feeding: "
                        + Exceptions.toMessageString(e), e);
            } finally {
                try {
                    replies.add(createOperationStatus("-", "-", ErrorCode.END_OF_FEED, false, null));
                } catch (InterruptedException e) {
                    // NOP, we are already exiting the thread
                }
            }
            return new FeedResponse(200, replies, 3 /* protocol version */, clientId, outstandingOperations.get(), hostName);
        } finally {
            ongoingRequests.decrementAndGet();
            threadsAvailableForFeeding.incrementAndGet();
        }
    }

    private int getOverloadReturnCode(HttpRequest request) {
        if (request.getHeader(Headers.SILENTUPGRADE) != null ) {
            return 299;
        }
        return 429;
    }

    private Optional<DocumentOperationMessageV3> pullMessageFromRequest(
            FeederSettings settings, InputStream requestInputStream, BlockingQueue<OperationStatus> repliesFromOldMessages) {
        while (true) {
            final Optional<String> operationId;
            try {
                operationId = streamReaderV3.getNextOperationId(requestInputStream);
            } catch (IOException ioe) {
                if (log.isLoggable(LogLevel.DEBUG)) {
                    log.log(LogLevel.DEBUG, Exceptions.toMessageString(ioe), ioe);
                }
                return Optional.empty();
            }
            if (! operationId.isPresent()) {
                return Optional.empty();
            }
            final DocumentOperationMessageV3 message;
            try {
                message = getNextMessage(operationId.get(), requestInputStream, settings);
            } catch (Exception e) {
                if (log.isLoggable(LogLevel.DEBUG)) {
                    log.log(LogLevel.DEBUG, Exceptions.toMessageString(e), e);
                }
                repliesFromOldMessages.add(new OperationStatus(
                        Exceptions.toMessageString(e), operationId.get(), ErrorCode.ERROR, false, ""));

                continue;
            }
            if (message != null)
                setRoute(message, settings);
            return Optional.ofNullable(message);
        }
    }

    private Result sendMessage(
            FeederSettings settings,
            DocumentOperationMessageV3 msg,
            AtomicInteger threadsAvailableForFeeding) throws InterruptedException {
        Result result = null;
        while (result == null || result.getError().getCode() == SEND_QUEUE_FULL) {
            msg.getMessage().pushHandler(feedReplyHandler);

            if (settings.denyIfBusy && threadsAvailableForFeeding.get() < 1) {
                return sourceSession.getResource().sendMessage(msg.getMessage());
            } else {
                result = sourceSession.getResource().sendMessageBlocking(msg.getMessage());
            }
            if (result.isAccepted()) {
                return result;
            }
            Thread.sleep(100);
        }
        return result;
    }

    private void feed(
            FeederSettings settings,
            InputStream requestInputStream,
            BlockingQueue<OperationStatus> repliesFromOldMessages,
            AtomicInteger threadsAvailableForFeeding) throws InterruptedException {
        while (true) {

            Optional<DocumentOperationMessageV3> msg = pullMessageFromRequest(settings, requestInputStream, repliesFromOldMessages);

            if (! msg.isPresent()) {
                break;
            }
            setMessageParameters(msg.get(), settings);

            final Result result;
            try {
                result = sendMessage(settings, msg.get(), threadsAvailableForFeeding);

            } catch  (RuntimeException e) {
                repliesFromOldMessages.add(createOperationStatus(msg.get().getOperationId(), Exceptions.toMessageString(e),
                        ErrorCode.ERROR, false, msg.get().getMessage()));
                continue;
            }

            if (result.isAccepted()) {
                outstandingOperations.incrementAndGet();
                updateOpsPerSec();
                log(LogLevel.DEBUG, "Sent message successfully, document id: ", msg.get().getOperationId());
            } else if (!result.getError().isFatal()) {
                repliesFromOldMessages.add(createOperationStatus(msg.get().getOperationId(), result.getError().getMessage(),
                        ErrorCode.TRANSIENT_ERROR, false, msg.get().getMessage()));
                continue;
            } else {
                // should probably not happen, but everybody knows stuff that
                // shouldn't happen, happens all the time
                boolean isConditionNotMet = result.getError().getCode() == DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED;
                repliesFromOldMessages.add(createOperationStatus(msg.get().getOperationId(), result.getError().getMessage(),
                        ErrorCode.ERROR, isConditionNotMet, msg.get().getMessage()));
                continue;
            }
        }
    }

    private OperationStatus createOperationStatus(String id, String message, ErrorCode code, boolean isConditionNotMet, Message msg)
            throws InterruptedException {
        String traceMessage = msg != null && msg.getTrace() != null &&  msg.getTrace().getLevel() > 0
                ? msg.getTrace().toString()
                : "";
        return new OperationStatus(message, id, code, isConditionNotMet, traceMessage);
    }

    // protected for mocking
    /** Returns the next message in the stream, or null if none */
    protected DocumentOperationMessageV3 getNextMessage(
            String operationId, InputStream requestInputStream, FeederSettings settings) throws Exception {
        VespaXMLFeedReader.Operation operation = streamReaderV3.getNextOperation(requestInputStream, settings);

        // This is a bit hard to set up while testing, so we accept that things are not perfect.
        if (sourceSession.getResource().session() != null) {
            metric.set(
                    MetricNames.PENDING,
                    Double.valueOf(sourceSession.getResource().session().getPendingCount()),
                    null);
        }

        DocumentOperationMessageV3 message = DocumentOperationMessageV3.create(operation, operationId, metric);
        if (message == null) {
            // typical end of feed
            return null;
        }
        metric.add(MetricNames.NUM_OPERATIONS, 1, null /*metricContext*/);
        log(LogLevel.DEBUG, "Successfully deserialized document id: ", message.getOperationId());
        return message;
    }

    private void setMessageParameters(DocumentOperationMessageV3 msg, FeederSettings settings) {
        msg.getMessage().setContext(new ReplyContext(msg.getOperationId(), feedReplies, DocumentOperationType.fromMessage(msg.getMessage())));
        if (settings.traceLevel != null) {
            msg.getMessage().getTrace().setLevel(settings.traceLevel);
        }
        if (settings.priority != null) {
            try {
                DocumentProtocol.Priority priority = DocumentProtocol.Priority.valueOf(settings.priority);
                if (msg.getMessage() instanceof DocumentMessage) {
                    ((DocumentMessage) msg.getMessage()).setPriority(priority);
                }
            }
            catch (IllegalArgumentException i) {
                log.severe(i.getMessage());
            }
        }
    }

    private void setRoute(DocumentOperationMessageV3 msg, FeederSettings settings) {
        if (settings.route != null) {
            msg.getMessage().setRoute(settings.route);
        }
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

    private void updateOpsPerSec() {
        Instant now = Instant.now();
        synchronized (monitor) {
            if (now.plusSeconds(1).isAfter(prevOpsPerSecTime)) {
                Duration duration = Duration.between(now, prevOpsPerSecTime);
                double opsPerSec = operationsForOpsPerSec / (duration.toMillis() / 1000.);
                metric.set(MetricNames.OPERATIONS_PER_SEC, opsPerSec, null /*metricContext*/);
                operationsForOpsPerSec = 1.0d;
                prevOpsPerSecTime = now;
            } else {
                operationsForOpsPerSec += 1.0d;
            }
        }
    }

}
