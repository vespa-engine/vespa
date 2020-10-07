// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.FixedBucketSpaces;
import com.yahoo.document.fieldset.AllFields;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentOperationParameters;
import com.yahoo.documentapi.DocumentResponse;
import com.yahoo.documentapi.DumpVisitorDataHandler;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.Response;
import com.yahoo.documentapi.ResponseHandler;
import com.yahoo.documentapi.Result;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.vespa.config.content.AllClustersBucketSpacesConfig;
import com.yahoo.yolean.Exceptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.BAD_REQUEST;
import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.ERROR;
import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.INSUFFICIENT_STORAGE;
import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.NOT_FOUND;
import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.OVERLOAD;
import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.PRECONDITION_FAILED;
import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.TIMEOUT;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Encapsulates a document access and supports running asynchronous document
 * operations and visits against this, with retries and optional timeouts.
 *
 * @author jonmv
 */
public class DocumentOperationExecutorImpl implements DocumentOperationExecutor {

    private static final Logger log = Logger.getLogger(DocumentOperationExecutorImpl.class.getName());

    private final Duration visitTimeout;
    private final long maxThrottled;
    private final DocumentAccess access;
    private final AsyncSession asyncSession;
    private final Map<String, StorageCluster> clusters;
    private final Clock clock;
    private final DelayQueue throttled;
    private final DelayQueue timeouts;
    private final Map<VisitorControlHandler, VisitorSession> visits = new ConcurrentHashMap<>();
    private final ExecutorService visitSessionShutdownExecutor = Executors.newSingleThreadExecutor(new DaemonThreadFactory("visit-session-shutdown-"));

    public DocumentOperationExecutorImpl(ClusterListConfig clustersConfig, AllClustersBucketSpacesConfig bucketsConfig,
                                         DocumentOperationExecutorConfig executorConfig, DocumentAccess access, Clock clock) {
        this(Duration.ofMillis(executorConfig.resendDelayMillis()),
             Duration.ofSeconds(executorConfig.defaultTimeoutSeconds()),
             Duration.ofSeconds(executorConfig.visitTimeoutSeconds()),
             executorConfig.maxThrottled(),
             access,
             parseClusters(clustersConfig, bucketsConfig),
             clock);
    }

    DocumentOperationExecutorImpl(Duration resendDelay, Duration defaultTimeout, Duration visitTimeout, long maxThrottled,
                                  DocumentAccess access, Map<String, StorageCluster> clusters, Clock clock) {
        this.visitTimeout = requireNonNull(visitTimeout);
        this.maxThrottled = maxThrottled;
        this.access = requireNonNull(access);
        this.asyncSession = access.createAsyncSession(new AsyncParameters());
        this.clock = requireNonNull(clock);
        this.clusters = Map.copyOf(clusters);
        this.throttled = new DelayQueue(maxThrottled, this::send, resendDelay, clock, "throttle");
        this.timeouts = new DelayQueue(Long.MAX_VALUE, (__, context) -> {
            context.error(TIMEOUT, "Timed out after " + defaultTimeout);
            return true;
        }, defaultTimeout, clock, "timeout");
    }

    private static VisitorParameters asParameters(VisitorOptions options, Map<String, StorageCluster> clusters, Duration visitTimeout) {
        if (options.cluster.isEmpty() && options.documentType.isEmpty())
            throw new IllegalArgumentException("Must set 'cluster' parameter to a valid content cluster id when visiting at a root /document/v1/ level");

        VisitorParameters parameters = new VisitorParameters(Stream.of(options.selection,
                                                                       options.documentType,
                                                                       options.namespace.map(value -> "id.namespace=='" + value + "'"),
                                                                       options.group.map(Group::selection))
                                                                   .flatMap(Optional::stream)
                                                                   .reduce(new StringJoiner(") and (", "(", ")").setEmptyValue(""), // don't mind the lonely chicken to the right
                                                                           StringJoiner::add,
                                                                           StringJoiner::merge)
                                                                   .toString());

        options.continuation.map(ProgressToken::fromSerializedString).ifPresent(parameters::setResumeToken);
        parameters.setFieldSet(options.fieldSet.orElse(options.documentType.map(type -> type + ":[document]").orElse(AllFields.NAME)));
        options.wantedDocumentCount.ifPresent(count -> { if (count <= 0) throw new IllegalArgumentException("wantedDocumentCount must be positive"); });
        parameters.setMaxTotalHits(options.wantedDocumentCount.orElse(1 << 10));
        options.concurrency.ifPresent(value -> { if (value <= 0) throw new IllegalArgumentException("concurrency must be positive"); });
        parameters.setThrottlePolicy(new StaticThrottlePolicy().setMaxPendingCount(options.concurrency.orElse(1)));
        parameters.setTimeoutMs(visitTimeout.toMillis());
        parameters.visitInconsistentBuckets(true);
        parameters.setPriority(DocumentProtocol.Priority.NORMAL_4);

        StorageCluster storageCluster = resolveCluster(options.cluster, clusters);
        parameters.setRoute(storageCluster.route());
        parameters.setBucketSpace(resolveBucket(storageCluster,
                                                options.documentType,
                                                List.of(FixedBucketSpaces.defaultSpace(), FixedBucketSpaces.globalSpace()),
                                                options.bucketSpace));

        return parameters;
    }

    /** Assumes this stops receiving operations roughly when this is called, then waits up to 20 seconds to drain operations. */
    @Override
    public void shutdown() {
        long shutdownMillis = clock.instant().plusSeconds(20).toEpochMilli();
        visitSessionShutdownExecutor.shutdown();
        visits.values().forEach(VisitorSession::destroy);
        Future<?> throttleShutdown = throttled.shutdown(Duration.ofSeconds(10),
                                                        context -> context.error(OVERLOAD, "Retry on overload failed due to shutdown"));
        Future<?> timeoutShutdown = timeouts.shutdown(Duration.ofSeconds(15),
                                                      context -> context.error(TIMEOUT, "Timed out due to shutdown"));
        try {
            throttleShutdown.get(Math.max(0, shutdownMillis - clock.millis()), TimeUnit.MILLISECONDS);
            timeoutShutdown.get(Math.max(0, shutdownMillis - clock.millis()), TimeUnit.MILLISECONDS);
            visitSessionShutdownExecutor.awaitTermination(Math.max(0, shutdownMillis - clock.millis()), TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException | ExecutionException | TimeoutException e) {
            throttleShutdown.cancel(true);
            timeoutShutdown.cancel(true);
            log.log(WARNING, "Exception shutting down " + getClass().getName(), e);
        }
    }

    @Override
    public void get(DocumentId id, DocumentOperationParameters parameters, OperationContext context) {
        accept(() -> asyncSession.get(id, parameters.withResponseHandler(handlerOf(parameters, context))), context);
    }

    @Override
    public void put(DocumentPut put, DocumentOperationParameters parameters, OperationContext context) {
        accept(() -> asyncSession.put(put, parameters.withResponseHandler(handlerOf(parameters, context))), context);
    }

    @Override
    public void update(DocumentUpdate update, DocumentOperationParameters parameters, OperationContext context) {
        accept(() -> asyncSession.update(update, parameters.withResponseHandler(handlerOf(parameters, context))), context);
    }

    @Override
    public void remove(DocumentId id, DocumentOperationParameters parameters, OperationContext context) {
        accept(() -> asyncSession.remove(id, parameters.withResponseHandler(handlerOf(parameters, context))), context);
    }

    @Override
    public void visit(VisitorOptions options, VisitOperationsContext context) {
        try {
            AtomicBoolean done = new AtomicBoolean(false);
            VisitorParameters parameters = asParameters(options, clusters, visitTimeout);
            parameters.setLocalDataHandler(new DumpVisitorDataHandler() {
                @Override public void onDocument(Document doc, long timeStamp) { context.document(doc); }
                @Override public void onRemove(DocumentId id) { } // We don't visit removes here.
            });
            parameters.setControlHandler(new VisitorControlHandler() {
                @Override public void onDone(CompletionCode code, String message) {
                    super.onDone(code, message);
                    switch (code) {
                        case TIMEOUT:
                            if ( ! hasVisitedAnyBuckets())
                                context.error(TIMEOUT, "No buckets visited within timeout of " + visitTimeout);
                        case SUCCESS: // intentional fallthrough
                        case ABORTED:
                            context.success(Optional.ofNullable(getProgress())
                                                    .filter(progress -> ! progress.isFinished())
                                                    .map(ProgressToken::serializeToString));
                            break;
                        default:
                            context.error(ERROR, message != null ? message : "Visiting failed");
                    }
                    done.set(true); // This may be reached before dispatching thread is done putting us in the map.
                    visits.computeIfPresent(this, (__, session) -> {
                        visitSessionShutdownExecutor.execute(() -> session.destroy());
                        return null;
                    });
                }
            });
            visits.put(parameters.getControlHandler(), access.createVisitorSession(parameters));
            if (done.get())
                visits.computeIfPresent(parameters.getControlHandler(), (__, session) -> {
                    visitSessionShutdownExecutor.execute(() -> session.destroy());
                    return null;
                });
        }
        catch (IllegalArgumentException | ParseException e) {
            context.error(BAD_REQUEST, Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            context.error(ERROR, Exceptions.toMessageString(e));
        }
    }

    @Override
    public String routeToCluster(String cluster) {
        return resolveCluster(Optional.of(cluster), clusters).route();
    }

    private ResponseHandler handlerOf(DocumentOperationParameters parameters, OperationContext context) {
        return response -> {
            parameters.responseHandler().ifPresent(originalHandler -> originalHandler.handleResponse(response));
            if (response.isSuccess())
                context.success(response instanceof DocumentResponse ? Optional.ofNullable(((DocumentResponse) response).getDocument())
                                                                     : Optional.empty());
            else
                context.error(toErrorType(response.outcome()), response.getTextMessage());
        };
    }

    /** Rejects operation if retry queue is full; otherwise starts a timer for the given task, and attempts to send it.  */
    private void accept(Supplier<Result> operation, OperationContext context) {
        timeouts.add(operation, context);
        if (throttled.size() > 0 || ! send(operation, context))
            if ( ! throttled.add(operation, context))
                context.error(OVERLOAD, maxThrottled + " requests already in retry queue");
    }

    /** Attempts to send the given operation through the async session of this, returning {@code false} if throttled. */
    private boolean send(Supplier<Result> operation, OperationContext context) {
        Result result = operation.get();
        switch (result.type()) {
            case SUCCESS:
                return true;
            case TRANSIENT_ERROR:
                return false;
            default:
                log.log(WARNING, "Unknown result type '" + result.type() + "'");
            case FATAL_ERROR: // intentional fallthrough
                context.error(ERROR, result.getError().getMessage());
                return true; // Request handled, don't retry.
        }
    }

    private static ErrorType toErrorType(Response.Outcome outcome) {
        switch (outcome) {
            case NOT_FOUND:
                return NOT_FOUND;
            case CONDITION_FAILED:
                return PRECONDITION_FAILED;
            case INSUFFICIENT_STORAGE:
                return INSUFFICIENT_STORAGE;
            default:
                log.log(WARNING, "Unexpected response outcome: " + outcome);
            case ERROR: // intentional fallthrough
                return ERROR;
        }
    }


    /**
     * Keeps delayed operations (retries or timeouts) until ready, at which point a bulk maintenance operation processes them.
     *
     * This is similar to {@link java.util.concurrent.DelayQueue}, but sacrifices the flexibility
     * of using dynamic timeouts, and latency, for higher throughput and efficient (lazy) deletions.
     */
    static class DelayQueue {

        private final long maxSize;
        private final Clock clock;
        private final ConcurrentLinkedQueue<Delayed> queue = new ConcurrentLinkedQueue<>();
        private final AtomicLong size = new AtomicLong(0);
        private final Thread maintainer;
        private final Duration delay;
        private final long defaultWaitMillis;

        public DelayQueue(long maxSize, BiPredicate<Supplier<Result>, OperationContext> action,
                          Duration delay, Clock clock, String threadName) {
            if (maxSize < 0)
                throw new IllegalArgumentException("Max size cannot be negative, but was " + maxSize);
            if (delay.isNegative())
                throw new IllegalArgumentException("Delay cannot be negative, but was " + delay);

            this.maxSize = maxSize;
            this.delay = delay;
            this.defaultWaitMillis = Math.min(delay.toMillis(), 100); // Run regularly to evict handled contexts.
            this.clock = requireNonNull(clock);
            this.maintainer = new DaemonThreadFactory("document-operation-executor-" + threadName).newThread(() -> maintain(action));
            this.maintainer.start();
        }

        boolean add(Supplier<Result> operation, OperationContext context) {
            if (size.incrementAndGet() > maxSize) {
                size.decrementAndGet();
                return false;
            }
            return queue.add(new Delayed(clock.instant().plus(delay), operation, context));
        }

        long size() { return size.get(); }

        Future<?> shutdown(Duration grace, Consumer<OperationContext> onShutdown) {
            ExecutorService shutdownService = Executors.newSingleThreadExecutor();
            Future<?> future = shutdownService.submit(() -> {
                try {
                    long doomMillis = clock.millis() + grace.toMillis();
                    while (size.get() > 0 && clock.millis() < doomMillis)
                        Thread.sleep(100);
                }
                finally {
                    maintainer.interrupt();
                    for (Delayed delayed; (delayed = queue.poll()) != null; ) {
                        size.decrementAndGet();
                        onShutdown.accept(delayed.context());
                    }
                }
                return null;
            });
            shutdownService.shutdown();
            return future;
        }

        /**
         * Repeatedly loops through the queue, evicting already handled entries and processing those
         * which have become ready since last time, then waits until new items are guaranteed to be ready,
         * or until it's time for a new run just to ensure GC of handled entries.
         * The entries are assumed to always be added to the back of the queue, with the same delay.
         * If the queue is to support random delays, the maintainer must be woken up on every insert with a ready time
         * lower than the current, and the earliest sleepUntilMillis be computed, rather than simply the first.
         */
        private void maintain(BiPredicate<Supplier<Result>, OperationContext> action) {
            while ( ! Thread.currentThread().isInterrupted()) {
                try {
                    Instant waitUntil = null;
                    Iterator<Delayed> operations = queue.iterator();
                    boolean rejected = false;
                    while (operations.hasNext()) {
                        Delayed delayed = operations.next();
                        // Already handled: remove and continue.
                        if (delayed.context().handled()) {
                            operations.remove();
                            size.decrementAndGet();
                            continue;
                        }
                        // Ready for action: remove from queue and run unless an operation was already rejected.
                        if (delayed.readyAt().isBefore(clock.instant()) && ! rejected) {
                            if (action.test(delayed.operation(), delayed.context())) {
                                operations.remove();
                                size.decrementAndGet();
                                continue;
                            }
                            else { // If an operation is rejected, handle no more this run, and wait a short while before retrying.
                                waitUntil = clock.instant().plus(Duration.ofMillis(10));
                                rejected = true;
                            }
                        }
                        // Not yet ready for action: keep time to wake up again.
                        waitUntil = waitUntil != null ? waitUntil : delayed.readyAt();
                    }
                    long waitUntilMillis = waitUntil != null ? waitUntil.toEpochMilli() : clock.millis() + defaultWaitMillis;
                    synchronized (this) {
                        do {
                            notify();
                            wait(Math.max(0, waitUntilMillis - clock.millis()));
                        }
                        while (clock.millis() < waitUntilMillis);
                    }
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                catch (Exception e) {
                    log.log(SEVERE, "Exception caught by delay queue maintainer", e);
                }
            }
        }
    }


    private static class Delayed {

        private final Supplier<Result> operation;
        private final OperationContext context;
        private final Instant readyAt;

        Delayed(Instant readyAt, Supplier<Result> operation, OperationContext context) {
            this.readyAt = requireNonNull(readyAt);
            this.context = requireNonNull(context);
            this.operation = requireNonNull(operation);
        }

        Supplier<Result> operation() { return operation; }
        OperationContext context() { return context; }
        Instant readyAt() { return readyAt; }

    }


    static class StorageCluster {

        private final String name;
        private final String configId;
        private final Map<String, String> documentBuckets;

        StorageCluster(String name, String configId, Map<String, String> documentBuckets) {
            this.name = requireNonNull(name);
            this.configId = requireNonNull(configId);
            this.documentBuckets = Map.copyOf(documentBuckets);
        }

        String name() { return name; }
        String configId() { return configId; }
        String route() { return "[Storage:cluster=" + name() + ";clusterconfigid=" + configId() + "]"; }
        Optional<String> bucketOf(String documentType) { return Optional.ofNullable(documentBuckets.get(documentType)); }

    }


    static StorageCluster resolveCluster(Optional<String> wanted, Map<String, StorageCluster> clusters) {
        if (clusters.isEmpty())
            throw new IllegalArgumentException("Your Vespa deployment has no content clusters, so the document API is not enabled");

        return wanted.map(cluster -> {
            if ( ! clusters.containsKey(cluster))
                throw new IllegalArgumentException("Your Vespa deployment has no content cluster '" + cluster + "', only '" +
                                                   String.join("', '", clusters.keySet()) + "'");

            return clusters.get(cluster);
        }).orElseGet(() -> {
            if (clusters.size() > 1)
                throw new IllegalArgumentException("Please specify one of the content clusters in your Vespa deployment: '" +
                                                   String.join("', '", clusters.keySet()) + "'");

            return clusters.values().iterator().next();
        });
    }

    private static String resolveBucket(StorageCluster cluster, Optional<String> documentType,
                                        List<String> bucketSpaces, Optional<String> bucketSpace) {
        return documentType.map(type -> cluster.bucketOf(type)
                                               .orElseThrow(() -> new IllegalArgumentException("Document type '" + type + "' in cluster '" + cluster.name() +
                                                                                               "' is not mapped to a known bucket space")))
                           .or(() -> bucketSpace.map(space -> {
                               if ( ! bucketSpaces.contains(space))
                                   throw new IllegalArgumentException("Bucket space '" + space + "' is not a known bucket space; expected one of " +
                                                                      String.join(", ", bucketSpaces));
                               return space;
                           }))
                           .orElse(FixedBucketSpaces.defaultSpace());
    }



    private static Map<String, StorageCluster> parseClusters(ClusterListConfig clusters, AllClustersBucketSpacesConfig buckets) {
        return clusters.storage().stream()
                       .collect(toUnmodifiableMap(storage -> storage.name(),
                                                  storage -> new StorageCluster(storage.name(),
                                                                                storage.configid(),
                                                                                buckets.cluster(storage.name())
                                                                                       .documentType().entrySet().stream()
                                                                                       .collect(toMap(entry -> entry.getKey(),
                                                                                                      entry -> entry.getValue().bucketSpace())))));
    }


    // Visible for testing.
    AsyncSession asyncSession() { return asyncSession; }
    Collection<VisitorControlHandler> visitorSessions() { return visits.keySet(); }
    void notifyMaintainers() throws InterruptedException {
        synchronized (throttled) { throttled.notify(); throttled.wait(); }
        synchronized (timeouts) { timeouts.notify(); timeouts.wait(); }
    }

}
