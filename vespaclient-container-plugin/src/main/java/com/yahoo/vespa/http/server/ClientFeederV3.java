// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.Result;
import com.yahoo.messagebus.shared.SharedSourceSession;
import com.yahoo.net.HostName;
import com.yahoo.vespaxmlparser.FeedOperation;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An instance of this class handles all requests from one client using VespaHttpClient.
 *
 * The implementation is based on the code from V2, but the object model is rewritten to simplify the logic and
 * avoid using a threadpool that has no effect with all the extra that comes with it. V2 has one instance per thread
 * on the client, while this is one instance for all threads.
 *
 * @author dybis
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
    private final String hostName;

    ClientFeederV3(ReferencedResource<SharedSourceSession> sourceSession,
                   FeedReaderFactory feedReaderFactory,
                   DocumentTypeManager docTypeManager,
                   String clientId,
                   Metric metric,
                   ReplyHandler feedReplyHandler) {
        this.sourceSession = sourceSession;
        this.clientId = clientId;
        this.feedReplyHandler = feedReplyHandler;
        this.metric = metric;
        this.streamReaderV3 = new StreamReaderV3(feedReaderFactory, docTypeManager);
        this.hostName = HostName.getLocalhost();
    }

    boolean timedOut() {
        synchronized (monitor) {
            return Instant.now().isAfter(prevOpsPerSecTime.plusSeconds(6000)) && ongoingRequests.get() == 0;
        }
    }

    void kill() {
        try (ResourceReference ignored = sourceSession.getReference()) {
            // No new requests should be sent to this object, but there can be old one, even though this is very unlikely.
            while (ongoingRequests.get() > 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to close reference to source session", e);
        }
    }

    private void transferPreviousRepliesToResponse(BlockingQueue<OperationStatus> operations) throws InterruptedException {
        OperationStatus status = feedReplies.poll();
        while (status != null) {
            outstandingOperations.decrementAndGet();
            operations.put(status);
            status = feedReplies.poll();
        }
    }

    HttpResponse handleRequest(HttpRequest request) throws IOException {
        ongoingRequests.incrementAndGet();
        try {
            FeederSettings feederSettings = new FeederSettings(request);
            InputStream inputStream = StreamReaderV3.unzipStreamIfNeeded(request);
            BlockingQueue<OperationStatus> replies = new LinkedBlockingQueue<>();
            try {
                feed(feederSettings, inputStream, replies);
                synchronized (monitor) {
                    // Handshake requests do not have DATA_FORMAT, we do not want to give responses to
                    // handshakes as it won't be processed by the client.
                    if (request.getJDiscRequest().headers().get(Headers.DATA_FORMAT) != null) {
                        transferPreviousRepliesToResponse(replies);
                    }
                }
            } catch (InterruptedException e) {
                log.log(Level.FINE, e, () -> "Feed handler was interrupted: " + e.getMessage());
                // NOP, just terminate
            } catch (Throwable e) {
                log.log(Level.WARNING, "Unhandled exception while feeding: " + Exceptions.toMessageString(e), e);
            } finally {
                replies.add(createOperationStatus("-", "-", ErrorCode.END_OF_FEED, null));
            }
            return new FeedResponse(200, replies, 3, clientId, outstandingOperations.get(), hostName);
        } finally {
            ongoingRequests.decrementAndGet();
        }
    }

    private Optional<DocumentOperationMessageV3> pullMessageFromRequest(FeederSettings settings,
                                                                        InputStream requestInputStream,
                                                                        BlockingQueue<OperationStatus> repliesFromOldMessages) {
        while (true) {
            Optional<String> operationId;
            try {
                operationId = streamReaderV3.getNextOperationId(requestInputStream);
                if (operationId.isEmpty()) return Optional.empty();
            } catch (IOException ioe) {
                log.log(Level.FINE, () -> Exceptions.toMessageString(ioe));
                return Optional.empty();
            }

            try {
                DocumentOperationMessageV3 message = getNextMessage(operationId.get(), requestInputStream, settings);
                if (message != null)
                    setRoute(message, settings);
                return Optional.ofNullable(message);
            } catch (Exception e) {
                log.log(Level.WARNING, () -> Exceptions.toMessageString(e));
                metric.add(MetricNames.PARSE_ERROR, 1, null);

                repliesFromOldMessages.add(new OperationStatus(Exceptions.toMessageString(e),
                                                               operationId.get(),
                                                               ErrorCode.ERROR,
                                                               false,
                                                               ""));
            }
        }
    }

    private Result sendMessage(DocumentOperationMessageV3 msg) throws InterruptedException {
        msg.getMessage().pushHandler(feedReplyHandler);
        return sourceSession.getResource().sendMessageBlocking(msg.getMessage());
    }

    private void feed(FeederSettings settings,
                      InputStream requestInputStream,
                      BlockingQueue<OperationStatus> repliesFromOldMessages) throws InterruptedException {
        while (true) {
            Optional<DocumentOperationMessageV3> message = pullMessageFromRequest(settings,
                                                                                  requestInputStream,
                                                                                  repliesFromOldMessages);

            if (message.isEmpty()) break;
            setMessageParameters(message.get(), settings);

            Result result;
            try {
                result = sendMessage(message.get());

            } catch  (RuntimeException e) {
                repliesFromOldMessages.add(createOperationStatus(message.get().getOperationId(),
                                                                 Exceptions.toMessageString(e),
                                                                 ErrorCode.ERROR,
                                                                 message.get().getMessage()));
                continue;
            }

            if (result.isAccepted()) {
                outstandingOperations.incrementAndGet();
                updateOpsPerSec();
                log(Level.FINE, "Sent message successfully, document id: ", message.get().getOperationId());
            } else if (!result.getError().isFatal()) {
                repliesFromOldMessages.add(createOperationStatus(message.get().getOperationId(),
                                                                 result.getError().getMessage(),
                                                                 ErrorCode.TRANSIENT_ERROR,
                        message.get().getMessage()));
            } else {
                repliesFromOldMessages.add(createOperationStatus(message.get().getOperationId(),
                                                                 result.getError().getMessage(),
                                                                 ErrorCode.ERROR,
                        message.get().getMessage()));
            }
        }
    }

    private OperationStatus createOperationStatus(String id, String message, ErrorCode code, Message msg) {
        String traceMessage = msg != null && msg.getTrace() != null &&  msg.getTrace().getLevel() > 0
                ? msg.getTrace().toString()
                : "";
        return new OperationStatus(message, id, code, false, traceMessage);
    }

    // protected for mocking
    /** Returns the next message in the stream, or null if none */
    protected DocumentOperationMessageV3 getNextMessage(String operationId,
                                                        InputStream requestInputStream,
                                                        FeederSettings settings) throws Exception {
        FeedOperation operation = streamReaderV3.getNextOperation(requestInputStream, settings);

        // This is a bit hard to set up while testing, so we accept that things are not perfect.
        if (sourceSession.getResource().session() != null) {
            metric.set(MetricNames.PENDING, (double) sourceSession.getResource().session().getPendingCount(), null);
        }

        DocumentOperationMessageV3 message = DocumentOperationMessageV3.create(operation, operationId, metric);
        if (message == null) {
            // typical end of feed
            return null;
        }
        metric.add(MetricNames.NUM_OPERATIONS, 1, null);
        log(Level.FINE, "Successfully deserialized document id: ", message.getOperationId());
        return message;
    }

    private void setMessageParameters(DocumentOperationMessageV3 msg, FeederSettings settings) {
        msg.getMessage().setContext(new ReplyContext(msg.getOperationId(), feedReplies));
        if (settings.traceLevel != null) {
            msg.getMessage().getTrace().setLevel(settings.traceLevel);
        }
    }

    private void setRoute(DocumentOperationMessageV3 msg, FeederSettings settings) {
        if (settings.route != null) {
            msg.getMessage().setRoute(settings.route);
        }
    }

    protected final void log(Level level, Object... msgParts) {
        if (!log.isLoggable(level)) return;

        StringBuilder s = new StringBuilder();
        for (Object part : msgParts)
            s.append(part.toString());
        log.log(level, s.toString());
    }

    private void updateOpsPerSec() {
        Instant now = Instant.now();
        synchronized (monitor) {
            if (now.plusSeconds(1).isAfter(prevOpsPerSecTime)) {
                Duration duration = Duration.between(now, prevOpsPerSecTime);
                double opsPerSec = operationsForOpsPerSec / (duration.toMillis() / 1000.);
                metric.set(MetricNames.OPERATIONS_PER_SEC, opsPerSec, null);
                operationsForOpsPerSec = 1.0d;
                prevOpsPerSecTime = now;
            } else {
                operationsForOpsPerSec += 1.0d;
            }
        }
    }

}
