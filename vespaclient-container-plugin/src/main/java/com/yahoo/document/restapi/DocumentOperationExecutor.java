// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.cloud.config.ClusterListConfig;
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
import com.yahoo.documentapi.Result;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.text.Text;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.BAD_REQUEST;
import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.ERROR;
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
public class DocumentOperationExecutor {

    private static final Logger log = Logger.getLogger(DocumentOperationExecutor.class.getName());

    private final Duration visitTimeout;
    private final long maxThrottled;
    private final DocumentAccess access;
    private final AsyncSession asyncSession;
    private final Map<String, StorageCluster> clusters;
    private final Clock clock;
    private final DelayQueue throttled;
    private final DelayQueue timeouts;
    private final Map<Long, Completion> outstanding = new ConcurrentHashMap<>();
    private final Map<VisitorControlHandler, VisitorSession> visits = new ConcurrentHashMap<>();

    public DocumentOperationExecutor(ClusterListConfig clustersConfig, AllClustersBucketSpacesConfig bucketsConfig,
                                     DocumentOperationExecutorConfig executorConfig, DocumentAccess access, Clock clock) {
        this(Duration.ofMillis(executorConfig.resendDelayMillis()),
             Duration.ofSeconds(executorConfig.defaultTimeoutSeconds()),
             Duration.ofSeconds(executorConfig.visitTimeoutSeconds()),
             executorConfig.maxThrottled(),
             access,
             parseClusters(clustersConfig, bucketsConfig),
             clock);
    }

    DocumentOperationExecutor(Duration resendDelay, Duration defaultTimeout, Duration visitTimeout, long maxThrottled,
                              DocumentAccess access, Map<String, StorageCluster> clusters, Clock clock) {
        this.visitTimeout = requireNonNull(visitTimeout);
        this.maxThrottled = maxThrottled;
        this.access = requireNonNull(access);
        this.asyncSession = access.createAsyncSession(new AsyncParameters().setResponseHandler(this::handle));
        this.clock = requireNonNull(clock);
        this.clusters = Map.copyOf(clusters);
        this.throttled = new DelayQueue(maxThrottled, this::send, resendDelay, clock);
        this.timeouts = new DelayQueue(Long.MAX_VALUE, (__, context) -> context.error(TIMEOUT, "Timed out after " + defaultTimeout), defaultTimeout, clock);
    }

    /** Assumes this stops receiving operations roughly when this is called, then waits up to 50 seconds to drain operations. */
    public void shutdown() {
        long shutdownMillis = clock.instant().plusSeconds(50).toEpochMilli();
        Future<?> throttleShutdown = throttled.shutdown(Duration.ofSeconds(30),
                                                        context -> context.error(OVERLOAD, "Retry on overload failed due to shutdown"));
        Future<?> timeoutShutdown = timeouts.shutdown(Duration.ofSeconds(40),
                                                      context -> context.error(TIMEOUT, "Timed out due to shutdown"));
        visits.values().forEach(VisitorSession::destroy);
        try {
            throttleShutdown.get(Math.max(0, shutdownMillis - clock.millis()), TimeUnit.MILLISECONDS);
            timeoutShutdown.get(Math.max(0, shutdownMillis - clock.millis()), TimeUnit.MILLISECONDS);
        }
        catch (Exception e) {
            log.log(WARNING, "Exception shutting down " + getClass().getName(), e);
        }
    }

    public void get(DocumentId id, DocumentOperationParameters parameters, OperationContext context) {
        accept(() -> asyncSession.get(id, parameters), context);
    }

    public void put(DocumentPut put, DocumentOperationParameters parameters, OperationContext context) {
        accept(() -> asyncSession.put(put, parameters), context);
    }

    public void update(DocumentUpdate update, DocumentOperationParameters parameters, OperationContext context) {
        accept(() -> asyncSession.update(update, parameters), context);
    }

    public void remove(DocumentId id, DocumentOperationParameters parameters, OperationContext context) {
        accept(() -> asyncSession.remove(id, parameters), context);
    }

    public void visit(VisitorOptions options, VisitOperationsContext context) {
        try {
            AtomicBoolean done = new AtomicBoolean(false);
            VisitorParameters parameters = options.asParameters(clusters, visitTimeout);
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
                    visits.computeIfPresent(this, (__, session) -> { session.destroy(); return null; });
                }
            });
            visits.put(parameters.getControlHandler(), access.createVisitorSession(parameters));
            if (done.get())
                visits.computeIfPresent(parameters.getControlHandler(), (__, session) -> { session.destroy(); return null; });
        }
        catch (IllegalArgumentException | ParseException e) {
            context.error(BAD_REQUEST, Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            context.error(ERROR, Exceptions.toMessageString(e));
        }
    }

    public String routeToCluster(String cluster) {
        return resolveCluster(Optional.of(cluster), clusters).route();
    }


    public enum ErrorType {
        OVERLOAD,
        NOT_FOUND,
        PRECONDITION_FAILED,
        BAD_REQUEST,
        TIMEOUT,
        ERROR;
    }


    /** Context for reacting to the progress of a visitor session. Completion signalled by an optional progress token. */
    public static class VisitOperationsContext extends Context<Optional<String>> {

        private final Consumer<Document> onDocument;

        public VisitOperationsContext(BiConsumer<ErrorType, String> onError, Consumer<Optional<String>> onSuccess, Consumer<Document> onDocument) {
            super(onError, onSuccess);
            this.onDocument = onDocument;
        }

        void document(Document document) {
            if ( ! handled())
                onDocument.accept(document);
        }

    }


    /** Context for a document operation. */
    public static class OperationContext extends Context<Optional<Document>> {

        public OperationContext(BiConsumer<ErrorType, String> onError, Consumer<Optional<Document>> onSuccess) {
            super(onError, onSuccess);
        }

    }


    public static class VisitorOptions {

        private final Optional<String> cluster;
        private final Optional<String> namespace;
        private final Optional<String> documentType;
        private final Optional<Group> group;
        private final Optional<String> selection;
        private final Optional<String> fieldSet;
        private final Optional<String> continuation;
        private final Optional<String> bucketSpace;
        private final Optional<Integer> wantedDocumentCount;
        private final Optional<Integer> concurrency;

        private VisitorOptions(Optional<String> cluster, Optional<String> documentType, Optional<String> namespace,
                               Optional<Group> group, Optional<String> selection, Optional<String> fieldSet,
                               Optional<String> continuation,Optional<String> bucketSpace,
                               Optional<Integer> wantedDocumentCount, Optional<Integer> concurrency) {
            this.cluster = cluster;
            this.namespace = namespace;
            this.documentType = documentType;
            this.group = group;
            this.selection = selection;
            this.fieldSet = fieldSet;
            this.continuation = continuation;
            this.bucketSpace = bucketSpace;
            this.wantedDocumentCount = wantedDocumentCount;
            this.concurrency = concurrency;
        }

        private VisitorParameters asParameters(Map<String, StorageCluster> clusters, Duration visitTimeout) {
            if (cluster.isEmpty() && documentType.isEmpty())
                throw new IllegalArgumentException("Must set 'cluster' parameter to a valid content cluster id when visiting at a root /document/v1/ level");

            VisitorParameters parameters = new VisitorParameters(Stream.of(selection,
                                                                           documentType,
                                                                           namespace.map(value -> "id.namespace=='" + value + "'"),
                                                                           group.map(Group::selection))
                                                                       .flatMap(Optional::stream)
                                                                       .reduce(new StringJoiner(") and (", "(", ")").setEmptyValue(""), // don't mind the lonely chicken to the right
                                                                               StringJoiner::add,
                                                                               StringJoiner::merge)
                                                                       .toString());

            continuation.map(ProgressToken::fromSerializedString).ifPresent(parameters::setResumeToken);
            parameters.setFieldSet(fieldSet.orElse(documentType.map(type -> type + ":[document]").orElse(AllFields.NAME)));
            wantedDocumentCount.ifPresent(count -> { if (count <= 0) throw new IllegalArgumentException("wantedDocumentCount must be positive"); });
            parameters.setMaxTotalHits(wantedDocumentCount.orElse(1 << 10));
            concurrency.ifPresent(value -> { if (value <= 0) throw new IllegalArgumentException("concurrency must be positive"); });
            parameters.setThrottlePolicy(new StaticThrottlePolicy().setMaxPendingCount(concurrency.orElse(1)));
            parameters.setTimeoutMs(visitTimeout.toMillis());
            parameters.visitInconsistentBuckets(true);
            parameters.setPriority(DocumentProtocol.Priority.NORMAL_4);

            StorageCluster storageCluster = resolveCluster(cluster, clusters);
            parameters.setRoute(storageCluster.route());
            parameters.setBucketSpace(resolveBucket(storageCluster,
                                                    documentType,
                                                    List.of(FixedBucketSpaces.defaultSpace(), FixedBucketSpaces.globalSpace()),
                                                    bucketSpace));

            return parameters;
        }

        public static Builder builder() { return new Builder(); }


        public static class Builder {

            private String cluster;
            private String documentType;
            private String namespace;
            private Group group;
            private String selection;
            private String fieldSet;
            private String continuation;
            private String bucketSpace;
            private Integer wantedDocumentCount;
            private Integer concurrency;

            public Builder cluster(String cluster) {
                this.cluster = cluster;
                return this;
            }

            public Builder documentType(String documentType) {
                this.documentType = documentType;
                return this;
            }

            public Builder namespace(String namespace) {
                this.namespace = namespace;
                return this;
            }

            public Builder group(Group group) {
                this.group = group;
                return this;
            }

            public Builder selection(String selection) {
                this.selection = selection;
                return this;
            }

            public Builder fieldSet(String fieldSet) {
                this.fieldSet = fieldSet;
                return this;
            }

            public Builder continuation(String continuation) {
                this.continuation = continuation;
                return this;
            }

            public Builder bucketSpace(String bucketSpace) {
                this.bucketSpace = bucketSpace;
                return this;
            }

            public Builder wantedDocumentCount(Integer wantedDocumentCount) {
                this.wantedDocumentCount = wantedDocumentCount;
                return this;
            }

            public Builder concurrency(Integer concurrency) {
                this.concurrency = concurrency;
                return this;
            }

            public VisitorOptions build() {
                return new VisitorOptions(Optional.ofNullable(cluster), Optional.ofNullable(documentType),
                                          Optional.ofNullable(namespace), Optional.ofNullable(group),
                                          Optional.ofNullable(selection), Optional.ofNullable(fieldSet),
                                          Optional.ofNullable(continuation), Optional.ofNullable(bucketSpace),
                                          Optional.ofNullable(wantedDocumentCount), Optional.ofNullable(concurrency));
            }

        }

    }


    public static class Group {

        private final String value;
        private final String docIdPart;
        private final String selection;

        private Group(String value, String docIdPart, String selection) {
            Text.validateTextString(value)
                .ifPresent(codePoint -> { throw new IllegalArgumentException(String.format("Illegal code point U%04X in group", codePoint)); });
            this.value = value;
            this.docIdPart = docIdPart;
            this.selection = selection;
        }

        public static Group of(long value) { return new Group(Long.toString(value), "n=" + value, "id.user==" + value); }
        public static Group of(String value) { return new Group(value, "g=" + value, "id.group=='" + value.replaceAll("'", "\\'") + "'"); }

        public String value() { return value; }
        public String docIdPart() { return docIdPart; }
        public String selection() { return selection; }

    }


    /** Rejects operation if retry queue is full; otherwise starts a timer for the given task, and attempts to send it.  */
    private void accept(Supplier<Result> operation, OperationContext context) {
        timeouts.add(operation, context);
        send(operation, context);
    }

    /** Sends the given operation through the async session of this, enqueueing a retry if throttled, unless overloaded. */
    private void send(Supplier<Result> operation, OperationContext context) {
        Result result = operation.get();
        switch (result.type()) {
            case SUCCESS:
                outstanding.merge(result.getRequestId(), Completion.of(context), Completion::merge);
                break;
            case TRANSIENT_ERROR:
                if ( ! throttled.add(operation, context))
                    context.error(OVERLOAD, maxThrottled + " requests already in retry queue");
                break;
            default:
                log.log(WARNING, "Unknown result type '" + result.type() + "'");
            case FATAL_ERROR: // intentional fallthrough
                context.error(ERROR, result.getError().getMessage());
        }
    }

    private void handle(Response response) {
        outstanding.merge(response.getRequestId(), Completion.of(response), Completion::merge);
    }

    private static ErrorType toErrorType(Response.Outcome outcome) {
        switch (outcome) {
            case NOT_FOUND:
                return NOT_FOUND;
            case CONDITION_FAILED:
                return PRECONDITION_FAILED;
            default:
                log.log(WARNING, "Unexpected response outcome: " + outcome);
            case ERROR: // intentional fallthrough
                return ERROR;
        }
    }


    /** The executor will call <em>exactly one</em> callback <em>exactly once</em> for contexts submitted to it. */
    private static class Context<T> {

        private final AtomicBoolean handled = new AtomicBoolean();
        private final BiConsumer<ErrorType, String> onError;
        private final Consumer<T> onSuccess;

        Context(BiConsumer<ErrorType, String> onError, Consumer<T> onSuccess) {
            this.onError = onError;
            this.onSuccess = onSuccess;
        }

        void error(ErrorType type, String message) {
            if ( ! handled.getAndSet(true))
                onError.accept(type, message);
        }

        void success(T result) {
            if ( ! handled.getAndSet(true))
                onSuccess.accept(result);
        }

        boolean handled() {
            return handled.get();
        }

    }


    private static class Completion {

        private final OperationContext context;
        private final Response response;

        private Completion(OperationContext context, Response response) {
            this.context = context;
            this.response = response;
        }

        static Completion of(OperationContext context) {
            return new Completion(requireNonNull(context), null);
        }

        static Completion of(Response response) {
            return new Completion(null, requireNonNull(response));
        }

        Completion merge(Completion other) {
            if (context == null)
                complete(other.context, response);
            else
                complete(context, other.response);
            return null;
        }

        private void complete(OperationContext context, Response response) {
            if (response.isSuccess())
                context.success(response instanceof DocumentResponse ? Optional.ofNullable(((DocumentResponse) response).getDocument())
                                                                     : Optional.empty());
            else
                context.error(toErrorType(response.outcome()), response.getTextMessage());
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

        public DelayQueue(long maxSize, BiConsumer<Supplier<Result>, OperationContext> action, Duration delay, Clock clock) {
            if (maxSize < 0)
                throw new IllegalArgumentException("Max size cannot be negative, but was " + maxSize);
            if (delay.isNegative())
                throw new IllegalArgumentException("Delay cannot be negative, but was " + delay);

            this.maxSize = maxSize;
            this.delay = delay;
            this.defaultWaitMillis = Math.min(delay.toMillis(), 100); // Run regularly to evict handled contexts.
            this.clock = requireNonNull(clock);
            this.maintainer = new Thread(() -> maintain(action));
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
                    Thread.sleep(grace.toMillis());
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
        private void maintain(BiConsumer<Supplier<Result>, OperationContext> action) {
            while ( ! Thread.currentThread().isInterrupted()) {
                try {
                    Instant waitUntil = null;
                    Iterator<Delayed> operations = queue.iterator();
                    while (operations.hasNext()) {
                        Delayed delayed = operations.next();
                        // Already handled: remove and continue.
                        if (delayed.context().handled()) {
                            operations.remove();
                            size.decrementAndGet();
                            continue;
                        }
                        // Ready for action: remove from queue and run.
                        if (delayed.readyAt().isBefore(clock.instant())) {
                            operations.remove();
                            size.decrementAndGet();
                            action.accept(delayed.operation(), delayed.context());
                            continue;
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


    private static StorageCluster resolveCluster(Optional<String> wanted, Map<String, StorageCluster> clusters) {
        if (clusters.isEmpty())
            throw new IllegalArgumentException("Your Vespa deployment has no content clusters, so the document API is not enabled.");

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
