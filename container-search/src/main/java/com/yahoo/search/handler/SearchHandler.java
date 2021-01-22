// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import com.google.inject.Inject;
import com.yahoo.collections.Tuple2;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.Vtag;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.core.ContainerHttpConfig;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.jdisc.VespaHeaders;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.language.Linguistics;
import com.yahoo.net.HostName;
import com.yahoo.net.UriTools;
import com.yahoo.prelude.query.parser.ParseException;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.processing.rendering.Renderer;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.query.context.QueryContext;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfileConfigurer;
import com.yahoo.search.query.profile.config.QueryProfilesConfig;
import com.yahoo.search.query.properties.DefaultProperties;
import com.yahoo.search.query.ranking.SoftTimeout;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.ExecutionFactory;
import com.yahoo.search.searchchain.SearchChainRegistry;
import com.yahoo.search.statistics.ElapsedTime;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.statistics.Callback;
import com.yahoo.statistics.Handle;
import com.yahoo.statistics.Statistics;
import com.yahoo.statistics.Value;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig;
import com.yahoo.yolean.Exceptions;
import com.yahoo.yolean.trace.TraceNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles search request.
 *
 * @author Steinar Knutsen
 */
public class SearchHandler extends LoggingRequestHandler {

    private static final Logger log = Logger.getLogger(SearchHandler.class.getName());

    private final AtomicInteger requestsInFlight = new AtomicInteger(0);

    // max number of threads for the executor for this handler
    private final int maxThreads;

    private static final CompoundName DETAILED_TIMING_LOGGING = new CompoundName("trace.timingDetails");
    private static final CompoundName FORCE_TIMESTAMPS = new CompoundName("trace.timestamps");

    /** Event name for number of connections to the search subsystem */
    private static final String SEARCH_CONNECTIONS = "search_connections";

    private static final String JSON_CONTENT_TYPE = "application/json";

    private final Value searchConnections;

    public static final String defaultSearchChainName = "default";
    private static final String fallbackSearchChain = "vespa";

    private final CompiledQueryProfileRegistry queryProfileRegistry;
    
    /** If present, responses from this will set the HTTP response header with this key to the host name of this */
    private final Optional<String> hostResponseHeaderKey;
    
    private final String selfHostname = HostName.getLocalhost();

    private final ExecutionFactory executionFactory;

    private final AtomicLong numRequestsLeftToTrace;

    private final class MeanConnections implements Callback {

        @Override
        public void run(Handle h, boolean firstTime) {
            if (firstTime) {
                metric.set(SEARCH_CONNECTIONS, 0.0d, null);
                return;
            }
            Value v = (Value) h;
            metric.set(SEARCH_CONNECTIONS, v.getMean(), null);
        }
    }

    @Inject
    public SearchHandler(Statistics statistics,
                         Metric metric,
                         ContainerThreadPool threadpool,
                         AccessLog ignored,
                         CompiledQueryProfileRegistry queryProfileRegistry,
                         ContainerHttpConfig config,
                         ExecutionFactory executionFactory) {
        this(statistics, metric, threadpool.executor(), ignored, queryProfileRegistry, config, executionFactory);
    }

    public SearchHandler(Statistics statistics,
                         Metric metric,
                         Executor executor,
                         AccessLog ignored,
                         CompiledQueryProfileRegistry queryProfileRegistry,
                         ContainerHttpConfig containerHttpConfig,
                         ExecutionFactory executionFactory) {
        this(statistics,
             metric,
             executor,
             queryProfileRegistry,
             executionFactory,
             containerHttpConfig.numQueriesToTraceOnDebugAfterConstruction(),
             containerHttpConfig.hostResponseHeaderKey().equals("") ?
                     Optional.empty() : Optional.of(containerHttpConfig.hostResponseHeaderKey()));
    }

    /**
     * @deprecated Use the @Inject annotated constructor instead.
     */
    @Deprecated // Vespa 8
    public SearchHandler(Statistics statistics,
                         Metric metric,
                         Executor executor,
                         AccessLog ignored,
                         QueryProfilesConfig queryProfileConfig,
                         ContainerHttpConfig containerHttpConfig,
                         ExecutionFactory executionFactory) {
        this(statistics,
             metric,
             executor,
             QueryProfileConfigurer.createFromConfig(queryProfileConfig).compile(),
             executionFactory,
             containerHttpConfig.numQueriesToTraceOnDebugAfterConstruction(),
             containerHttpConfig.hostResponseHeaderKey().equals("") ?
                     Optional.empty() : Optional.of( containerHttpConfig.hostResponseHeaderKey()));
    }

    public SearchHandler(Statistics statistics,
                         Metric metric,
                         Executor executor,
                         AccessLog ignored,
                         CompiledQueryProfileRegistry queryProfileRegistry,
                         ExecutionFactory executionFactory,
                         Optional<String> hostResponseHeaderKey) {
        this(statistics, metric, executor, queryProfileRegistry, executionFactory, 0, hostResponseHeaderKey);
    }

    private SearchHandler(Statistics statistics,
                         Metric metric,
                         Executor executor,
                         CompiledQueryProfileRegistry queryProfileRegistry,
                         ExecutionFactory executionFactory,
                         long numQueriesToTraceOnDebugAfterStartup,
                         Optional<String> hostResponseHeaderKey) {
        super(executor, metric, true);
        log.log(Level.FINE, "SearchHandler.init " + System.identityHashCode(this));
        this.queryProfileRegistry = queryProfileRegistry;
        this.executionFactory = executionFactory;

        this.maxThreads = examineExecutor(executor);

        searchConnections = new Value(SEARCH_CONNECTIONS, statistics,
                                      new Value.Parameters().setLogRaw(true).setLogMax(true)
                                                            .setLogMean(true).setLogMin(true)
                                                            .setNameExtension(true)
                                                            .setCallback(new MeanConnections()));

        this.hostResponseHeaderKey = hostResponseHeaderKey;
        this.numRequestsLeftToTrace = new AtomicLong(numQueriesToTraceOnDebugAfterStartup);
    }

    /** @deprecated use the other constructor */
    @Deprecated // TODO: Remove on Vespa 8
    public SearchHandler(ChainsConfig chainsConfig,
                         IndexInfoConfig indexInfo,
                         QrSearchersConfig clusters,
                         SpecialtokensConfig specialtokens,
                         Statistics statistics,
                         Linguistics linguistics,
                         Metric metric,
                         ComponentRegistry<Renderer> renderers,
                         Executor executor,
                         AccessLog accessLog,
                         QueryProfilesConfig queryProfileConfig,
                         ComponentRegistry<Searcher> searchers,
                         ContainerHttpConfig containerHttpConfig) {
        this(statistics,
             metric,
             executor,
             accessLog,
             queryProfileConfig,
             containerHttpConfig,
             new ExecutionFactory(chainsConfig, indexInfo, clusters, searchers, specialtokens, linguistics, renderers));
    }

    private static int examineExecutor(Executor executor) {
        if (executor instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) executor).getMaximumPoolSize();
        }
        return Integer.MAX_VALUE; // assume unbound
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

    private int getHttpResponseStatus(com.yahoo.container.jdisc.HttpRequest httpRequest, Result result) {
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
        Renderer renderer = getRendererCopy(ComponentSpecification.fromString(request.getProperty("format")));

        return new HttpSearchResponse(getHttpResponseStatus(request, result), result, query, renderer);
    }

    private HttpResponse illegalQueryResponse(HttpRequest request, RuntimeException e) {
        return errorResponse(request, ErrorMessage.createIllegalQuery(Exceptions.toMessageString(e)));
    }

    private HttpResponse internalServerErrorResponse(HttpRequest request, RuntimeException e) {
        return errorResponse(request, ErrorMessage.createInternalServerError(Exceptions.toMessageString(e)));
    }

    private HttpSearchResponse handleBody(HttpRequest request) {
        Map<String, String> requestMap = requestMapFromRequest(request);

        // Get query profile
        String queryProfileName = requestMap.getOrDefault("queryProfile", null);
        CompiledQueryProfile queryProfile = queryProfileRegistry.findQueryProfile(queryProfileName);

        Query query = new Query(request, requestMap, queryProfile);

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
        } else {
            String pathAndQuery = UriTools.rawRequest(request.getUri());
            result = search(pathAndQuery, query, searchChain);
        }

        // Transform result to response
        Renderer renderer = toRendererCopy(query.getPresentation().getRenderer());
        HttpSearchResponse response = new HttpSearchResponse(getHttpResponseStatus(request, result),
                                                             result, query, renderer,
                                                             extractTraceNode(query));
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
        Renderer<Result> renderer = executionFactory.rendererRegistry().getRenderer(format);
        renderer = perRenderingCopy(renderer);
        return renderer;
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
        execution.fill(result, result.getQuery().getPresentation().getSummary());

        traceExecutionTimes(query, result);
        traceVespaVersion(query);
        traceRequestAttributes(query);
        return result;
    }

    private void traceRequestAttributes(Query query) {
        int miminumTraceLevel = 7;
        if (query.getTraceLevel() >= 7) {
            query.trace("Request attributes: " + query.getHttpRequest().getJDiscRequest().context(), miminumTraceLevel);
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

        if (searchConnections != null) {
            connectionStatistics();
        } else {
            log.log(Level.WARNING,
                    "searchConnections is a null reference, probably a known race condition during startup.",
                    new IllegalStateException("searchConnections reference is null."));
        }
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
        } catch (IllegalArgumentException e) {
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
        } catch (Exception e) {
            log(request, query, e);
            return new Result(query, ErrorMessage.createUnspecifiedError("Failed: " +
                                                                         Exceptions.toMessageString(e), e));
        }
    }

    private void connectionStatistics() {
        int connections = requestsInFlight.intValue();
        searchConnections.put(connections);
        if (maxThreads > 3) {
            // cast to long to avoid overflows if maxThreads is at no
            // log value (maxint)
            final long maxThreadsAsLong = maxThreads;
            final long connectionsAsLong = connections;
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
        if (query.getHttpRequest().getProperty(DefaultProperties.MAX_HITS.toString()) != null)
            throw new RuntimeException(DefaultProperties.MAX_HITS + " must be specified in a query profile.");

        if (query.getHttpRequest().getProperty(DefaultProperties.MAX_OFFSET.toString()) != null)
            throw new RuntimeException(DefaultProperties.MAX_OFFSET + " must be specified in a query profile.");

        int maxHits = query.properties().getInteger(DefaultProperties.MAX_HITS);
        int maxOffset = query.properties().getInteger(DefaultProperties.MAX_OFFSET);

        if (query.getHits() > maxHits) {
            return new Result(query, ErrorMessage.createIllegalQuery(query.getHits() +
                              " hits requested, configured limit: " + maxHits + "."));

        } else if (query.getOffset() > maxOffset) {
            return new Result(query,
                    ErrorMessage.createIllegalQuery("Offset of " + query.getOffset() +
                                                    " requested, configured limit: " + maxOffset + "."));
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
        query.trace("Vespa version: " + Vtag.currentVersion.toString(), false, 4);
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

    /** Add properties POSTed as a JSON payload, if any, to the request map */
    private Map<String, String> requestMapFromRequest(HttpRequest request) {
        if (request.getMethod() != com.yahoo.jdisc.http.HttpRequest.Method.POST
            ||  ! JSON_CONTENT_TYPE.equals(getMediaType(request)))
            return request.propertyMap();

        Inspector inspector;
        try {
            // Use an 4k buffer, that should be plenty for most json requests to pass in a single chunk
            byte[] byteArray = IOUtils.readBytes(request.getData(), 4096);
            inspector = SlimeUtils.jsonToSlime(byteArray).get();
            if (inspector.field("error_message").valid()) {
                throw new IllegalInputException("Illegal query: " + inspector.field("error_message").asString() + " at: '" +
                                                new String(inspector.field("offending_input").asData(), StandardCharsets.UTF_8) + "'");
            }

        } catch (IOException e) {
            throw new RuntimeException("Problem reading POSTed data", e);
        }

        // Add fields from JSON to the request map
        Map<String, String> requestMap = new HashMap<>();
        createRequestMapping(inspector, requestMap, "");
        requestMap.putAll(request.propertyMap());

        if (requestMap.containsKey("yql") && (requestMap.containsKey("select.where") || requestMap.containsKey("select.grouping")) )
            throw new IllegalInputException("Illegal query: Query contains both yql and select parameter");
        if (requestMap.containsKey("query") && (requestMap.containsKey("select.where") || requestMap.containsKey("select.grouping")) )
            throw new IllegalInputException("Illegal query: Query contains both query and select parameter");

        return requestMap;
    }

    public void createRequestMapping(Inspector inspector, Map<String, String> map, String parent) {
        inspector.traverse((ObjectTraverser) (key, value) -> {
            String qualifiedKey = parent + key;
            switch (value.type()) {
                case BOOL:
                    map.put(qualifiedKey, Boolean.toString(value.asBool()));
                    break;
                case DOUBLE:
                    map.put(qualifiedKey, Double.toString(value.asDouble()));
                    break;
                case LONG:
                    map.put(qualifiedKey, Long.toString(value.asLong()));
                    break;
                case STRING:
                    map.put(qualifiedKey , value.asString());
                    break;
                case ARRAY:
                    map.put(qualifiedKey, value.toString()); // XXX: Causes parsing the JSON twice (Query.setPropertiesFromRequestMap)
                    break;
                case OBJECT:
                    if (qualifiedKey.equals("select.where") || qualifiedKey.equals("select.grouping")) {
                        map.put(qualifiedKey, value.toString());  // XXX: Causes parsing the JSON twice (Query.setPropertiesFromRequestMap)
                        break;
                    }
                    createRequestMapping(value, map, qualifiedKey + ".");
                    break;
            }

        });
    }

}


