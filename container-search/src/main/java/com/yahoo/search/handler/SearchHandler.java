// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import ai.vespa.metrics.ContainerMetrics;
import ai.vespa.cloud.ZoneInfo;
import com.yahoo.collections.Tuple2;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.Vtag;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.ContainerHttpConfig;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.container.jdisc.AclMapping;
import com.yahoo.container.jdisc.HttpMethodAclMapping;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.jdisc.RequestHandlerSpec;
import com.yahoo.container.jdisc.VespaHeaders;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.provider.DefaultEmbedderProvider;
import com.yahoo.net.AcceptHeaderMatcher;
import com.yahoo.net.HostName;
import com.yahoo.net.UriTools;
import com.yahoo.prelude.query.parser.ParseException;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.processing.rendering.Renderer;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.query.context.QueryContext;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.properties.DefaultProperties;
import com.yahoo.search.query.ranking.SoftTimeout;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.ExecutionFactory;
import com.yahoo.search.searchchain.SearchChainRegistry;
import com.yahoo.search.statistics.ElapsedTime;
import com.yahoo.slime.Inspector;
import com.yahoo.yolean.Exceptions;
import com.yahoo.yolean.trace.TraceNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.io.InputStream.nullInputStream;

/**
 * Handles search request.
 *
 * @author Steinar Knutsen
 * @author bratseth
 */
@SuppressWarnings("deprecation") // super class is deprecated
public class SearchHandler extends LoggingRequestHandler {

    private static final Logger log = Logger.getLogger(SearchHandler.class.getName());

    private final AtomicInteger requestsInFlight = new AtomicInteger(0);

    // max number of threads for the executor for this handler
    private final int maxThreads;

    private static final CompoundName DETAILED_TIMING_LOGGING = CompoundName.from("trace.timingDetails");
    private static final CompoundName FORCE_TIMESTAMPS = CompoundName.from("trace.timestamps");
    /** Event name for number of connections to the search subsystem */
    private static final String SEARCH_CONNECTIONS = ContainerMetrics.SEARCH_CONNECTIONS.baseName();
    static final String RENDER_LATENCY_METRIC = ContainerMetrics.JDISC_RENDER_LATENCY.baseName();
    static final String MIME_DIMENSION = "mime";
    static final String RENDERER_DIMENSION = "renderer";

    private static final String JSON_CONTENT_TYPE = "application/json";
    public static final String defaultSearchChainName = "default";
    private static final String fallbackSearchChain = "vespa";

    private final CompiledQueryProfileRegistry queryProfileRegistry;

    /** If present, responses from this will set the HTTP response header with this key to the host name of this */
    private final Optional<String> hostResponseHeaderKey;

    private final String selfHostname = HostName.getLocalhost();
    private final Map<String, Embedder> embedders;
    private final ExecutionFactory executionFactory;
    private final AtomicLong numRequestsLeftToTrace;

    private final ZoneInfo zoneInfo;

    private final static RequestHandlerSpec REQUEST_HANDLER_SPEC = RequestHandlerSpec.builder()
            .withAclMapping(SearchHandler.aclRequestMapper()).build();


    @Inject
    public SearchHandler(Metric metric,
                         ContainerThreadPool threadpool,
                         CompiledQueryProfileRegistry queryProfileRegistry,
                         ContainerHttpConfig config,
                         ComponentRegistry<Embedder> embedders,
                         ExecutionFactory executionFactory,
                         ZoneInfo zoneInfo) {
        this(metric,
             threadpool.executor(),
             queryProfileRegistry,
             embedders,
             executionFactory,
             config.numQueriesToTraceOnDebugAfterConstruction(),
             config.hostResponseHeaderKey().isEmpty() ? Optional.empty() : Optional.of(config.hostResponseHeaderKey()),
             config.warmup(),
             zoneInfo);
    }

    private SearchHandler(Metric metric,
                          Executor executor,
                          CompiledQueryProfileRegistry queryProfileRegistry,
                          ComponentRegistry<Embedder> embedders,
                          ExecutionFactory executionFactory,
                          long numQueriesToTraceOnDebugAfterStartup,
                          Optional<String> hostResponseHeaderKey,
                          boolean warmup,
                          ZoneInfo zoneInfo) {
        super(executor, metric, true);
        log.log(Level.FINE, () -> "SearchHandler.init " + System.identityHashCode(this));
        this.queryProfileRegistry = queryProfileRegistry;
        this.embedders = toMap(embedders);
        this.executionFactory = executionFactory;

        this.maxThreads = examineExecutor(executor);

        this.hostResponseHeaderKey = hostResponseHeaderKey;
        this.numRequestsLeftToTrace = new AtomicLong(numQueriesToTraceOnDebugAfterStartup);
        metric.set(SEARCH_CONNECTIONS, 0.0d, null);
        this.zoneInfo = zoneInfo;

        if (warmup)
            warmup();
    }

    Metric metric() { return metric; }

    private static int examineExecutor(Executor executor) {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getMaximumPoolSize();
        }
        return Integer.MAX_VALUE; // assume unbound
    }

    private void warmup() {
        try {
            handle(HttpRequest.createTestRequest("/search/" +
                                                 "?timeout=2s" +
                                                 "&ranking.profile=unranked" +
                                                 "&warmup=true" +
                                                 "&metrics.ignore=true" +
                                                 "&yql=select+*+from+sources+*+where+true+limit+0;",
                                                 com.yahoo.jdisc.http.HttpRequest.Method.GET,
                                                 nullInputStream()));
        }
        catch (RuntimeException e) {
            log.log(Level.INFO, "Exception warming up search handler", e);
        }
    }

    @Override
    public final HttpResponse handle(com.yahoo.container.jdisc.HttpRequest request) {
        requestsInFlight.incrementAndGet();
        try {
            try {
                return handleBody(request);
            } catch (IllegalInputException e) {
                return illegalQueryResponse(request, e);
            } catch (RuntimeException e) { // Make sure we generate a valid response even on unexpected errors
                log.log(Level.WARNING, "Failed handling " + request, e);
                return internalServerErrorResponse(request, e);
            }
        } finally {
            requestsInFlight.decrementAndGet();
        }
    }

    @Override
    public Optional<Request.RequestType> getRequestType() { return Optional.of(Request.RequestType.READ); }

    static int getHttpResponseStatus(com.yahoo.container.jdisc.HttpRequest httpRequest, Result result) {
        boolean benchmarkOutput = VespaHeaders.benchmarkOutput(httpRequest);
        if (benchmarkOutput) {
            return VespaHeaders.getEagerErrorStatus(result.hits().getError(),
                                                    SearchResponse.getErrorIterator(result.hits().getErrorHit()));
        } else {
            return VespaHeaders.getStatus(SearchResponse.isSuccess(result),
                                          result.hits().getError(),
                                          SearchResponse.getErrorIterator(result.hits().getErrorHit()));
        }

    }

    private HttpResponse errorResponse(HttpRequest request, ErrorMessage errorMessage) {
        Query query = new Query();
        Result result = new Result(query, errorMessage);
        Renderer<Result> renderer = getRendererCopy(ComponentSpecification.fromString(request.getProperty("format")));

        return new HttpSearchResponse(getHttpResponseStatus(request, result), result, query, renderer);
    }

    private HttpResponse illegalQueryResponse(HttpRequest request, RuntimeException e) {
        return errorResponse(request, ErrorMessage.createIllegalQuery(Exceptions.toMessageString(e)));
    }

    private HttpResponse internalServerErrorResponse(HttpRequest request, RuntimeException e) {
        return errorResponse(request, ErrorMessage.createInternalServerError(Exceptions.toMessageString(e)));
    }

    private HttpSearchResponse handleBody(HttpRequest request) {
        long executionStart = System.currentTimeMillis();
        Map<String, String> requestMap = requestMapFromRequest(request);

        // Get query profile
        String queryProfileName = requestMap.getOrDefault("queryProfile", null);
        CompiledQueryProfile queryProfile = queryProfileRegistry.findQueryProfile(queryProfileName);

        Query query = new Query.Builder().setRequest(request)
                                         .setRequestMap(requestMap)
                                         .setQueryProfile(queryProfile)
                                         .setEmbedders(embedders)
                                         .setZoneInfo(zoneInfo)
                                         .setSchemaInfo(executionFactory.schemaInfo())
                                         .build();
        query.getHttpRequest().context().put("search.handlerStartTime", executionStart);

        // If format not explicitly set, use Accept header to determine response format
        if (!requestMap.containsKey("format") && !requestMap.containsKey("presentation.format")) {
            setFormatFromAcceptHeader(request, query);
        }

        boolean benchmarking = VespaHeaders.benchmarkOutput(request);
        boolean benchmarkCoverage = VespaHeaders.benchmarkCoverage(benchmarking, request.getJDiscRequest().headers());

        // Don't use soft timeout by default when benchmarking to avoid wrong conclusions by excluding nodes
        if (benchmarking && ! request.hasProperty(SoftTimeout.enableProperty.toString()))
            query.properties().set(SoftTimeout.enableProperty, false);

        // Find and execute search chain if we have a valid query
        String invalidReason = query.validate();
        Chain<Searcher> searchChain = null;
        String searchChainName = null;
        if (invalidReason == null) {
            Tuple2<String, Chain<Searcher>> nameAndChain = resolveChain(query.properties().getString(Query.SEARCH_CHAIN));
            searchChainName = nameAndChain.first;
            searchChain = nameAndChain.second;
        }

        // Create the result
        Result result;
        if (invalidReason != null) {
            result = new Result(query, ErrorMessage.createIllegalQuery(invalidReason));
        } else if (queryProfile == null && queryProfileName != null) {
            result = new Result(query,
                                ErrorMessage.createIllegalQuery("Could not resolve query profile '" + queryProfileName + "'"));
        } else if (searchChain == null) {
            result = new Result(query,
                                ErrorMessage.createInvalidQueryParameter("No search chain named '" + searchChainName + "' was found"));
        } else if (query.getTimeLeft() <= 0) {
            result = new Result(query,
                                ErrorMessage.createTimeout("No time left after waiting for " + query.getDurationTime() + "ms to execute query"));
        } else {
            String pathAndQuery = UriTools.rawRequest(request.getUri());
            result = search(pathAndQuery, query, searchChain);
        }

        // Transform result to response
        Renderer<Result> renderer = toRendererCopy(query.getPresentation().getRenderer());
        HttpSearchResponse response = new HttpSearchResponse(getHttpResponseStatus(request, result),
                                                             result, query, renderer,
                                                             extractTraceNode(query),
                                                             metric);
        response.setRequestType(Request.RequestType.READ);
        hostResponseHeaderKey.ifPresent(key -> response.headers().add(key, selfHostname));

        if (benchmarking)
            VespaHeaders.benchmarkOutput(response.headers(), benchmarkCoverage, response.getTiming(),
                                         response.getHitCounts(), getErrors(result), response.getCoverage());

        return response;
    }

    private static TraceNode extractTraceNode(Query query) {
        if (log.isLoggable(Level.FINE)) {
            QueryContext queryContext = query.getContext(false);
            if (queryContext != null) {
                Execution.Trace trace = queryContext.getTrace();
                if (trace != null) {
                    return trace.traceNode();
                }
            }
        }
        return null;
    }

    private static int getErrors(Result result) {
        return result.hits().getErrorHit() == null ? 0 : 1;
    }

    private Renderer<Result> toRendererCopy(ComponentSpecification format) {
        return perRenderingCopy(executionFactory.rendererRegistry().getRenderer(format));
    }

    private Tuple2<String, Chain<Searcher>> resolveChain(String explicitChainName) {
        String chainName = explicitChainName;
        if (chainName == null) {
            chainName = defaultSearchChainName;
        }

        Chain<Searcher> searchChain = executionFactory.searchChainRegistry().getChain(chainName);
        if (searchChain == null && explicitChainName == null) { // explicit chain not found should cause error
            chainName = fallbackSearchChain;
            searchChain = executionFactory.searchChainRegistry().getChain(chainName);
        }
        return new Tuple2<>(chainName, searchChain);
    }

    /** Used from container SDK, for internal use only */
    public Result searchAndFill(Query query, Chain<? extends Searcher> searchChain) {
        Result errorResult = validateQuery(query);
        if (errorResult != null) return errorResult;

        Renderer<Result> renderer = executionFactory.rendererRegistry().getRenderer(query.getPresentation().getRenderer());

        // docsumClass null means "unset", so we set it (it might be null
        // here too in which case it will still be "unset" after we set it :-)
        if (query.getPresentation().getSummary() == null && renderer instanceof com.yahoo.search.rendering.Renderer)
            query.getPresentation().setSummary(((com.yahoo.search.rendering.Renderer) renderer).getDefaultSummaryClass());

        Execution execution = executionFactory.newExecution(searchChain);
        query.getModel().setExecution(execution);
        if (log.isLoggable(Level.FINE) && (numRequestsLeftToTrace.getAndDecrement() > 0)) {
            query.setTraceLevel(Math.max(1, query.getTraceLevel()));
            execution.trace().setForceTimestamps(true);

        } else {
            execution.trace().setForceTimestamps(query.properties().getBoolean(FORCE_TIMESTAMPS, false));
        }
        if (query.properties().getBoolean(DETAILED_TIMING_LOGGING, false)) {
            // check and set (instead of set directly) to avoid overwriting stuff from prepareForBreakdownAnalysis()
            execution.context().setDetailedDiagnostics(true);
        }
        Result result = execution.search(query);

        ensureQuerySet(result, query);
        execution.fill(result);

        traceExecutionTimes(query, result);
        traceVespaVersion(query);
        traceRequestAttributes(query);
        return result;
    }

    private void traceRequestAttributes(Query query) {
        int miminumTraceLevel = 7;
        if (query.getTraceLevel() >= 7) {
            query.trace("Request attributes: " + query.getHttpRequest().context(), miminumTraceLevel);
        }
    }

    /** For internal use only */
    public Renderer<Result> getRendererCopy(ComponentSpecification spec) {
        Renderer<Result> renderer = executionFactory.rendererRegistry().getRenderer(spec);
        return perRenderingCopy(renderer);
    }

    private Renderer<Result> perRenderingCopy(Renderer<Result> renderer) {
        Renderer<Result> copy = renderer.clone();
        copy.init();
        return copy;
    }

    private void ensureQuerySet(Result result, Query fallbackQuery) {
        Query query = result.getQuery();
        if (query == null) {
            result.setQuery(fallbackQuery);
        }
    }

    private Result search(String request, Query query, Chain<Searcher> searchChain) {
        if (query.getTraceLevel() >= 2) {
            query.trace("Invoking " + searchChain, false, 2);
        }

        connectionStatistics();

        try {
            return searchAndFill(query, searchChain);
        } catch (ParseException e) {
            ErrorMessage error = ErrorMessage.createIllegalQuery("Could not parse query [" + request + "]: "
                                                                 + Exceptions.toMessageString(e));
            log.log(Level.FINE, error::getDetailedMessage);
            return new Result(query, error);
        } catch (IllegalInputException e) {
            ErrorMessage error = ErrorMessage.createBadRequest("Invalid request [" + request + "]: "
                                                               + Exceptions.toMessageString(e));
            log.log(Level.FINE, error::getDetailedMessage);
            return new Result(query, error);
        } catch (Exception e) {
            log(request, query, e);
            return new Result(query, ErrorMessage.createUnspecifiedError("Failed: " +
                                                                         Exceptions.toMessageString(e), e));
        } catch (LinkageError | StackOverflowError e) {
            // LinkageError should have been an Exception in an OSGi world - typical bundle dependency issue problem
            // StackOverflowError is recoverable
            ErrorMessage error = ErrorMessage.createErrorInPluginSearcher("Error executing " + searchChain + "]: " +
                                                                          Exceptions.toMessageString(e), e);
            log(request, query, e);
            return new Result(query, error);
        }
    }

    private void connectionStatistics() {
        if (maxThreads <= 3) return;

        int connections = requestsInFlight.intValue();
        metric.set(SEARCH_CONNECTIONS, connections, null);
        // cast to long to avoid overflows if maxThreads is at no
        // log value (maxint)
        long maxThreadsAsLong = maxThreads;
        long connectionsAsLong = connections;
        // only log when exactly crossing the limit to avoid
        // spamming the log
        if (connectionsAsLong < maxThreadsAsLong * 9L / 10L) {
            // NOP
        } else if (connectionsAsLong == maxThreadsAsLong * 9L / 10L) {
            log.log(Level.WARNING, threadConsumptionMessage(connections, maxThreads, "90"));
        } else if (connectionsAsLong == maxThreadsAsLong * 95L / 100L) {
            log.log(Level.WARNING, threadConsumptionMessage(connections, maxThreads, "95"));
        } else if (connectionsAsLong == maxThreadsAsLong) {
            log.log(Level.WARNING, threadConsumptionMessage(connections, maxThreads, "100"));
        }
    }

    private String threadConsumptionMessage(int connections, int maxThreads, String percentage) {
        return percentage + "% of possible search connections (" + connections +
               " of maximum " + maxThreads + ") currently active.";
    }

    private void log(String request, Query query, Throwable e) {
        // Attempted workaround for missing stack traces
        if (e.getStackTrace().length == 0) {
            log.log(Level.SEVERE, "Failed executing " + query.toDetailString() +
                                  " [" + request + "], received exception with no context", e);
        } else {
            log.log(Level.SEVERE, "Failed executing " + query.toDetailString() + " [" + request + "]", e);
        }
    }

    private Result validateQuery(Query query) {
        DefaultProperties.requireNotPresentIn(query.getHttpRequest().propertyMap());

        int maxHits = query.properties().getInteger(DefaultProperties.MAX_HITS);
        int maxOffset = query.properties().getInteger(DefaultProperties.MAX_OFFSET);

        if (query.getHits() > maxHits) {
            return new Result(query, ErrorMessage.createIllegalQuery(query.getHits() +
                              " hits requested, configured limit: " + maxHits +
                              ". See https://docs.vespa.ai/en/reference/api/query.html#native-execution-parameters"));

        } else if (query.getOffset() > maxOffset) {
            return new Result(query, ErrorMessage.createIllegalQuery("Offset of " + query.getOffset() +
                              " requested, configured limit: " + maxOffset +
                              ". See https://docs.vespa.ai/en/reference/api/query.html#native-execution-parameters"));
        }
        return null;
    }

    private void traceExecutionTimes(Query query, Result result) {
        if (query.getTraceLevel() < 3) return;

        ElapsedTime elapsedTime = result.getElapsedTime();
        long now = System.currentTimeMillis();
        if (elapsedTime.firstFill() != 0) {
            query.trace("Query time " + query + ": " + (elapsedTime.firstFill() - elapsedTime.first()) + " ms", false, 3);
            query.trace("Summary fetch time " + query + ": " + (now - elapsedTime.firstFill()) + " ms", false, 3);
        } else {
            query.trace("Total search time " + query + ": " + (now - elapsedTime.first()) + " ms", false, 3);
        }
    }

    private void traceVespaVersion(Query query) {
        query.trace("Vespa version: " + Vtag.currentVersion, false, 4);
    }

    public SearchChainRegistry getSearchChainRegistry() { return executionFactory.searchChainRegistry();
    }

    static private String getMediaType(HttpRequest request) {
        String header = request.getHeader(com.yahoo.jdisc.http.HttpHeaders.Names.CONTENT_TYPE);
        if (header == null) {
            return "";
        }
        int semi = header.indexOf(';');
        if (semi != -1) {
            header = header.substring(0, semi);
        }
        return com.yahoo.text.Lowercase.toLowerCase(header.trim());
    }

    private static final String CBOR_CONTENT_TYPE = "application/cbor";

    /** Sets the response format based on the Accept header if CBOR is preferred over JSON */
    private static void setFormatFromAcceptHeader(HttpRequest request, Query query) {
        String acceptHeader = request.getHeader(com.yahoo.jdisc.http.HttpHeaders.Names.ACCEPT);
        if (acceptHeader == null || acceptHeader.isEmpty()) return;

        try {
            var acceptMatcher = new AcceptHeaderMatcher(acceptHeader);
            var preferred = acceptMatcher.preferredExactMediaTypes(CBOR_CONTENT_TYPE, JSON_CONTENT_TYPE);
            if (!preferred.isEmpty() && CBOR_CONTENT_TYPE.equals(preferred.get(0))) {
                query.getPresentation().setFormat("cbor");
            }
        } catch (IllegalArgumentException e) {
            query.trace("Ignoring malformed Accept header: " + e.getMessage(), 2);
        }
    }

    /** Add properties POSTed as a JSON payload, if any, to the request map */
    private Map<String, String> requestMapFromRequest(HttpRequest request) {
        if (request.getMethod() != com.yahoo.jdisc.http.HttpRequest.Method.POST
            ||  ! JSON_CONTENT_TYPE.equals(getMediaType(request)))
            return request.propertyMap();

        Map<String, String> requestMap = new Json2SingleLevelMap(request.getData()).parse();

        // Add fields from JSON to the request map
        requestMap.putAll(request.propertyMap());

        if (requestMap.containsKey("yql") && (requestMap.containsKey("select.where") || requestMap.containsKey("select.grouping")) )
            throw new IllegalInputException("Illegal query: Query contains both yql and select parameter");
        if (requestMap.containsKey("query") && (requestMap.containsKey("select.where") || requestMap.containsKey("select.grouping")) )
            throw new IllegalInputException("Illegal query: Query contains both query and select parameter");

        return requestMap;
    }

    @Deprecated // TODO: Remove on Vespa 9
    public void createRequestMapping(Inspector inspector, Map<String, String> map, String parent) {
        try {
            new Json2SingleLevelMap(new ByteArrayInputStream(inspector.toString().getBytes(StandardCharsets.UTF_8))).parse(map, parent);
        } catch (IOException e) {
            throw new RuntimeException("Failed creating request mapping for parent '" + parent + "'", e);
        }
    }

    @Override
    public RequestHandlerSpec requestHandlerSpec() {
        return REQUEST_HANDLER_SPEC;
    }

    private static AclMapping aclRequestMapper() {
        return HttpMethodAclMapping.standard()
                .override(com.yahoo.jdisc.http.HttpRequest.Method.POST, AclMapping.Action.READ)
                .build();
    }

    private Map<String, Embedder> toMap(ComponentRegistry<Embedder> embedders) {
        var map = embedders.allComponentsById().entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey().stringValue(), Map.Entry::getValue));
        if (map.size() > 1) {
            map.remove(DefaultEmbedderProvider.class.getName());
            // Ideally, this should be handled by dependency injection, however for now this workaround is necessary.
        }
        return Collections.unmodifiableMap(map);
    }

}
