// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.inject.Inject;
import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.container.core.HandlerMetricContextUtil;
import com.yahoo.container.core.documentapi.VespaDocumentAccess;
import com.yahoo.container.jdisc.ContentChannelOutputStream;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.FixedBucketSpaces;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.fieldset.AllFields;
import com.yahoo.document.fieldset.DocIdOnly;
import com.yahoo.document.idstring.IdIdString;
import com.yahoo.document.json.DocumentOperationType;
import com.yahoo.document.json.JsonReader;
import com.yahoo.document.json.JsonWriter;
import com.yahoo.document.restapi.DocumentOperationExecutorConfig;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.AckToken;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentOperationParameters;
import com.yahoo.documentapi.DocumentResponse;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.Result;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorDataHandler;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.PutDocumentMessage;
import com.yahoo.documentapi.metrics.DocumentApiMetrics;
import com.yahoo.documentapi.metrics.DocumentOperationStatus;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
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
import com.yahoo.text.Text;
import com.yahoo.vespa.config.content.AllClustersBucketSpacesConfig;
import com.yahoo.yolean.Exceptions;
import com.yahoo.yolean.Exceptions.RunnableThrowingIOException;

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
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
public class DocumentV1ApiHandler extends AbstractRequestHandler {

    private static final Duration defaultTimeout = Duration.ofSeconds(175);

    private static final Logger log = Logger.getLogger(DocumentV1ApiHandler.class.getName());
    private static final Parser<Integer> integerParser = Integer::parseInt;
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

    private static final JsonFactory jsonFactory = new JsonFactory();

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

    private final Clock clock;
    private final Metric metric;
    private final DocumentApiMetrics metrics;
    private final DocumentOperationParser parser;
    private final long maxThrottled;
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
        this(Clock.systemUTC(), metric, metricReceiver, documentAccess,
             documentManagerConfig, executorConfig, clusterListConfig, bucketSpacesConfig);
    }

    DocumentV1ApiHandler(Clock clock, Metric metric, MetricReceiver metricReceiver, DocumentAccess access,
                         DocumentmanagerConfig documentmanagerConfig, DocumentOperationExecutorConfig executorConfig,
                         ClusterListConfig clusterListConfig, AllClustersBucketSpacesConfig bucketSpacesConfig) {
        this.clock = clock;
        this.parser = new DocumentOperationParser(documentmanagerConfig);
        this.metric = metric;
        this.metrics = new DocumentApiMetrics(metricReceiver, "documentV1");
        this.maxThrottled = executorConfig.maxThrottled();
        this.access = access;
        this.asyncSession = access.createAsyncSession(new AsyncParameters());
        this.clusters = parseClusters(clusterListConfig, bucketSpacesConfig);
        this.operations = new ConcurrentLinkedDeque<>();
        this.dispatcher.scheduleWithFixedDelay(this::dispatchEnqueued,
                                               executorConfig.resendDelayMillis(),
                                               executorConfig.resendDelayMillis(),
                                               TimeUnit.MILLISECONDS);
        this.visitDispatcher.scheduleWithFixedDelay(this::dispatchVisitEnqueued,
                                                    executorConfig.resendDelayMillis(),
                                                    executorConfig.resendDelayMillis(),
                                                    TimeUnit.MILLISECONDS);
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
            request.setTimeout(getProperty(request, TIMEOUT, timeoutMillisParser)
                                       .orElse(defaultTimeout.toMillis()),
                               TimeUnit.MILLISECONDS);

            Path requestPath = new Path(request.getUri());
            for (String path : handlers.keySet())
                if (requestPath.matches(path)) {
                    Map<Method, Handler> methods = handlers.get(path);
                    if (methods.containsKey(request.getMethod()))
                        return methods.get(request.getMethod()).handle(request, new DocumentPath(requestPath), responseHandler);

                    if (request.getMethod() == OPTIONS)
                        options(methods.keySet(), responseHandler);

                    methodNotAllowed(request, methods.keySet(), responseHandler);
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
        timeout((HttpRequest) request, "Request timeout after " + request.getTimeout(TimeUnit.MILLISECONDS) + "ms", responseHandler);
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
            log.log(WARNING, "Failed to empty visitor operations queue before shutdown timeout — " + operations.size() + " operations left");

        try {
            while (outstanding.get() > 0 && clock.instant().isBefore(doom))
                Thread.sleep(Math.max(1, Duration.between(clock.instant(), doom).toMillis()));

            if ( ! dispatcher.awaitTermination(Duration.between(clock.instant(), doom).toMillis(), TimeUnit.MILLISECONDS))
                dispatcher.shutdownNow();

            if ( ! visitDispatcher.awaitTermination(Duration.between(clock.instant(), doom).toMillis(), TimeUnit.MILLISECONDS))
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
        enqueueAndDispatch(request, handler, () -> {
            VisitorParameters parameters = parseGetParameters(request, path);
            return () -> {
                visitAndWrite(request, parameters, handler);
                return true; // VisitorSession has its own throttle handling.
            };
        });
        return ignoredContent;
    }

    private ContentChannel postDocuments(HttpRequest request, DocumentPath path, ResponseHandler handler) {
        enqueueAndDispatch(request, handler, () -> {
            StorageCluster destination = resolveCluster(Optional.of(requireProperty(request, DESTINATION_CLUSTER)), clusters);
            VisitorParameters parameters = parseParameters(request, path);
            parameters.setRemoteDataHandler("[Content:cluster=" + destination.name() + "]"); // Bypass indexing.
            parameters.setFieldSet(AllFields.NAME);
            return () -> {
                visitWithRemote(request, parameters, handler);
                return true; // VisitorSession has its own throttle handling.
            };
        });
        return ignoredContent;
    }

    private ContentChannel putDocuments(HttpRequest request, DocumentPath path, ResponseHandler handler) {
        return new ForwardingContentChannel(in -> {
            enqueueAndDispatch(request, handler, () -> {
                StorageCluster cluster = resolveCluster(Optional.of(requireProperty(request, CLUSTER)), clusters);
                VisitorParameters parameters = parseParameters(request, path);
                parameters.setFieldSet(DocIdOnly.NAME);
                String type = path.documentType().orElseThrow(() -> new IllegalStateException("Document type must be specified for mass updates"));
                IdIdString dummyId = new IdIdString("dummy", type, "", "");
                DocumentUpdate update = parser.parseUpdate(in, dummyId.toString());
                update.setCondition(new TestAndSetCondition(requireProperty(request, SELECTION)));
                return () -> {
                    visitAndUpdate(request, parameters, handler, update, cluster.name());
                    return true; // VisitorSession has its own throttle handling.
                };
            });
        });
    }

    private ContentChannel deleteDocuments(HttpRequest request, DocumentPath path, ResponseHandler handler) {
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

    private ContentChannel getDocument(HttpRequest request, DocumentPath path, ResponseHandler handler) {
        enqueueAndDispatch(request, handler, () -> {
            DocumentOperationParameters rawParameters = parametersFromRequest(request, CLUSTER, FIELD_SET);
            if (rawParameters.fieldSet().isEmpty())
                rawParameters = rawParameters.withFieldSet(path.documentType().orElseThrow() + ":[document]");
            DocumentOperationParameters parameters = rawParameters.withResponseHandler(response -> {
                outstanding.decrementAndGet();
                handle(path, handler, response, (document, jsonResponse) -> {
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
        ResponseHandler handler = new MeasuringResponseHandler(rawHandler, com.yahoo.documentapi.metrics.DocumentOperationType.PUT, clock.instant());
        return new ForwardingContentChannel(in -> {
            enqueueAndDispatch(request, handler, () -> {
                DocumentPut put = parser.parsePut(in, path.id().toString());
                getProperty(request, CONDITION).map(TestAndSetCondition::new).ifPresent(put::setCondition);
                DocumentOperationParameters parameters = parametersFromRequest(request, ROUTE)
                        .withResponseHandler(response -> {
                            outstanding.decrementAndGet();
                            handle(path, handler, response);
                        });
                return () -> dispatchOperation(() -> asyncSession.put(put, parameters));
            });
        });
    }

    private ContentChannel putDocument(HttpRequest request, DocumentPath path, ResponseHandler rawHandler) {
        ResponseHandler handler = new MeasuringResponseHandler(rawHandler, com.yahoo.documentapi.metrics.DocumentOperationType.UPDATE, clock.instant());
        return new ForwardingContentChannel(in -> {
            enqueueAndDispatch(request, handler, () -> {
                DocumentUpdate update = parser.parseUpdate(in, path.id().toString());
                getProperty(request, CONDITION).map(TestAndSetCondition::new).ifPresent(update::setCondition);
                getProperty(request, CREATE, booleanParser).ifPresent(update::setCreateIfNonExistent);
                DocumentOperationParameters parameters = parametersFromRequest(request, ROUTE)
                        .withResponseHandler(response -> {
                            outstanding.decrementAndGet();
                            handle(path, handler, response);
                        });
                return () -> dispatchOperation(() -> asyncSession.update(update, parameters));
            });
        });
    }

    private ContentChannel deleteDocument(HttpRequest request, DocumentPath path, ResponseHandler rawHandler) {
        ResponseHandler handler = new MeasuringResponseHandler(rawHandler, com.yahoo.documentapi.metrics.DocumentOperationType.REMOVE, clock.instant());
        enqueueAndDispatch(request, handler, () -> {
            DocumentRemove remove = new DocumentRemove(path.id());
            getProperty(request, CONDITION).map(TestAndSetCondition::new).ifPresent(remove::setCondition);
            DocumentOperationParameters parameters = parametersFromRequest(request, ROUTE)
                    .withResponseHandler(response -> {
                        outstanding.decrementAndGet();
                        handle(path, handler, response);
                    });
            return () -> dispatchOperation(() -> asyncSession.remove(remove, parameters));
        });
        return ignoredContent;
    }

    private DocumentOperationParameters parametersFromRequest(HttpRequest request, String... names) {
        DocumentOperationParameters parameters = getProperty(request, TRACELEVEL, integerParser).map(parameters()::withTraceLevel)
                                                                                                .orElse(parameters());
        for (String name : names) switch (name) {
            case CLUSTER:
                parameters = getProperty(request, CLUSTER).map(cluster -> resolveCluster(Optional.of(cluster), clusters).name())
                                                          .map(parameters::withRoute)
                                                          .orElse(parameters);
                break;
            case FIELD_SET:
                parameters = getProperty(request, FIELD_SET).map(parameters::withFieldSet)
                                                            .orElse(parameters);
                break;
            case ROUTE:
                parameters = getProperty(request, ROUTE).map(parameters::withRoute)
                                                        .orElse(parameters);
                break;
            default:
                throw new IllegalArgumentException("Unrecognized document operation parameter name '" + name + "'");
        }
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
    void dispatchVisitEnqueued() {
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

    /**
     * Enqueues the given request and operation, or responds with "overload" if the queue is full,
     * and then attempts to dispatch an enqueued operation from the head of the queue.
     */
    private void enqueueAndDispatch(HttpRequest request, ResponseHandler handler, Supplier<BooleanSupplier> operationParser) {
        if (enqueued.incrementAndGet() > maxThrottled) {
            enqueued.decrementAndGet();
            overload(request, "Rejecting execution due to overload: " + maxThrottled + " requests already enqueued", handler);
            return;
        }
        operations.offer(new Operation(request, handler) {
            @Override BooleanSupplier parse() { return operationParser.get(); }
        });
        dispatchFirst();
    }


    // ------------------------------------------------ Responses ------------------------------------------------

    /** Class for writing and returning JSON responses to document operations in a thread safe manner. */
    private static class JsonResponse implements AutoCloseable {

        private final BufferedContentChannel buffer = new BufferedContentChannel();
        private final OutputStream out = new ContentChannelOutputStream(buffer);
        private final JsonGenerator json = jsonFactory.createGenerator(out);
        private final ResponseHandler handler;
        private ContentChannel channel;

        private JsonResponse(ResponseHandler handler) throws IOException {
            this.handler = handler;
            json.writeStartObject();
        }

        /** Creates a new JsonResponse with path and id fields written. */
        static JsonResponse create(DocumentPath path, ResponseHandler handler) throws IOException {
            JsonResponse response = new JsonResponse(handler);
            response.writePathId(path.rawPath());
            response.writeDocId(path.id());
            return response;
        }

        /** Creates a new JsonResponse with path field written. */
        static JsonResponse create(HttpRequest request, ResponseHandler handler) throws IOException {
            JsonResponse response = new JsonResponse(handler);
            response.writePathId(request.getUri().getRawPath());
            return response;
        }

        /** Creates a new JsonResponse with path and message fields written. */
        static JsonResponse create(HttpRequest request, String message, ResponseHandler handler) throws IOException {
            JsonResponse response = new JsonResponse(handler);
            response.writePathId(request.getUri().getRawPath());
            response.writeMessage(message);
            return response;
        }

        /** Commits a response with the given status code and some default headers, and writes whatever content is buffered. */
        synchronized void commit(int status) throws IOException {
            Response response = new Response(status);
            response.headers().addAll(Map.of("Content-Type", List.of("application/json; charset=UTF-8")));
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

        synchronized void writeSingleDocument(Document document) throws IOException {
            new JsonWriter(json).writeFields(document);
        }

        synchronized void writeDocumentsArrayStart() throws IOException {
            json.writeArrayFieldStart("documents");
        }

        synchronized void writeDocumentValue(Document document) {
            new JsonWriter(json).write(document);
        }

        synchronized void writeArrayEnd() throws IOException {
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
            log.log(WARNING, "Uncaught exception handling request " + request.getMethod() + " " + request.getUri().getRawPath() + ":", t);
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

    private static abstract class Operation {

        private final Lock lock = new ReentrantLock();
        private final HttpRequest request;
        private final ResponseHandler handler;
        private BooleanSupplier operation;

        Operation(HttpRequest request, ResponseHandler handler) {
            this.request = request;
            this.handler = handler;
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
                if (operation == null)
                    operation = parse();

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

        abstract BooleanSupplier parse();

    }

    /** Attempts to send the given document operation, returning false if this needs to be retried. */
    private boolean dispatchOperation(Supplier<Result> documentOperation) {
        Result result = documentOperation.get();
        if (result.type() == Result.ResultType.TRANSIENT_ERROR)
            return false;

        if (result.type() == Result.ResultType.FATAL_ERROR)
            throw new RuntimeException(result.getError());

        outstanding.incrementAndGet();
        return true;
    }

    /** Readable content channel which forwards data to a reader when closed. */
    static class ForwardingContentChannel implements ContentChannel {

        private final ReadableContentChannel delegate = new ReadableContentChannel();
        private final Consumer<InputStream> reader;

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
                reader.accept(new UnsafeContentInputStream(delegate));
                handler.completed();
            }
            catch (Exception e) {
                handler.failed(e);
            }
        }

    }

    static class DocumentOperationParser {

        private final DocumentTypeManager manager;

        DocumentOperationParser(DocumentmanagerConfig config) {
            this.manager = new DocumentTypeManager(config);
        }

        DocumentPut parsePut(InputStream inputStream, String docId) {
            return (DocumentPut) parse(inputStream, docId, DocumentOperationType.PUT);
        }

        DocumentUpdate parseUpdate(InputStream inputStream, String docId)  {
            return (DocumentUpdate) parse(inputStream, docId, DocumentOperationType.UPDATE);
        }

        private DocumentOperation parse(InputStream inputStream, String docId, DocumentOperationType operation)  {
            return new JsonReader(manager, inputStream, jsonFactory).readSingleDocument(operation, docId);
        }

    }

    interface SuccessCallback {
        void onSuccess(Document document, JsonResponse response) throws IOException;
    }

    private static void handle(DocumentPath path, ResponseHandler handler, com.yahoo.documentapi.Response response, SuccessCallback callback) {
        try (JsonResponse jsonResponse = JsonResponse.create(path, handler)) {
            jsonResponse.writeTrace(response.getTrace());
            if (response.isSuccess())
                callback.onSuccess((response instanceof DocumentResponse) ? ((DocumentResponse) response).getDocument() : null, jsonResponse);
            else {
                jsonResponse.writeMessage(response.getTextMessage());
                switch (response.outcome()) {
                    case NOT_FOUND:
                        jsonResponse.commit(Response.Status.NOT_FOUND);
                        break;
                    case CONDITION_FAILED:
                        jsonResponse.commit(Response.Status.PRECONDITION_FAILED);
                        break;
                    case INSUFFICIENT_STORAGE:
                        jsonResponse.commit(Response.Status.INSUFFICIENT_STORAGE);
                        break;
                    default:
                        log.log(WARNING, "Unexpected document API operation outcome '" + response.outcome() + "'");
                    case ERROR:
                        log.log(FINE, () -> "Exception performing document operation: " + response.getTextMessage());
                        jsonResponse.commit(Response.Status.INTERNAL_SERVER_ERROR);
                }
            }
        }
        catch (Exception e) {
            log.log(FINE, "Failed writing response", e);
        }
    }

    private static void handle(DocumentPath path, ResponseHandler handler, com.yahoo.documentapi.Response response) {
        handle(path, handler, response, (document, jsonResponse) -> jsonResponse.commit(Response.Status.OK));
    }

    // ------------------------------------------------- Visits ------------------------------------------------

    private VisitorParameters parseGetParameters(HttpRequest request, DocumentPath path) {
        int wantedDocumentCount = Math.min(1 << 10, getProperty(request, WANTED_DOCUMENT_COUNT, integerParser).orElse(1));
        if (wantedDocumentCount <= 0)
            throw new IllegalArgumentException("wantedDocumentCount must be positive");

        int concurrency = Math.min(100, getProperty(request, CONCURRENCY, integerParser).orElse(1));
        if (concurrency <= 0)
            throw new IllegalArgumentException("concurrency must be positive");

        Optional<String> cluster = getProperty(request, CLUSTER);
        if (cluster.isEmpty() && path.documentType().isEmpty())
            throw new IllegalArgumentException("Must set 'cluster' parameter to a valid content cluster id when visiting at a root /document/v1/ level");

        VisitorParameters parameters = parseCommonParameters(request, path, cluster);
        parameters.setFieldSet(getProperty(request, FIELD_SET).orElse(path.documentType().map(type -> type + ":[document]").orElse(AllFields.NAME)));
        parameters.setMaxTotalHits(wantedDocumentCount);
        parameters.setThrottlePolicy(new StaticThrottlePolicy().setMaxPendingCount(concurrency));
        parameters.visitInconsistentBuckets(true);
        parameters.setSessionTimeoutMs(Math.max(1, request.getTimeout(TimeUnit.MILLISECONDS) - 5000));
        return parameters;
    }

    private VisitorParameters parseParameters(HttpRequest request, DocumentPath path) {
        disallow(request, CONCURRENCY, FIELD_SET, ROUTE, WANTED_DOCUMENT_COUNT);
        requireProperty(request, SELECTION);
        VisitorParameters parameters = parseCommonParameters(request, path, Optional.of(requireProperty(request, CLUSTER)));
        parameters.setThrottlePolicy(new DynamicThrottlePolicy().setMinWindowSize(1).setWindowSizeIncrement(1));
        long timeChunk = getProperty(request, TIME_CHUNK, timeoutMillisParser).orElse(60_000L);
        parameters.setSessionTimeoutMs(Math.max(1, Math.min(timeChunk, request.getTimeout(TimeUnit.MILLISECONDS) - 5000L)));
        return parameters;
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

        getProperty(request, CONTINUATION).map(ProgressToken::fromSerializedString).ifPresent(parameters::setResumeToken);
        parameters.setPriority(DocumentProtocol.Priority.NORMAL_4);

        StorageCluster storageCluster = resolveCluster(cluster, clusters);
        parameters.setRoute(storageCluster.name());
        parameters.setBucketSpace(resolveBucket(storageCluster,
                                                path.documentType(),
                                                List.of(FixedBucketSpaces.defaultSpace(), FixedBucketSpaces.globalSpace()),
                                                getProperty(request, BUCKET_SPACE)));

        return parameters;
    }

    private interface VisitCallback {
        /** Called at the start of response rendering. */
        default void onStart(JsonResponse response) throws IOException { }

        /** Called for every document received from backend visitors — must call the ack for these to proceed. */
        default void onDocument(JsonResponse response, Document document, Runnable ack, Consumer<String> onError) { }

        /** Called at the end of response rendering, before generic status data is written. */
        default void onEnd(JsonResponse response) throws IOException { }
    }

    private void visitAndDelete(HttpRequest request, VisitorParameters parameters, ResponseHandler handler,
                                TestAndSetCondition condition, String route) {
        visitAndProcess(request, parameters, handler, route, (id, operationParameters) -> {
            DocumentRemove remove = new DocumentRemove(id);
            remove.setCondition(condition);
            return asyncSession.remove(remove, operationParameters);
        });
    }

    private void visitAndUpdate(HttpRequest request, VisitorParameters parameters, ResponseHandler handler,
                                DocumentUpdate protoUpdate, String route) {
        visitAndProcess(request, parameters, handler, route, (id, operationParameters) -> {
                DocumentUpdate update = new DocumentUpdate(protoUpdate);
                update.setId(id);
                return asyncSession.update(update, operationParameters);
        });
    }

    private void visitAndProcess(HttpRequest request, VisitorParameters parameters, ResponseHandler handler,
                                 String route, BiFunction<DocumentId, DocumentOperationParameters, Result> operation) {
        visit(request, parameters, handler, new VisitCallback() {
            @Override public void onDocument(JsonResponse response, Document document, Runnable ack, Consumer<String> onError) {
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
                                    onError.accept(operationResponse.getTextMessage());
                                    break;
                                default:
                                    onError.accept("Unexpected response " + operationResponse);
                            }
                        });
                visitOperations.offer(() -> {
                    Result result = operation.apply(document.getId(), operationParameters);
                    if (result.type() == Result.ResultType.TRANSIENT_ERROR)
                        return false;

                    if (result.type() == Result.ResultType.FATAL_ERROR)
                        onError.accept(result.getError().getMessage());
                    else
                        outstanding.incrementAndGet();

                    ack.run();
                    return true;
                });
                dispatchFirstVisit();
            }
        });
    }

    private void visitAndWrite(HttpRequest request, VisitorParameters parameters, ResponseHandler handler) {
        visit(request, parameters, handler, new VisitCallback() {
            @Override public void onStart(JsonResponse response) throws IOException {
                response.writeDocumentsArrayStart();
            }
            @Override public void onDocument(JsonResponse response, Document document, Runnable ack, Consumer<String> onError) {
                response.writeDocumentValue(document);
                ack.run();
            }
            @Override public void onEnd(JsonResponse response) throws IOException {
                response.writeArrayEnd();
            }
        });
    }

    private void visitWithRemote(HttpRequest request, VisitorParameters parameters, ResponseHandler handler) {
        visit(request, parameters, handler, new VisitCallback() { });
    }

    private void visit(HttpRequest request, VisitorParameters parameters, ResponseHandler handler, VisitCallback callback) {
        try {
            JsonResponse response = JsonResponse.create(request, handler);
            Phaser phaser = new Phaser(2); // Synchronize this thread (dispatch) with the visitor callback thread.
            AtomicReference<String> error = new AtomicReference<>(); // Set if error occurs during processing of visited documents.
            callback.onStart(response);
            VisitorControlHandler controller = new VisitorControlHandler() {
                @Override public void onDone(CompletionCode code, String message) {
                    super.onDone(code, message);
                    loggingException(() -> {
                        callback.onEnd(response);
                        switch (code) {
                            case TIMEOUT:
                                if ( ! hasVisitedAnyBuckets() && parameters.getVisitInconsistentBuckets()) {
                                    response.writeMessage("No buckets visited within timeout of " +
                                                          parameters.getSessionTimeoutMs() + "ms (request timeout -5s)");
                                    response.respond(Response.Status.GATEWAY_TIMEOUT);
                                    break;
                                }
                            case SUCCESS: // Intentional fallthrough.
                            case ABORTED: // Intentional fallthrough.
                                if (error.get() == null) {
                                    ProgressToken progress = getProgress() != null ? getProgress() : parameters.getResumeToken();
                                    if (progress != null && ! progress.isFinished())
                                        response.writeContinuation(progress.serializeToString());

                                    if (getVisitorStatistics() != null)
                                        response.writeDocumentCount(getVisitorStatistics().getDocumentsVisited());

                                    response.respond(Response.Status.OK);
                                    break;
                                }
                            default:
                                response.writeMessage(error.get() != null ? error.get() : message != null ? message : "Visiting failed");
                                if (getVisitorStatistics() != null)
                                    response.writeDocumentCount(getVisitorStatistics().getDocumentsReturned());

                                response.respond(Response.Status.INTERNAL_SERVER_ERROR);
                        }
                    });
                    visitDispatcher.execute(() -> {
                        phaser.arriveAndAwaitAdvance(); // We may get here while dispatching thread is still putting us in the map.
                        visits.remove(this).destroy();
                    });
                }
            };
            if (parameters.getRemoteDataHandler() == null) {
                parameters.setLocalDataHandler(new VisitorDataHandler() {
                    @Override public void onMessage(Message m, AckToken token) {
                        if (m instanceof PutDocumentMessage)
                            callback.onDocument(response,
                                                ((PutDocumentMessage) m).getDocumentPut().getDocument(),
                                                () -> ack(token),
                                                errorMessage -> {
                                                    error.set(errorMessage);
                                                    controller.abort();
                                                });
                        else
                            throw new UnsupportedOperationException("Only PutDocumentMessage is supported, but got a " + m.getClass());
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

        private MeasuringResponseHandler(ResponseHandler delegate, com.yahoo.documentapi.metrics.DocumentOperationType type, Instant start) {
            this.delegate = delegate;
            this.type = type;
            this.start = start;
        }

        @Override
        public ContentChannel handleResponse(Response response) {
            switch (response.getStatus() / 100) {
                case 2: metrics.reportSuccessful(type, start); break;
                case 4: metrics.reportFailure(type, DocumentOperationStatus.REQUEST_ERROR); break;
                case 5: metrics.reportFailure(type, DocumentOperationStatus.SERVER_ERROR); break;
            }
            return delegate.handleResponse(response);
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
                       .collect(toUnmodifiableMap(storage -> storage.name(),
                                                  storage -> new StorageCluster(storage.name(),
                                                                                buckets.cluster(storage.name())
                                                                                       .documentType().entrySet().stream()
                                                                                       .collect(toMap(entry -> entry.getKey(),
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

    private static class DocumentPath {

        private final Path path;
        private final Optional<Group> group;

        DocumentPath(Path path) {
            this.path = requireNonNull(path);
            this.group = Optional.ofNullable(path.get("number")).map(integerParser::parse).map(Group::of)
                                 .or(() -> Optional.ofNullable(path.get("group")).map(Group::of));
        }

        DocumentId id() {
            return new DocumentId("id:" + requireNonNull(path.get("namespace")) +
                                  ":" + requireNonNull(path.get("documentType")) +
                                  ":" + group.map(Group::docIdPart).orElse("") +
                                  ":" + requireNonNull(path.getRest()));
        }

        String rawPath() { return path.asString(); }
        Optional<String> documentType() { return Optional.ofNullable(path.get("documentType")); }
        Optional<String> namespace() { return Optional.ofNullable(path.get("namespace")); }
        Optional<Group> group() { return group; }

    }

    static class Group {

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
        public static Group of(String value) { return new Group(value, "g=" + value, "id.group=='" + value.replaceAll("'", "\\\\'") + "'"); }

        public String value() { return value; }
        public String docIdPart() { return docIdPart; }
        public String selection() { return selection; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Group group = (Group) o;
            return value.equals(group.value) &&
                   docIdPart.equals(group.docIdPart) &&
                   selection.equals(group.selection);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, docIdPart, selection);
        }

        @Override
        public String toString() {
            return "Group{" +
                   "value='" + value + '\'' +
                   ", docIdPart='" + docIdPart + '\'' +
                   ", selection='" + selection + '\'' +
                   '}';
        }

    }

}
