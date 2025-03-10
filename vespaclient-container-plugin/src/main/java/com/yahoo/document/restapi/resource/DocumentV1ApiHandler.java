// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.concurrent.SystemTimer;
import com.yahoo.container.core.HandlerMetricContextUtil;
import com.yahoo.container.core.documentapi.VespaDocumentAccess;
import com.yahoo.container.jdisc.ContentChannelOutputStream;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.FixedBucketSpaces;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.fieldset.DocIdOnly;
import com.yahoo.document.fieldset.DocumentOnly;
import com.yahoo.document.idstring.IdIdString;
import com.yahoo.document.json.DocumentOperationType;
import com.yahoo.document.json.JsonReader;
import com.yahoo.document.json.JsonWriter;
import com.yahoo.document.json.ParsedDocumentOperation;
import com.yahoo.document.restapi.DocumentOperationExecutorConfig;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.AckToken;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentOperationParameters;
import com.yahoo.documentapi.DocumentResponse;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.Response.Outcome;
import com.yahoo.documentapi.Result;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorControlSession;
import com.yahoo.documentapi.VisitorDataHandler;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.RemoveDocumentMessage;
import com.yahoo.documentapi.metrics.DocumentApiMetrics;
import com.yahoo.documentapi.metrics.DocumentOperationStatus;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.Response.Status;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.BufferedContentChannel;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ReadableContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.handler.UnsafeContentInputStream;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.messagebus.DynamicThrottlePolicy;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.messagebus.Trace;
import com.yahoo.messagebus.TraceNode;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.restapi.Path;
import com.yahoo.search.query.ParameterParser;
import com.yahoo.tensor.serialization.JsonFormat;
import com.yahoo.text.Text;
import com.yahoo.vespa.config.content.AllClustersBucketSpacesConfig;
import com.yahoo.vespa.http.server.Headers;
import com.yahoo.vespa.http.server.MetricNames;
import com.yahoo.yolean.Exceptions;
import com.yahoo.yolean.Exceptions.RunnableThrowingIOException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static com.yahoo.documentapi.DocumentOperationParameters.parameters;
import static com.yahoo.jdisc.http.HttpRequest.Method.DELETE;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpRequest.Method.OPTIONS;
import static com.yahoo.jdisc.http.HttpRequest.Method.POST;
import static com.yahoo.jdisc.http.HttpRequest.Method.PUT;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * Asynchronous HTTP handler for /document/v1
 *
 * @author jonmv
 */
public final class DocumentV1ApiHandler extends AbstractRequestHandler {

    private static final Duration defaultTimeout = Duration.ofSeconds(180); // Match document API default timeout.
    private static final Duration handlerTimeout = Duration.ofMillis(100); // Extra time to allow for handler, JDisc and jetty to complete.

    private static final Logger log = Logger.getLogger(DocumentV1ApiHandler.class.getName());
    private static final Parser<Integer> integerParser = Integer::parseInt;
    private static final Parser<Long> unsignedLongParser = Long::parseUnsignedLong;
    private static final Parser<Long> timeoutMillisParser = value -> ParameterParser.asMilliSeconds(value, defaultTimeout.toMillis());
    private static final Parser<Boolean> booleanParser = Boolean::parseBoolean;

    private static final CompletionHandler logException = new CompletionHandler() {
        @Override public void completed() { }
        @Override public void failed(Throwable t) {
            log.log(FINE, "Exception writing or closing response data", t);
        }
    };

    private static final ContentChannel ignoredContent = new ContentChannel() {
        @Override public void write(ByteBuffer buf, CompletionHandler handler) { handler.completed(); }
        @Override public void close(CompletionHandler handler) { handler.completed(); }
    };

    private static final JsonFactory jsonFactory = new JsonFactoryBuilder()
            .streamReadConstraints(StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build())
            .build();

    private static final String CREATE = "create";
    private static final String CONDITION = "condition";
    private static final String ROUTE = "route";
    private static final String FIELD_SET = "fieldSet";
    private static final String SELECTION = "selection";
    private static final String CLUSTER = "cluster";
    private static final String DESTINATION_CLUSTER = "destinationCluster";
    private static final String CONTINUATION = "continuation";
    private static final String WANTED_DOCUMENT_COUNT = "wantedDocumentCount";
    private static final String CONCURRENCY = "concurrency";
    private static final String BUCKET_SPACE = "bucketSpace";
    private static final String TIME_CHUNK = "timeChunk";
    private static final String TIMEOUT = "timeout";
    private static final String TRACELEVEL = "tracelevel";
    private static final String STREAM = "stream";
    private static final String SLICES = "slices";
    private static final String SLICE_ID = "sliceId";
    private static final String DRY_RUN = "dryRun";
    private static final String FROM_TIMESTAMP = "fromTimestamp";
    private static final String TO_TIMESTAMP = "toTimestamp";
    private static final String INCLUDE_REMOVES = "includeRemoves";

    private final Clock clock;
    private final Duration visitTimeout;
    private final Metric metric;
    private final DocumentApiMetrics metrics;
    private final DocumentOperationParser parser;
    private final long maxThrottled;
    private final long maxThrottledAgeNS;
    private final DocumentAccess access;
    private final AsyncSession asyncSession;
    private final Map<String, StorageCluster> clusters;
    private final Deque<Operation> operations;
    private final Deque<BooleanSupplier> visitOperations = new ConcurrentLinkedDeque<>();
    private final AtomicLong enqueued = new AtomicLong();
    private final AtomicLong outstanding = new AtomicLong();
    private final Map<VisitorControlHandler, VisitorSession> visits = new ConcurrentHashMap<>();
    private final ScheduledExecutorService dispatcher = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory("document-api-handler-"));
    private final ScheduledExecutorService visitDispatcher = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory("document-api-handler-visit-"));
    private final Map<String, Map<Method, Handler>> handlers = defineApi();

    @Inject
    public DocumentV1ApiHandler(Metric metric,
                                MetricReceiver metricReceiver,
                                VespaDocumentAccess documentAccess,
                                DocumentmanagerConfig documentManagerConfig,
                                ClusterListConfig clusterListConfig,
                                AllClustersBucketSpacesConfig bucketSpacesConfig,
                                DocumentOperationExecutorConfig executorConfig) {
        this(Clock.systemUTC(), Duration.ofSeconds(5), metric, metricReceiver, documentAccess,
             documentManagerConfig, executorConfig, clusterListConfig, bucketSpacesConfig);
    }

    DocumentV1ApiHandler(Clock clock, Duration visitTimeout, Metric metric, MetricReceiver metricReceiver, DocumentAccess access,
                         DocumentmanagerConfig documentmanagerConfig, DocumentOperationExecutorConfig executorConfig,
                         ClusterListConfig clusterListConfig, AllClustersBucketSpacesConfig bucketSpacesConfig) {
        this.clock = clock;
        this.visitTimeout = visitTimeout;
        this.parser = new DocumentOperationParser(documentmanagerConfig);
        this.metric = metric;
        this.metrics = new DocumentApiMetrics(metricReceiver, "documentV1");
        this.maxThrottled = executorConfig.maxThrottled();
        log.info("maxThrottled=" + maxThrottled);
        this.maxThrottledAgeNS = (long) (executorConfig.maxThrottledAge() * 1_000_000_000.0);
        this.access = access;
        this.asyncSession = access.createAsyncSession(new AsyncParameters());
        this.clusters = parseClusters(clusterListConfig, bucketSpacesConfig);
        this.operations = new ConcurrentLinkedDeque<>();
        long resendDelayMS = SystemTimer.adjustTimeoutByDetectedHz(Duration.ofMillis(executorConfig.resendDelayMillis())).toMillis();

        // TODO: Here it would be better to have dedicated threads with different wait depending on blocked or empty.
        this.dispatcher.scheduleWithFixedDelay(this::dispatchEnqueued, resendDelayMS, resendDelayMS, MILLISECONDS);
        this.visitDispatcher.scheduleWithFixedDelay(this::dispatchVisitEnqueued, resendDelayMS, resendDelayMS, MILLISECONDS);
    }

    // ------------------------------------------------ Requests -------------------------------------------------

    @Override
    public ContentChannel handleRequest(Request rawRequest, ResponseHandler rawResponseHandler) {
        HandlerMetricContextUtil.onHandle(rawRequest, metric, getClass());
        ResponseHandler responseHandler = response -> {
            HandlerMetricContextUtil.onHandled(rawRequest, metric, getClass());
            return rawResponseHandler.handleResponse(response);
        };

        HttpRequest request = (HttpRequest) rawRequest;
        try {
            // Set a higher HTTP layer timeout than the document API timeout, to prefer triggering the latter.
            request.setTimeout(doomMillis(request) - clock.millis(), MILLISECONDS);

            Path requestPath = Path.withoutValidation(request.getUri()); // No segment validation here, as document IDs can be anything.
            for (String path : handlers.keySet()) {
                if (requestPath.matches(path)) {
                    Map<Method, Handler> methods = handlers.get(path);
                    if (methods.containsKey(request.getMethod()))
                        return methods.get(request.getMethod()).handle(request, new DocumentPath(requestPath, request.getUri().getRawPath()), responseHandler);

                    if (request.getMethod() == OPTIONS)
                        options(methods.keySet(), responseHandler);

                    methodNotAllowed(request, methods.keySet(), responseHandler);
                }
            }
            notFound(request, handlers.keySet(), responseHandler);
        }
        catch (IllegalArgumentException e) {
            badRequest(request, e, responseHandler);
        }
        catch (RuntimeException e) {
            serverError(request, e, responseHandler);
        }
        return ignoredContent;
    }

    @Override
    public void handleTimeout(Request request, ResponseHandler responseHandler) {
        HttpRequest httpRequest = (HttpRequest) request;
        timeout(httpRequest, "Timeout after " + (getProperty(httpRequest, TIMEOUT, timeoutMillisParser).orElse(defaultTimeout.toMillis())) + "ms", responseHandler);
    }

    @Override
    public void destroy() {
        Instant doom = clock.instant().plus(Duration.ofSeconds(30));

        // This blocks until all visitors are done. These, in turn, may require the asyncSession to be alive
        // to be able to run, as well as dispatch of operations against it, which is done by visitDispatcher.
        visits.values().forEach(VisitorSession::abort);
        visits.values().forEach(VisitorSession::destroy);

        // Shut down both dispatchers, so only we empty the queues of outstanding operations, and can be sure they're empty.
        dispatcher.shutdown();
        visitDispatcher.shutdown();
        while ( ! (operations.isEmpty() && visitOperations.isEmpty()) && clock.instant().isBefore(doom)) {
            dispatchEnqueued();
            dispatchVisitEnqueued();
        }

        if ( ! operations.isEmpty())
            log.log(WARNING, "Failed to empty request queue before shutdown timeout — " + operations.size() + " requests left");

        if ( ! visitOperations.isEmpty())
            log.log(WARNING, "Failed to empty visitor operations queue before shutdown timeout — " + visitOperations.size() + " operations left");

        try {
            while (outstanding.get() > 0 && clock.instant().isBefore(doom))
                Thread.sleep(Math.max(1, Duration.between(clock.instant(), doom).toMillis()));

            if ( ! dispatcher.awaitTermination(Duration.between(clock.instant(), doom).toMillis(), MILLISECONDS))
                dispatcher.shutdownNow();

            if ( ! visitDispatcher.awaitTermination(Duration.between(clock.instant(), doom).toMillis(), MILLISECONDS))
                visitDispatcher.shutdownNow();
        }
        catch (InterruptedException e) {
            log.log(WARNING, "Interrupted waiting for /document/v1 executor to shut down");
        }
        finally {
            asyncSession.destroy();
            if (outstanding.get() != 0)
                log.log(WARNING, "Failed to receive a response to " + outstanding.get() + " outstanding document operations during shutdown");
        }
    }

    @FunctionalInterface
    interface Handler {
        ContentChannel handle(HttpRequest request, DocumentPath path, ResponseHandler handler);
    }

    /** Defines all paths/methods handled by this handler. */
    private Map<String, Map<Method, Handler>> defineApi() {
        Map<String, Map<Method, Handler>> handlers = new LinkedHashMap<>();

        handlers.put("/document/v1/",
                     Map.of(GET, this::getDocuments,
                            POST, this::postDocuments,
                            DELETE, this::deleteDocuments));

        handlers.put("/document/v1/{namespace}/{documentType}/docid/",
                     Map.of(GET, this::getDocuments,
                            POST, this::postDocuments,
                            PUT, this::putDocuments,
                            DELETE, this::deleteDocuments));

        handlers.put("/document/v1/{namespace}/{documentType}/group/{group}/",
                     Map.of(GET, this::getDocuments,
                            POST, this::postDocuments,
                            PUT, this::putDocuments,
                            DELETE, this::deleteDocuments));

        handlers.put("/document/v1/{namespace}/{documentType}/number/{number}/",
                     Map.of(GET, this::getDocuments,
                            POST, this::postDocuments,
                            PUT, this::putDocuments,
                            DELETE, this::deleteDocuments));

        handlers.put("/document/v1/{namespace}/{documentType}/docid/{*}",
                     Map.of(GET, this::getDocument,
                            POST, this::postDocument,
                            PUT, this::putDocument,
                            DELETE, this::deleteDocument));

        handlers.put("/document/v1/{namespace}/{documentType}/group/{group}/{*}",
                     Map.of(GET, this::getDocument,
                            POST, this::postDocument,
                            PUT, this::putDocument,
                            DELETE, this::deleteDocument));

        handlers.put("/document/v1/{namespace}/{documentType}/number/{number}/{*}",
                     Map.of(GET, this::getDocument,
                            POST, this::postDocument,
                            PUT, this::putDocument,
                            DELETE, this::deleteDocument));

        return Collections.unmodifiableMap(handlers);
    }

    private ContentChannel getDocuments(HttpRequest request, DocumentPath path, ResponseHandler handler) {
        disallow(request, DRY_RUN);
        enqueueAndDispatch(request, handler, () -> {
            boolean streamed = getProperty(request, STREAM, booleanParser).orElse(false);
            VisitorParameters parameters = parseGetParameters(request, path, streamed);
            return () -> {
                visitAndWrite(request, parameters, handler, streamed);
                return true; // VisitorSession has its own throttle handling.
            };
        });
        return ignoredContent;
    }

    private ContentChannel postDocuments(HttpRequest request, DocumentPath path, ResponseHandler handler) {
        disallow(request, DRY_RUN);
        enqueueAndDispatch(request, handler, () -> {
            StorageCluster destination = resolveCluster(Optional.of(requireProperty(request, DESTINATION_CLUSTER)), clusters);
            VisitorParameters parameters = parseParameters(request, path);
            parameters.setRemoteDataHandler("[Content:cluster=" + destination.name() + "]"); // Bypass indexing.
            parameters.setFieldSet(DocumentOnly.NAME);
            return () -> {
                visitWithRemote(request, parameters, handler);
                return true; // VisitorSession has its own throttle handling.
            };
        });
        return ignoredContent;
    }

    private ContentChannel putDocuments(HttpRequest request, DocumentPath path, ResponseHandler handler) {
        disallow(request, DRY_RUN);
        return new ForwardingContentChannel(in -> {
            enqueueAndDispatch(request, handler, () -> {
                StorageCluster cluster = resolveCluster(Optional.of(requireProperty(request, CLUSTER)), clusters);
                VisitorParameters parameters = parseParameters(request, path);
                parameters.setFieldSet(DocIdOnly.NAME);
                String type = path.documentType().orElseThrow(() -> new IllegalStateException("Document type must be specified for mass updates"));
                IdIdString dummyId = new IdIdString("dummy", type, "", "");
                ParsedDocumentOperation update = parser.parseUpdate(in, dummyId.toString());
                update.operation().setCondition(new TestAndSetCondition(requireProperty(request, SELECTION)));
                return () -> {
                    visitAndUpdate(request, parameters, update.fullyApplied(), handler, (DocumentUpdate)update.operation(), cluster.name());
                    return true; // VisitorSession has its own throttle handling.
                };
            });
        });
    }

    private ContentChannel deleteDocuments(HttpRequest request, DocumentPath path, ResponseHandler handler) {
        disallow(request, DRY_RUN);
        enqueueAndDispatch(request, handler, () -> {
            VisitorParameters parameters = parseParameters(request, path);
            parameters.setFieldSet(DocIdOnly.NAME);
            TestAndSetCondition condition = new TestAndSetCondition(requireProperty(request, SELECTION));
            StorageCluster cluster = resolveCluster(Optional.of(requireProperty(request, CLUSTER)), clusters);
            return () -> {
                visitAndDelete(request, parameters, handler, condition, cluster.name());
                return true; // VisitorSession has its own throttle handling.
            };
        });
        return ignoredContent;
    }

    private ContentChannel getDocument(HttpRequest request, DocumentPath path, ResponseHandler rawHandler) {
        ResponseHandler handler = new MeasuringResponseHandler(request, rawHandler, com.yahoo.documentapi.metrics.DocumentOperationType.GET, clock.instant());
        disallow(request, DRY_RUN);
        enqueueAndDispatch(request, handler, () -> {
            DocumentOperationParameters rawParameters = parametersFromRequest(request, CLUSTER, FIELD_SET);
            if (rawParameters.fieldSet().isEmpty())
                rawParameters = rawParameters.withFieldSet(path.documentType().orElseThrow() + ":[document]");
            DocumentOperationParameters parameters = rawParameters.withResponseHandler(response -> {
                outstanding.decrementAndGet();
                handle(path, request, handler, response, (document, jsonResponse) -> {
                    if (document != null) {
                        jsonResponse.writeSingleDocument(document);
                        jsonResponse.commit(Response.Status.OK);
                    }
                    else
                        jsonResponse.commit(Response.Status.NOT_FOUND);
                });
            });
            return () -> dispatchOperation(() -> asyncSession.get(path.id(), parameters));
        });
        return ignoredContent;
    }

    private ContentChannel postDocument(HttpRequest request, DocumentPath path, ResponseHandler rawHandler) {
        ResponseHandler handler = new MeasuringResponseHandler(
                request, rawHandler, com.yahoo.documentapi.metrics.DocumentOperationType.PUT, clock.instant());
        if (getProperty(request, DRY_RUN, booleanParser).orElse(false)) {
            handleFeedOperation(path, true, handler, new com.yahoo.documentapi.Response(-1));
            return ignoredContent;
        }

        return new ForwardingContentChannel(in -> {
            enqueueAndDispatch(request, handler, () -> {
                ParsedDocumentOperation parsed = parser.parsePut(in, path.id().toString());
                DocumentPut put = (DocumentPut)parsed.operation();
                getProperty(request, CONDITION).map(TestAndSetCondition::new).ifPresent(put::setCondition);
                getProperty(request, CREATE, booleanParser).ifPresent(put::setCreateIfNonExistent);
                DocumentOperationParameters parameters = parametersFromRequest(request, ROUTE)
                        .withResponseHandler(response -> {
                            outstanding.decrementAndGet();
                            updatePutMetrics(response.outcome(), latencyOf(request), put.getCreateIfNonExistent());
                            handleFeedOperation(path, parsed.fullyApplied(), handler, response);
                        });
                return () -> dispatchOperation(() -> asyncSession.put(put, parameters));
            });
        });
    }

    private ContentChannel putDocument(HttpRequest request, DocumentPath path, ResponseHandler rawHandler) {
        ResponseHandler handler = new MeasuringResponseHandler(
                request, rawHandler, com.yahoo.documentapi.metrics.DocumentOperationType.UPDATE, clock.instant());
        if (getProperty(request, DRY_RUN, booleanParser).orElse(false)) {
            handleFeedOperation(path, true, handler, new com.yahoo.documentapi.Response(-1));
            return ignoredContent;
        }

        return new ForwardingContentChannel(in -> {
            enqueueAndDispatch(request, handler, () -> {
                ParsedDocumentOperation parsed = parser.parseUpdate(in, path.id().toString());
                DocumentUpdate update = (DocumentUpdate)parsed.operation();
                getProperty(request, CONDITION).map(TestAndSetCondition::new).ifPresent(update::setCondition);
                getProperty(request, CREATE, booleanParser).ifPresent(update::setCreateIfNonExistent);
                DocumentOperationParameters parameters = parametersFromRequest(request, ROUTE)
                        .withResponseHandler(response -> {
                            outstanding.decrementAndGet();
                            updateUpdateMetrics(response.outcome(), latencyOf(request), update.getCreateIfNonExistent());
                            handleFeedOperation(path, parsed.fullyApplied(), handler, response);
                        });
                return () -> dispatchOperation(() -> asyncSession.update(update, parameters));
            });
        });
    }

    private ContentChannel deleteDocument(HttpRequest request, DocumentPath path, ResponseHandler rawHandler) {
        ResponseHandler handler = new MeasuringResponseHandler(
                request, rawHandler, com.yahoo.documentapi.metrics.DocumentOperationType.REMOVE, clock.instant());
        if (getProperty(request, DRY_RUN, booleanParser).orElse(false)) {
            handleFeedOperation(path, true, handler, new com.yahoo.documentapi.Response(-1));
            return ignoredContent;
        }

        enqueueAndDispatch(request, handler, () -> {
            DocumentRemove remove = new DocumentRemove(path.id());
            getProperty(request, CONDITION).map(TestAndSetCondition::new).ifPresent(remove::setCondition);
            DocumentOperationParameters parameters = parametersFromRequest(request, ROUTE)
                    .withResponseHandler(response -> {
                        outstanding.decrementAndGet();
                        updateRemoveMetrics(response.outcome(), latencyOf(request));
                        handleFeedOperation(path, true, handler, response);
                    });
            return () -> dispatchOperation(() -> asyncSession.remove(remove, parameters));
        });
        return ignoredContent;
    }

    private DocumentOperationParameters parametersFromRequest(HttpRequest request, String... names) {
        DocumentOperationParameters parameters = getProperty(request, TRACELEVEL, integerParser).map(parameters()::withTraceLevel)
                                                                                                .orElse(parameters());
        parameters = parameters.withDeadline(Instant.ofEpochMilli(doomMillis(request)).minus(handlerTimeout));
        for (String name : names)
            parameters = switch (name) {
                case CLUSTER ->
                        getProperty(request, CLUSTER)
                                .map(cluster -> resolveCluster(Optional.of(cluster), clusters).name())
                                .map(parameters::withRoute)
                                .orElse(parameters);
                case FIELD_SET -> getProperty(request, FIELD_SET).map(parameters::withFieldSet).orElse(parameters);
                case ROUTE -> getProperty(request, ROUTE).map(parameters::withRoute).orElse(parameters);
                default ->
                        throw new IllegalArgumentException("Unrecognized document operation parameter name '" + name + "'");
            };
        return parameters;
    }

    /** Dispatches enqueued requests until one is blocked. */
    void dispatchEnqueued() {
        try {
            while (dispatchFirst());
        }
        catch (Exception e) {
            log.log(WARNING, "Uncaught exception in /document/v1 dispatch thread", e);
        }
    }

    /** Attempts to dispatch the first enqueued operations, and returns whether this was successful. */
    private boolean dispatchFirst() {
        Operation operation = operations.poll();
        if (operation == null)
            return false;

        if (operation.dispatch()) {
            enqueued.decrementAndGet();
            return true;
        }
        operations.push(operation);
        return false;
    }

    /** Dispatches enqueued requests until one is blocked. */
    private void dispatchVisitEnqueued() {
        try {
            while (dispatchFirstVisit());
        }
        catch (Exception e) {
            log.log(WARNING, "Uncaught exception in /document/v1 dispatch thread", e);
        }
    }

    /** Attempts to dispatch the first enqueued visit operations, and returns whether this was successful. */
    private boolean dispatchFirstVisit() {
        BooleanSupplier operation = visitOperations.poll();
        if (operation == null)
            return false;

        if (operation.getAsBoolean())
            return true;

        visitOperations.push(operation);
        return false;
    }

    private long qAgeNS(HttpRequest request) {
        Operation oldest = operations.peek();
        return (oldest != null)
                ? (request.relativeCreatedAtNanoTime() - oldest.request.relativeCreatedAtNanoTime())
                : 0;
    }

    /**
     * Enqueues the given request and operation, or responds with "overload" if the queue is full,
     * and then attempts to dispatch an enqueued operation from the head of the queue.
     */
    private void enqueueAndDispatch(HttpRequest request, ResponseHandler handler, Supplier<BooleanSupplier> operationParser) {
        if (maxThrottled == 0) {
            var operation = new Operation(request, handler, operationParser);
            if (!operation.dispatch()) {
                overload(request, "Rejecting execution due to overload: "
                        + (long)asyncSession.getCurrentWindowSize() + " requests already enqueued", handler);
            }
            return;
        }
        long numQueued = enqueued.incrementAndGet();
        if (numQueued > maxThrottled) {
            enqueued.decrementAndGet();
            overload(request, "Rejecting execution due to overload: "
                    + maxThrottled + " requests already enqueued", handler);
            return;
        }
        if (numQueued > 1) {
            long ageNS = qAgeNS(request);
            if (ageNS > maxThrottledAgeNS) {
                enqueued.decrementAndGet();
                overload(request, "Rejecting execution due to overload: "
                        + maxThrottledAgeNS / 1_000_000_000.0 + " seconds worth of work enqueued", handler);
                return;
            }
        }
        operations.offer(new Operation(request, handler, operationParser));
        dispatchFirst();
    }


    // ------------------------------------------------ Responses ------------------------------------------------

    /** Class for writing and returning JSON responses to document operations in a thread safe manner. */
    private static class JsonResponse implements AutoCloseable {

        private static final ByteBuffer emptyBuffer = ByteBuffer.wrap(new byte[0]);
        private static final int FLUSH_SIZE = 128;

        private final BufferedContentChannel buffer = new BufferedContentChannel();
        private final OutputStream out = new ContentChannelOutputStream(buffer);
        private final JsonGenerator json;
        private final ResponseHandler handler;
        private final HttpRequest request;
        private final Queue<CompletionHandler> acks = new ConcurrentLinkedQueue<>();
        private final Queue<ByteArrayOutputStream> docs = new ConcurrentLinkedQueue<>();
        private final AtomicLong documentsWritten = new AtomicLong();
        private final AtomicLong documentsFlushed = new AtomicLong();
        private final AtomicLong documentsAcked = new AtomicLong();
        private boolean documentsDone = false;
        private boolean first = true;
        private ContentChannel channel;

        private JsonResponse(ResponseHandler handler, HttpRequest request) throws IOException {
            this.handler = handler;
            this.request = request;
            json = jsonFactory.createGenerator(out);
            json.writeStartObject();
        }

        /** Creates a new JsonResponse with path and id fields written. */
        static JsonResponse create(DocumentPath path, ResponseHandler handler, HttpRequest request) throws IOException {
            JsonResponse response = new JsonResponse(handler, request);
            response.writePathId(path.rawPath());
            response.writeDocId(path.id());
            return response;
        }

        /** Creates a new JsonResponse with path field written. */
        static JsonResponse create(HttpRequest request, ResponseHandler handler) throws IOException {
            JsonResponse response = new JsonResponse(handler, request);
            response.writePathId(request.getUri().getRawPath());
            return response;
        }

        /** Creates a new JsonResponse with path and message fields written. */
        static JsonResponse create(HttpRequest request, String message, ResponseHandler handler) throws IOException {
            JsonResponse response = create(request, handler);
            response.writeMessage(message);
            return response;
        }

        synchronized void commit(int status) throws IOException {
            commit(status, true);
        }

        /** Commits a response with the given status code and some default headers, and writes whatever content is buffered. */
        synchronized void commit(int status, boolean fullyApplied) throws IOException {
            Response response = new Response(status);
            response.headers().add("Content-Type", List.of("application/json; charset=UTF-8"));
            if (! fullyApplied)
                response.headers().add(Headers.IGNORED_FIELDS, "true");
            try {
                channel = handler.handleResponse(response);
                buffer.connectTo(channel);
            }
            catch (RuntimeException e) {
                throw new IOException(e);
            }
        }

        /** Commits a response with the given status code and some default headers, writes buffered content, and closes this. */
        synchronized void respond(int status) throws IOException {
            try (this) {
                commit(status);
            }
        }

        /** Closes the JSON and the output content channel of this. */
        @Override
        public synchronized void close() throws IOException {
            documentsDone = true; // In case we were closed without explicitly closing the documents array.
            try {
                if (channel == null) {
                    log.log(WARNING, "Close called before response was committed, in " + getClass().getName());
                    commit(Response.Status.INTERNAL_SERVER_ERROR);
                }
                json.close(); // Also closes object and array scopes.
                out.close();  // Simply flushes the output stream.
            }
            finally {
                if (channel != null)
                    channel.close(logException); // Closes the response handler's content channel.
            }
        }

        synchronized void writePathId(String path) throws IOException {
            json.writeStringField("pathId", path);
        }

        synchronized void writeMessage(String message) throws IOException {
            json.writeStringField("message", message);
        }

        synchronized void writeDocumentCount(long count) throws IOException {
            json.writeNumberField("documentCount", count);
        }

        synchronized void writeDocId(DocumentId id) throws IOException {
            json.writeStringField("id", id.toString());
        }

        synchronized void writeTrace(Trace trace) throws IOException {
            if (trace != null && ! trace.getRoot().isEmpty()) {
                writeTrace(trace.getRoot());
            }
        }

        private void writeTrace(TraceNode node) throws IOException {
            if (node.hasNote())
                json.writeStringField("message", node.getNote());
            if ( ! node.isLeaf()) {
                json.writeArrayFieldStart(node.isStrict() ? "trace" : "fork");
                for (int i = 0; i < node.getNumChildren(); i++) {
                    json.writeStartObject();
                    writeTrace(node.getChild(i));
                    json.writeEndObject();
                }
                json.writeEndArray();
            }
        }

        private JsonFormat.EncodeOptions tensorOptions() {
            // TODO: Flip default on Vespa 9 to "short-value"
            String format = "short";
            if (request != null && request.parameters().containsKey("format.tensors")) {
                var params = request.parameters().get("format.tensors");
                if (params.size() == 1) {
                    format = params.get(0);
                }
            }
            return switch (format) {
                case "hex" ->
                        new JsonFormat.EncodeOptions(true, false, true);
                case "hex-value" ->
                        new JsonFormat.EncodeOptions(true, true, true);
                default ->
                        // aka "short"
                        new JsonFormat.EncodeOptions(true, false, false);
                case "short-value" ->
                        new JsonFormat.EncodeOptions(true, true, false);
                case "long" ->
                        new JsonFormat.EncodeOptions(false, false, false);
                case "long-value" ->
                        new JsonFormat.EncodeOptions(false, true, false);
            };
        }

        private boolean tensorShortForm() {
            return tensorOptions().shortForm();
        }

        private boolean tensorDirectValues() {
            return tensorOptions().directValues();
        }

        synchronized void writeSingleDocument(Document document) throws IOException {
            new JsonWriter(json, tensorOptions()).writeFields(document);
        }

        synchronized void writeDocumentsArrayStart() throws IOException {
            json.writeArrayFieldStart("documents");
        }

        private interface DocumentWriter {
            void write(ByteArrayOutputStream out) throws IOException;
        }

        /** Writes documents to an internal queue, which is flushed regularly. */
        void writeDocumentValue(Document document, CompletionHandler completionHandler) throws IOException {
            writeDocument(myOut -> {
                try (JsonGenerator myJson = jsonFactory.createGenerator(myOut)) {
                    new JsonWriter(myJson, tensorShortForm(), tensorDirectValues()).write(document);
                }
            }, completionHandler);
        }

        void writeDocumentRemoval(DocumentId id, CompletionHandler completionHandler) throws IOException {
            writeDocument(myOut -> {
                try (JsonGenerator myJson = jsonFactory.createGenerator(myOut)) {
                    myJson.writeStartObject();
                    myJson.writeStringField("remove", id.toString());
                    myJson.writeEndObject();
                }
            }, completionHandler);
        }

        /** Writes documents to an internal queue, which is flushed regularly. */
        void writeDocument(DocumentWriter documentWriter, CompletionHandler completionHandler) throws IOException {
            // Serialise document and add to queue, not necessarily in the order dictated by "written" above,
            // i.e., the first 128 documents in the queue are not necessarily the ones ack'ed early.
            ByteArrayOutputStream myOut = new ByteArrayOutputStream(1);
            myOut.write(','); // Prepend rather than append, to avoid double memory copying.
            documentWriter.write(myOut);
            docs.add(myOut);

            // It is crucial that ACKing of in-flight operations happens _after_ the document payload is
            // visible in the `docs` queue. Otherwise, there is a risk that the ACK sets in motion a
            // full unwind of the entire visitor session from the content node back to the client session
            // (that's us), triggering the `onDone` callback and transitively the final flush of enqueued
            // documents. If `myOut` is then not part of `docs`, it won't be rendered at all.
            // This is a Dark Souls-tier distributed race condition.
            if (completionHandler != null) {
                acks.add(completionHandler);
                ackDocuments();
            }

            // Flush the first FLUSH_SIZE documents in the queue to the network layer if chunk is filled.
            if (documentsWritten.incrementAndGet() % FLUSH_SIZE == 0) {
                flushDocuments();
            }
        }

        void ackDocuments() {
            while (documentsAcked.incrementAndGet() <= documentsFlushed.get() + FLUSH_SIZE) {
                CompletionHandler ack = acks.poll();
                if (ack != null)
                    ack.completed();
                else
                    break;
            }
            documentsAcked.decrementAndGet(); // We overshoot by one above, so decrement again when done.
        }

        synchronized void flushDocuments() throws IOException {
            for (int i = 0; i < FLUSH_SIZE; i++) {
                ByteArrayOutputStream doc = docs.poll();
                if (doc == null)
                    break;

                if ( ! documentsDone) {
                    if (first) { // First chunk, remove leading comma from first document, and flush "json" to "buffer".
                        json.flush();
                        buffer.write(ByteBuffer.wrap(doc.toByteArray(), 1, doc.size() - 1), null);
                        first = false;
                    }
                    else {
                        buffer.write(ByteBuffer.wrap(doc.toByteArray()), null);
                    }
                }
            }

            // Ensure new, eligible acks are done, after flushing these documents.
            buffer.write(emptyBuffer, new CompletionHandler() {
                @Override public void completed() {
                    documentsFlushed.addAndGet(FLUSH_SIZE);
                    ackDocuments();
                }
                @Override public void failed(Throwable t) {
                    // This is typically caused by the client closing the connection during production of the response content.
                    log.log(FINE, "Error writing documents", t);
                    completed();
                }
            });
        }

        synchronized void writeArrayEnd() throws IOException {
            flushDocuments();
            documentsDone = true;
            json.writeEndArray();
        }

        synchronized void writeContinuation(String token) throws IOException {
            json.writeStringField("continuation", token);
        }

    }

    private static void options(Collection<Method> methods, ResponseHandler handler) {
        loggingException(() -> {
            Response response = new Response(Response.Status.NO_CONTENT);
            response.headers().add("Allow", methods.stream().sorted().map(Method::name).collect(joining(",")));
            handler.handleResponse(response).close(logException);
        });
    }

    private static void badRequest(HttpRequest request, IllegalArgumentException e, ResponseHandler handler) {
        loggingException(() -> {
            String message = Exceptions.toMessageString(e);
            log.log(FINE, () -> "Bad request for " + request.getMethod() + " at " + request.getUri().getRawPath() + ": " + message);
            JsonResponse.create(request, message, handler).respond(Response.Status.BAD_REQUEST);
        });
    }

    private static void notFound(HttpRequest request, Collection<String> paths, ResponseHandler handler) {
        loggingException(() -> {
        JsonResponse.create(request,
                           "Nothing at '" + request.getUri().getRawPath() + "'. " +
                           "Available paths are:\n" + String.join("\n", paths),
                            handler)
                    .respond(Response.Status.NOT_FOUND);
        });
    }

    private static void methodNotAllowed(HttpRequest request, Collection<Method> methods, ResponseHandler handler) {
        loggingException(() -> {
            JsonResponse.create(request,
                               "'" + request.getMethod() + "' not allowed at '" + request.getUri().getRawPath() + "'. " +
                               "Allowed methods are: " + methods.stream().sorted().map(Method::name).collect(joining(", ")),
                                handler)
                        .respond(Response.Status.METHOD_NOT_ALLOWED);
        });
    }

    private static void overload(HttpRequest request, String message, ResponseHandler handler) {
        loggingException(() -> {
            log.log(FINE, () -> "Overload handling request " + request.getMethod() + " " + request.getUri().getRawPath() + ": " + message);
            JsonResponse.create(request, message, handler).respond(Response.Status.TOO_MANY_REQUESTS);
        });
    }

    private static void serverError(HttpRequest request, Throwable t, ResponseHandler handler) {
        loggingException(() -> {
            log.log(WARNING, "Uncaught exception handling request " + request.getMethod() + " " + request.getUri().getRawPath(), t);
            JsonResponse.create(request, Exceptions.toMessageString(t), handler).respond(Response.Status.INTERNAL_SERVER_ERROR);
        });
    }

    private static void timeout(HttpRequest request, String message, ResponseHandler handler) {
        loggingException(() -> {
            log.log(FINE, () -> "Timeout handling request " + request.getMethod() + " " + request.getUri().getRawPath() + ": " + message);
            JsonResponse.create(request, message, handler).respond(Response.Status.GATEWAY_TIMEOUT);
        });
    }

    private static void loggingException(RunnableThrowingIOException runnable) {
        try {
            runnable.run();
        }
        catch (Exception e) {
            log.log(FINE, "Failed writing response", e);
        }
    }

    // -------------------------------------------- Document Operations ----------------------------------------

    private static class Operation {

        private final Lock lock = new ReentrantLock();
        private final HttpRequest request;
        private final ResponseHandler handler;
        private BooleanSupplier operation; // The operation to attempt until it returns success.
        private Supplier<BooleanSupplier> parser; // The unparsed operation—getting this will parse it.

        Operation(HttpRequest request, ResponseHandler handler, Supplier<BooleanSupplier> parser) {
            this.request = request;
            this.handler = handler;
            this.parser = parser;
        }

        /**
         * Attempts to dispatch this operation to the document API, and returns whether this completed or not.
         * Returns {@code} true if dispatch was successful, or if it failed fatally; or {@code false} if
         * dispatch should be retried at a later time.
         */
        boolean dispatch() {
            if (request.isCancelled())
                return true;

            if ( ! lock.tryLock())
                throw new IllegalStateException("Concurrent attempts at dispatch — this is a bug");

            try {
                if (operation == null) {
                    operation = parser.get();
                    parser = null;
                }

                return operation.getAsBoolean();
            }
            catch (IllegalArgumentException e) {
                badRequest(request, e, handler);
            }
            catch (RuntimeException e) {
                serverError(request, e, handler);
            }
            finally {
                lock.unlock();
            }
            return true;
        }

    }

    /** Attempts to send the given document operation, returning false if this needs to be retried. */
    private boolean dispatchOperation(Supplier<Result> documentOperation) {
        Result result = documentOperation.get();
        if (result.type() == Result.ResultType.TRANSIENT_ERROR)
            return false;

        if (result.type() == Result.ResultType.FATAL_ERROR)
            throw new DispatchException(new Throwable(result.error().toString()));

        outstanding.incrementAndGet();
        return true;
    }

    private static class DispatchException extends RuntimeException {
        private DispatchException(Throwable cause) { super(cause); }
    }

    /** Readable content channel which forwards data to a reader when closed. */
    static class ForwardingContentChannel implements ContentChannel {

        private final ReadableContentChannel delegate = new ReadableContentChannel();
        private final Consumer<InputStream> reader;
        private volatile boolean errorReported = false;

        public ForwardingContentChannel(Consumer<InputStream> reader) {
            this.reader = reader;
        }

        /** Write is complete when we have stored the buffer — call completion handler. */
        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            try {
                delegate.write(buf, logException);
                handler.completed();
            }
            catch (Exception e) {
                handler.failed(e);
            }
        }

        /** Close is complete when we have closed the buffer. */
        @Override
        public void close(CompletionHandler handler) {
            try {
                delegate.close(logException);
                if (!errorReported) {
                    reader.accept(new UnsafeContentInputStream(delegate));
                }
                handler.completed();
            }
            catch (Exception e) {
                handler.failed(e);
            }
        }

        @Override
        public void onError(Throwable error) {
            // Jdisc will automatically generate an error response in this scenario
            log.log(FINE, error, () -> "ContentChannel.onError(): " + error.getMessage());
            errorReported = true;
        }
    }

    class DocumentOperationParser {

        private final DocumentTypeManager manager;

        DocumentOperationParser(DocumentmanagerConfig config) {
            this.manager = new DocumentTypeManager(config);
        }

        ParsedDocumentOperation parsePut(InputStream inputStream, String docId) {
            return parse(inputStream, docId, DocumentOperationType.PUT);
        }

        ParsedDocumentOperation parseUpdate(InputStream inputStream, String docId)  {
            return parse(inputStream, docId, DocumentOperationType.UPDATE);
        }

        private ParsedDocumentOperation parse(InputStream inputStream, String docId, DocumentOperationType operation) {
            try {
                return new JsonReader(manager, inputStream, jsonFactory).readSingleDocumentStreaming(operation, docId);
            } catch (IllegalArgumentException e) {
                incrementMetricParseError();
                throw e;
            }
        }

    }

    interface SuccessCallback {
        void onSuccess(Document document, JsonResponse response) throws IOException;
    }

    private static void handle(DocumentPath path,
                               HttpRequest request,
                               ResponseHandler handler,
                               com.yahoo.documentapi.Response response,
                               SuccessCallback callback) {
        try (JsonResponse jsonResponse = JsonResponse.create(path, handler, request)) {
            jsonResponse.writeTrace(response.getTrace());
            if (response.isSuccess())
                callback.onSuccess((response instanceof DocumentResponse) ? ((DocumentResponse) response).getDocument() : null, jsonResponse);
            else {
                jsonResponse.writeMessage(response.getTextMessage());
                switch (response.outcome()) {
                    case NOT_FOUND -> jsonResponse.commit(Response.Status.NOT_FOUND);
                    case CONDITION_FAILED -> jsonResponse.commit(Response.Status.PRECONDITION_FAILED);
                    case INSUFFICIENT_STORAGE -> jsonResponse.commit(Response.Status.INSUFFICIENT_STORAGE);
                    case TIMEOUT -> jsonResponse.commit(Response.Status.GATEWAY_TIMEOUT);
                    case ERROR -> {
                        log.log(FINE, () -> "Exception performing document operation: " + response.getTextMessage());
                        jsonResponse.commit(Status.INTERNAL_SERVER_ERROR);
                    }
                    default -> {
                        log.log(WARNING, "Unexpected document API operation outcome '" + response.outcome() + "' " + response.getTextMessage());
                        jsonResponse.commit(Status.INTERNAL_SERVER_ERROR);
                    }
                }
            }
        }
        catch (Exception e) {
            log.log(FINE, "Failed writing response", e);
        }
    }

    private static void handleFeedOperation(DocumentPath path,
                                            boolean fullyApplied,
                                            ResponseHandler handler,
                                            com.yahoo.documentapi.Response response) {
        handle(path, null, handler, response, (document, jsonResponse) -> jsonResponse.commit(Response.Status.OK, fullyApplied));
    }

    private static double latencyOf(HttpRequest r) { return (System.nanoTime() - r.relativeCreatedAtNanoTime()) / 1e+9d; }

    private void updatePutMetrics(Outcome outcome, double latency, boolean create) {
        if (create && outcome == Outcome.NOT_FOUND) outcome = Outcome.SUCCESS; // >_<
        incrementMetricNumOperations(); incrementMetricNumPuts(); sampleLatency(latency);
        switch (outcome) {
            case SUCCESS -> incrementMetricSucceeded();
            case NOT_FOUND -> incrementMetricNotFound();
            case CONDITION_FAILED -> incrementMetricConditionNotMet();
            case TIMEOUT -> { incrementMetricFailedTimeout(); incrementMetricFailed();}
            case INSUFFICIENT_STORAGE -> { incrementMetricFailedInsufficientStorage(); incrementMetricFailed(); }
            case ERROR -> { incrementMetricFailedUnknown(); incrementMetricFailed(); }
        }
    }

    private void updateUpdateMetrics(Outcome outcome, double latency, boolean create) {
        if (create && outcome == Outcome.NOT_FOUND) outcome = Outcome.SUCCESS; // >_<
        incrementMetricNumOperations(); incrementMetricNumUpdates(); sampleLatency(latency);
        switch (outcome) {
            case SUCCESS -> incrementMetricSucceeded();
            case NOT_FOUND -> incrementMetricNotFound();
            case CONDITION_FAILED -> incrementMetricConditionNotMet();
            case TIMEOUT -> { incrementMetricFailedTimeout(); incrementMetricFailed();}
            case INSUFFICIENT_STORAGE -> { incrementMetricFailedInsufficientStorage(); incrementMetricFailed(); }
            case ERROR -> { incrementMetricFailedUnknown(); incrementMetricFailed(); }
        }
    }

    private void updateRemoveMetrics(Outcome outcome, double latency) {
        incrementMetricNumOperations(); incrementMetricNumRemoves(); sampleLatency(latency);
        switch (outcome) {
            case SUCCESS,NOT_FOUND -> incrementMetricSucceeded();
            case CONDITION_FAILED -> incrementMetricConditionNotMet();
            case TIMEOUT -> { incrementMetricFailedTimeout(); incrementMetricFailed();}
            case INSUFFICIENT_STORAGE -> { incrementMetricFailedInsufficientStorage(); incrementMetricFailed(); }
            case ERROR -> { incrementMetricFailedUnknown(); incrementMetricFailed(); }
        }
    }

    private void sampleLatency(double latency) { setMetric(MetricNames.LATENCY, latency); }
    private void incrementMetricNumOperations() { incrementMetric(MetricNames.NUM_OPERATIONS); }
    private void incrementMetricNumPuts() { incrementMetric(MetricNames.NUM_PUTS); }
    private void incrementMetricNumRemoves() { incrementMetric(MetricNames.NUM_REMOVES); }
    private void incrementMetricNumUpdates() { incrementMetric(MetricNames.NUM_UPDATES); }
    private void incrementMetricFailed() { incrementMetric(MetricNames.FAILED); }
    private void incrementMetricConditionNotMet() { incrementMetric(MetricNames.CONDITION_NOT_MET); }
    private void incrementMetricSucceeded() { incrementMetric(MetricNames.SUCCEEDED); }
    private void incrementMetricNotFound() { incrementMetric(MetricNames.NOT_FOUND); }
    private void incrementMetricParseError() { incrementMetric(MetricNames.PARSE_ERROR); }
    private void incrementMetricFailedUnknown() { incrementMetric(MetricNames.FAILED_UNKNOWN); }
    private void incrementMetricFailedTimeout() { incrementMetric(MetricNames.FAILED_TIMEOUT); }
    private void incrementMetricFailedInsufficientStorage() { incrementMetric(MetricNames.FAILED_INSUFFICIENT_STORAGE); }
    private void incrementMetric(String n) { metric.add(n, 1, null); }
    private void setMetric(String n, Number v) { metric.set(n, v, null); }

    // ------------------------------------------------- Visits ------------------------------------------------

    private VisitorParameters parseGetParameters(HttpRequest request, DocumentPath path, boolean streamed) {
        int wantedDocumentCount = getProperty(request, WANTED_DOCUMENT_COUNT, integerParser)
                .orElse(streamed ? Integer.MAX_VALUE : 1);
        if (wantedDocumentCount <= 0)
            throw new IllegalArgumentException("wantedDocumentCount must be positive");

        Optional<Integer> concurrency = getProperty(request, CONCURRENCY, integerParser);
        concurrency.ifPresent(value -> {
            if (value <= 0)
                throw new IllegalArgumentException("concurrency must be positive");
        });

        Optional<String> cluster = getProperty(request, CLUSTER);
        if (cluster.isEmpty() && path.documentType().isEmpty())
            throw new IllegalArgumentException("Must set 'cluster' parameter to a valid content cluster id when visiting at a root /document/v1/ level");

        VisitorParameters parameters = parseCommonParameters(request, path, cluster);
        // TODO can the else-case be safely reduced to always be DocumentOnly.NAME?
        parameters.setFieldSet(getProperty(request, FIELD_SET).orElse(path.documentType().map(type -> type + ":[document]").orElse(DocumentOnly.NAME)));
        parameters.setMaxTotalHits(wantedDocumentCount);
        parameters.visitInconsistentBuckets(true);
        getProperty(request, INCLUDE_REMOVES, booleanParser).ifPresent(parameters::setVisitRemoves);
        if (streamed) {
            StaticThrottlePolicy throttlePolicy = new DynamicThrottlePolicy().setMinWindowSize(1).setWindowSizeIncrement(1);
            concurrency.ifPresent(throttlePolicy::setMaxPendingCount);
            parameters.setThrottlePolicy(throttlePolicy);
            parameters.setTimeoutMs(visitTimeout(request)); // Ensure visitor eventually completes.
        }
        else {
            parameters.setThrottlePolicy(new StaticThrottlePolicy().setMaxPendingCount(Math.min(100, concurrency.orElse(1))));
            parameters.setSessionTimeoutMs(visitTimeout(request));
        }
        return parameters;
    }

    private VisitorParameters parseParameters(HttpRequest request, DocumentPath path) {
        disallow(request, CONCURRENCY, FIELD_SET, ROUTE, WANTED_DOCUMENT_COUNT);
        requireProperty(request, SELECTION);
        VisitorParameters parameters = parseCommonParameters(request, path, Optional.of(requireProperty(request, CLUSTER)));
        parameters.setThrottlePolicy(new DynamicThrottlePolicy().setMinWindowSize(1).setWindowSizeIncrement(1));
        long timeChunk = getProperty(request, TIME_CHUNK, timeoutMillisParser).orElse(60_000L);
        parameters.setSessionTimeoutMs(Math.min(timeChunk, visitTimeout(request)));
        return parameters;
    }

    private long visitTimeout(HttpRequest request) {
        return Math.max(1,
                        Math.max(doomMillis(request) - clock.millis() - visitTimeout.toMillis(),
                                 9 * (doomMillis(request) - clock.millis()) / 10 - handlerTimeout.toMillis()));
    }

    private VisitorParameters parseCommonParameters(HttpRequest request, DocumentPath path, Optional<String> cluster) {
        VisitorParameters parameters = new VisitorParameters(Stream.of(getProperty(request, SELECTION),
                                                                       path.documentType(),
                                                                       path.namespace().map(value -> "id.namespace=='" + value + "'"),
                                                                       path.group().map(Group::selection))
                                                                   .flatMap(Optional::stream)
                                                                   .reduce(new StringJoiner(") and (", "(", ")").setEmptyValue(""), // don't mind the lonely chicken to the right
                                                                           StringJoiner::add,
                                                                           StringJoiner::merge)
                                                                   .toString());

        getProperty(request, TRACELEVEL, integerParser).ifPresent(parameters::setTraceLevel);

        getProperty(request, CONTINUATION, ProgressToken::fromSerializedString).ifPresent(parameters::setResumeToken);
        parameters.setPriority(DocumentProtocol.Priority.NORMAL_4);

        getProperty(request, FROM_TIMESTAMP, unsignedLongParser).ifPresent(parameters::setFromTimestamp);
        getProperty(request, TO_TIMESTAMP, unsignedLongParser).ifPresent(ts -> {
            parameters.setToTimestamp(ts);
            if (Long.compareUnsigned(parameters.getFromTimestamp(), parameters.getToTimestamp()) > 0) {
                throw new IllegalArgumentException("toTimestamp must be greater than, or equal to, fromTimestamp");
            }
        });

        StorageCluster storageCluster = resolveCluster(cluster, clusters);
        parameters.setRoute(storageCluster.name());
        parameters.setBucketSpace(resolveBucket(storageCluster,
                                                path.documentType(),
                                                List.of(FixedBucketSpaces.defaultSpace(), FixedBucketSpaces.globalSpace()),
                                                getProperty(request, BUCKET_SPACE)));

        Optional<Integer> slices = getProperty(request, SLICES, integerParser);
        Optional<Integer> sliceId = getProperty(request, SLICE_ID, integerParser);
        if (slices.isPresent() && sliceId.isPresent())
            parameters.slice(slices.get(), sliceId.get());
        else if (slices.isPresent() != sliceId.isPresent())
            throw new IllegalArgumentException("None or both of '" + SLICES + "' and '" + SLICE_ID + "' must be set");

        return parameters;
    }

    private interface VisitCallback {
        /** Called at the start of response rendering. */
        default void onStart(JsonResponse response, boolean fullyApplied) throws IOException { }

        /** Called for every document or removal received from backend visitors—must call the ack for these to proceed. */
        default void onDocument(JsonResponse response, Document document, DocumentId removeId, long persistedTimestamp, Runnable ack, Consumer<String> onError) { }

        /** Called at the end of response rendering, before generic status data is written. Called from a dedicated thread pool. */
        default void onEnd(JsonResponse response) throws IOException { }
    }

    @FunctionalInterface
    private interface VisitProcessingCallback {
        Result apply(DocumentId id, long persistedTimestamp, DocumentOperationParameters params);
    }

    private void visitAndDelete(HttpRequest request, VisitorParameters parameters, ResponseHandler handler,
                                TestAndSetCondition condition, String route) {
        visitAndProcess(request, parameters, true, handler, route, (id, timestamp, operationParameters) -> {
            DocumentRemove remove = new DocumentRemove(id);
            // If the backend provided a persisted timestamp, we set a condition that specifies _both_ the
            // original selection and the timestamp. If the backend supports timestamp-predicated TaS operations,
            // it will ignore the selection entirely and only look at the timestamp. If it does not, it will fall
            // back to evaluating the selection, which preserves legacy behavior.
            if (timestamp != 0) {
                remove.setCondition(TestAndSetCondition.ofRequiredTimestampWithSelectionFallback(
                        timestamp, condition.getSelection()));
            } else {
                remove.setCondition(condition);
            }
            return asyncSession.remove(remove, operationParameters);
        });
    }

    private void visitAndUpdate(HttpRequest request, VisitorParameters parameters, boolean fullyApplied,
                                ResponseHandler handler, DocumentUpdate protoUpdate, String route) {
        visitAndProcess(request, parameters, fullyApplied, handler, route, (id, timestamp, operationParameters) -> {
            DocumentUpdate update = new DocumentUpdate(protoUpdate);
            // See `visitAndDelete()` for rationale for sending down a timestamp _and_ the original condition.
            if (timestamp != 0) {
                update.setCondition(TestAndSetCondition.ofRequiredTimestampWithSelectionFallback(
                        timestamp, protoUpdate.getCondition().getSelection()));
            } // else: use condition already set from protoUpdate
            update.setId(id);
            return asyncSession.update(update, operationParameters);
        });
    }

    private void visitAndProcess(HttpRequest request, VisitorParameters parameters, boolean fullyApplied,
                                 ResponseHandler handler,
                                 String route, VisitProcessingCallback operation) {
        visit(request, parameters, false, fullyApplied, handler, new VisitCallback() {
            @Override public void onDocument(JsonResponse response, Document document, DocumentId removeId,
                                             long persistedTimestamp, Runnable ack, Consumer<String> onError) {
                DocumentOperationParameters operationParameters = parameters().withRoute(route)
                        .withResponseHandler(operationResponse -> {
                            outstanding.decrementAndGet();
                            switch (operationResponse.outcome()) {
                                case SUCCESS:
                                case NOT_FOUND:
                                case CONDITION_FAILED:
                                    break; // This is all OK — the latter two are due to mitigating races.
                                case ERROR:
                                case INSUFFICIENT_STORAGE:
                                case TIMEOUT:
                                    onError.accept(operationResponse.getTextMessage());
                                    break;
                                default:
                                    onError.accept("Unexpected response " + operationResponse);
                            }
                        });
                visitOperations.offer(() -> {
                    Result result = operation.apply(document.getId(), persistedTimestamp, operationParameters);
                    if (result.type() == Result.ResultType.TRANSIENT_ERROR)
                        return false;

                    if (result.type() == Result.ResultType.FATAL_ERROR)
                        onError.accept(result.error().getMessage());
                    else
                        outstanding.incrementAndGet();

                    ack.run();
                    return true;
                });
                dispatchFirstVisit();
            }
        });
    }

    private void visitAndWrite(HttpRequest request, VisitorParameters parameters, ResponseHandler handler, boolean streamed) {
        visit(request, parameters, streamed, true, handler, new VisitCallback() {
            @Override public void onStart(JsonResponse response, boolean fullyApplied) throws IOException {
                if (streamed)
                    response.commit(Response.Status.OK, fullyApplied);

                response.writeDocumentsArrayStart();
            }
            @Override public void onDocument(JsonResponse response, Document document, DocumentId removeId,
                                             long persistedTimestamp, Runnable ack, Consumer<String> onError) {
                try {
                    if (streamed) {
                        CompletionHandler completion = new CompletionHandler() {
                            @Override public void completed() { ack.run(); }
                            @Override public void failed(Throwable t) {
                                ack.run();
                                onError.accept(t.getMessage());
                            }
                        };
                        if (document != null) response.writeDocumentValue(document, completion);
                        else response.writeDocumentRemoval(removeId, completion);
                    }
                    else {
                        if (document != null) response.writeDocumentValue(document, null);
                        else response.writeDocumentRemoval(removeId, null);
                        ack.run();
                    }
                }
                catch (Exception e) {
                    onError.accept(e.getMessage());
                }
            }
            @Override public void onEnd(JsonResponse response) throws IOException {
                response.writeArrayEnd();
            }
        });
    }

    private void visitWithRemote(HttpRequest request, VisitorParameters parameters, ResponseHandler handler) {
        visit(request, parameters, false, true, handler, new VisitCallback() { });
    }

    @SuppressWarnings("fallthrough")
    private void visit(HttpRequest request, VisitorParameters parameters, boolean streaming, boolean fullyApplied, ResponseHandler handler, VisitCallback callback) {
        try {
            JsonResponse response = JsonResponse.create(request, handler);
            Phaser phaser = new Phaser(2); // Synchronize this thread (dispatch) with the visitor callback thread.
            AtomicReference<String> error = new AtomicReference<>(); // Set if error occurs during processing of visited documents.
            callback.onStart(response, fullyApplied);
            final AtomicLong locallyReceivedDocCount = new AtomicLong(0);
            VisitorControlHandler controller = new VisitorControlHandler() {
                final ScheduledFuture<?> abort = streaming ? visitDispatcher.schedule(this::abort, visitTimeout(request), MILLISECONDS) : null;
                final AtomicReference<VisitorSession> session = new AtomicReference<>();
                @Override public void setSession(VisitorControlSession session) { // Workaround for broken session API ಠ_ಠ
                    super.setSession(session);
                    if (session instanceof VisitorSession visitorSession) this.session.set(visitorSession);
                }
                @Override public void onDone(CompletionCode code, String message) {
                    super.onDone(code, message);
                    loggingException(() -> {
                        try (response) {
                            callback.onEnd(response);

                            // Locally tracked document count is only correct if we have a local data handler.
                            // Otherwise, we have to report the statistics received transitively from the content nodes.
                            long statsDocCount = (getVisitorStatistics() != null ? getVisitorStatistics().getDocumentsVisited() : 0);
                            response.writeDocumentCount(parameters.getLocalDataHandler() != null ? locallyReceivedDocCount.get() : statsDocCount);

                            if (session.get() != null)
                                response.writeTrace(session.get().getTrace());

                            int status = Status.INTERNAL_SERVER_ERROR;
                            switch (code) {
                                case TIMEOUT: // Intentional fallthrough.
                                case ABORTED:
                                    if (error.get() == null && ! hasVisitedAnyBuckets() && parameters.getVisitInconsistentBuckets()) {
                                        response.writeMessage("No buckets visited within timeout of " +
                                                              parameters.getSessionTimeoutMs() + "ms (request timeout -5s)");
                                        status = Response.Status.GATEWAY_TIMEOUT;
                                        break;
                                    }
                                case SUCCESS:
                                    if (error.get() == null) {
                                        ProgressToken progress = getProgress() != null ? getProgress() : parameters.getResumeToken();
                                        if (progress != null && ! progress.isFinished())
                                            response.writeContinuation(progress.serializeToString());

                                        status = Response.Status.OK;
                                        break;
                                    }
                                default:
                                    response.writeMessage(error.get() != null ? error.get() : message != null ? message : "Visiting failed");
                            }
                            if ( ! streaming)
                                response.commit(status, fullyApplied);
                        }
                    });
                    if (abort != null) abort.cancel(false); // Avoid keeping scheduled future alive if this completes in any other fashion.
                    visitDispatcher.execute(() -> {
                        phaser.arriveAndAwaitAdvance(); // We may get here while dispatching thread is still putting us in the map.
                        visits.remove(this).destroy();
                    });
                }
            };
            if (parameters.getRemoteDataHandler() == null) {
                parameters.setLocalDataHandler(new VisitorDataHandler() {
                    @Override public void onMessage(Message m, AckToken token) {
                        Document document = null;
                        DocumentId removeId = null;
                        long persistedTimestamp = 0;
                        if (m instanceof PutDocumentMessage put) {
                            document = put.getDocumentPut().getDocument();
                            persistedTimestamp = put.getPersistedTimestamp();
                        } else if (parameters.visitRemoves() && m instanceof RemoveDocumentMessage remove) {
                            removeId = remove.getDocumentId();
                            persistedTimestamp = remove.getPersistedTimestamp();
                        } else {
                            throw new UnsupportedOperationException("Got unsupported message type: " + m.getClass().getName());
                        }
                        locallyReceivedDocCount.getAndAdd(1);
                        callback.onDocument(response,
                                            document,
                                            removeId,
                                            persistedTimestamp,
                                            () -> ack(token),
                                            errorMessage -> {
                                                error.set(errorMessage);
                                                controller.abort();
                                            });
                    }
                });
            }
            parameters.setControlHandler(controller);
            visits.put(controller, access.createVisitorSession(parameters));
            phaser.arriveAndDeregister();
        }
        catch (ParseException e) {
            badRequest(request, new IllegalArgumentException(e), handler);
        }
        catch (IOException e) {
            log.log(FINE, "Failed writing response", e);
        }
    }

    // ------------------------------------------------ Helpers ------------------------------------------------

    private static long doomMillis(HttpRequest request) {
        long createdAtMillis = request.creationTime(MILLISECONDS);
        long requestTimeoutMillis = getProperty(request, TIMEOUT, timeoutMillisParser).orElse(defaultTimeout.toMillis());
        return createdAtMillis + requestTimeoutMillis;
    }

    private static String requireProperty(HttpRequest request, String name) {
        return getProperty(request, name)
                .orElseThrow(() -> new IllegalArgumentException("Must specify '" + name + "' at '" + request.getUri().getRawPath() + "'"));
    }

    /** Returns the last property with the given name, if present, or throws if this is empty or blank. */
    private static Optional<String> getProperty(HttpRequest request, String name) {
        if ( ! request.parameters().containsKey(name))
            return Optional.empty();

        List<String> values = request.parameters().get(name);
        String value;
        if (values == null || values.isEmpty() || (value = values.get(values.size() - 1)) == null || value.isEmpty())
            throw new IllegalArgumentException("Expected non-empty value for request property '" + name + "'");

        return Optional.of(value);
    }

    private static <T> Optional<T> getProperty(HttpRequest request, String name, Parser<T> parser) {
        return getProperty(request, name).map(parser::parse);
    }

    private static void disallow(HttpRequest request, String... properties) {
        for (String property : properties)
            if (request.parameters().containsKey(property))
                throw new IllegalArgumentException("May not specify '" + property + "' at '" + request.getUri().getRawPath() + "'");
    }

    @FunctionalInterface
    interface Parser<T> extends Function<String, T> {
        default T parse(String value) {
            try {
                return apply(value);
            }
            catch (RuntimeException e) {
                throw new IllegalArgumentException("Failed parsing '" + value + "': " + Exceptions.toMessageString(e));
            }
        }
    }

    private class MeasuringResponseHandler implements ResponseHandler {

        private final ResponseHandler delegate;
        private final com.yahoo.documentapi.metrics.DocumentOperationType type;
        private final Instant start;
        private final HttpRequest request;

        private MeasuringResponseHandler(HttpRequest request,
                                         ResponseHandler delegate,
                                         com.yahoo.documentapi.metrics.DocumentOperationType type,
                                         Instant start) {
            this.request = request;
            this.delegate = delegate;
            this.type = type;
            this.start = start;
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            switch (response.getStatus()) {
                case 200 -> report(DocumentOperationStatus.OK);
                case 400 -> report(DocumentOperationStatus.REQUEST_ERROR);
                case 404 -> report(DocumentOperationStatus.NOT_FOUND);
                case 412 -> report(DocumentOperationStatus.CONDITION_FAILED);
                case 429 -> report(DocumentOperationStatus.TOO_MANY_REQUESTS);
                case 500,503,504,507 -> report(DocumentOperationStatus.SERVER_ERROR);
                default -> throw new IllegalStateException("Unexpected status code '%s'".formatted(response.getStatus()));
            }
            metrics.reportHttpRequest(clientVersion());
            return delegate.handleResponse(response);
        }

        private void report(DocumentOperationStatus... status) { metrics.report(type, start, status); }

        private String clientVersion() {
            return Optional.ofNullable(request.headers().get(Headers.CLIENT_VERSION))
                    .filter(l -> !l.isEmpty()).map(l -> l.get(0))
                    .orElse("unknown");
        }

    }

    static class StorageCluster {

        private final String name;
        private final Map<String, String> documentBuckets;

        StorageCluster(String name, Map<String, String> documentBuckets) {
            this.name = requireNonNull(name);
            this.documentBuckets = Map.copyOf(documentBuckets);
        }

        String name() { return name; }
        Optional<String> bucketOf(String documentType) { return Optional.ofNullable(documentBuckets.get(documentType)); }

    }

    private static Map<String, StorageCluster> parseClusters(ClusterListConfig clusters, AllClustersBucketSpacesConfig buckets) {
        return clusters.storage().stream()
                       .collect(toUnmodifiableMap(ClusterListConfig.Storage::name,
                                                  storage -> new StorageCluster(storage.name(),
                                                                                buckets.cluster(storage.name())
                                                                                       .documentType().entrySet().stream()
                                                                                       .collect(toMap(Map.Entry::getKey,
                                                                                                      entry -> entry.getValue().bucketSpace())))));
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

    static String resolveBucket(StorageCluster cluster, Optional<String> documentType,
                                List<String> bucketSpaces, Optional<String> bucketSpace) {
        return documentType.map(type -> cluster.bucketOf(type)
                                               .orElseThrow(() -> new IllegalArgumentException("There is no document type '" + type + "' in cluster '" + cluster.name() +
                                                                                               "', only '" + String.join("', '", cluster.documentBuckets.keySet()) + "'")))
                           .or(() -> bucketSpace.map(space -> {
                               if ( ! bucketSpaces.contains(space))
                                   throw new IllegalArgumentException("Bucket space '" + space + "' is not a known bucket space; expected one of " +
                                                                      String.join(", ", bucketSpaces));
                               return space;
                           }))
                           .orElse(FixedBucketSpaces.defaultSpace());
    }

    private static class DocumentPath {

        private final Path path;
        private final String rawPath;
        private final Optional<Group> group;

        DocumentPath(Path path, String rawPath) {
            this.path = requireNonNull(path);
            this.rawPath = requireNonNull(rawPath);
            this.group = Optional.ofNullable(path.get("number")).map(unsignedLongParser::parse).map(Group::of)
                                 .or(() -> Optional.ofNullable(path.get("group")).map(Group::of));
        }

        DocumentId id() {
            return new DocumentId("id:" + requireNonNull(path.get("namespace")) +
                                  ":" + requireNonNull(path.get("documentType")) +
                                  ":" + group.map(Group::docIdPart).orElse("") +
                                  ":" + String.join("/", requireNonNull(path.getRest()).segments())); // :'(
        }

        String rawPath() { return rawPath; }
        Optional<String> documentType() { return Optional.ofNullable(path.get("documentType")); }
        Optional<String> namespace() { return Optional.ofNullable(path.get("namespace")); }
        Optional<Group> group() { return group; }

    }

    static class Group {

        private final String docIdPart;
        private final String selection;

        private Group(String docIdPart, String selection) {
            this.docIdPart = docIdPart;
            this.selection = selection;
        }

        public static Group of(long value) {
            String stringValue = Long.toUnsignedString(value);
            return new Group("n=" + stringValue, "id.user==" + stringValue);
        }

        public static Group of(String value) {
            Text.validateTextString(value)
                .ifPresent(codePoint -> { throw new IllegalArgumentException(String.format("Illegal code point U%04X in group", codePoint)); });

            return new Group("g=" + value, "id.group=='" + value.replaceAll("'", "\\\\'") + "'");
        }

        public String docIdPart() { return docIdPart; }
        public String selection() { return selection; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Group group = (Group) o;
            return docIdPart.equals(group.docIdPart) &&
                   selection.equals(group.selection);
        }

        @Override
        public int hashCode() {
            return Objects.hash(docIdPart, selection);
        }

        @Override
        public String toString() {
            return "Group{" +
                   "docIdPart='" + docIdPart + '\'' +
                   ", selection='" + selection + '\'' +
                   '}';
        }

    }

}
