// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import com.google.inject.Inject;
import com.yahoo.collections.Tuple2;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.Vtag;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.ChainsConfigurer;
import com.yahoo.component.chain.model.ChainsModel;
import com.yahoo.component.chain.model.ChainsModelBuilder;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.core.ContainerHttpConfig;
import com.yahoo.container.core.QrTemplatesConfig;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.jdisc.VespaHeaders;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.protect.FreezeDetector;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Response;
import com.yahoo.language.Linguistics;
import com.yahoo.log.LogLevel;
import com.yahoo.net.HostName;
import com.yahoo.net.UriTools;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.query.QueryException;
import com.yahoo.prelude.query.parser.ParseException;
import com.yahoo.prelude.query.parser.SpecialTokenRegistry;
import com.yahoo.processing.rendering.Renderer;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.yolean.Exceptions;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfileConfigurer;
import com.yahoo.search.query.profile.config.QueryProfilesConfig;
import com.yahoo.search.query.properties.DefaultProperties;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.SearchChainRegistry;
import com.yahoo.search.statistics.ElapsedTime;
import com.yahoo.statistics.Callback;
import com.yahoo.statistics.Handle;
import com.yahoo.statistics.Statistics;
import com.yahoo.statistics.Value;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles search request.
 *
 * @author Steinar Knutsen
 */
public class SearchHandler extends LoggingRequestHandler {

    private final AtomicInteger requestsInFlight = new AtomicInteger(0);

    // max number of threads for the executor for this handler
    private final int maxThreads;

    private static final CompoundName DETAILED_TIMING_LOGGING = new CompoundName("trace.timingDetails");

    /** Event name for number of connections to the search subsystem */
    private static final String SEARCH_CONNECTIONS = "search_connections";

    private static Logger log = Logger.getLogger(SearchHandler.class.getName());

    private Value searchConnections;

    private final SearchChainRegistry searchChainRegistry;

    private final RendererRegistry rendererRegistry;

    private final IndexFacts indexFacts;

    private final SpecialTokenRegistry specialTokens;

    public static final String defaultSearchChainName = "default";
    private static final String fallbackSearchChain = "vespa";
    private static final CompoundName FORCE_TIMESTAMPS = new CompoundName("trace.timestamps");;

    private final Linguistics linguistics;

    private final CompiledQueryProfileRegistry queryProfileRegistry;
    
    /** If present, responses from this will set the HTTP response header with this key to the host name of this */
    private final Optional<String> hostResponseHeaderKey;
    
    private final String selfHostname = HostName.getLocalhost();

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
    public SearchHandler(
            final ChainsConfig chainsConfig,
            final IndexInfoConfig indexInfo,
            final QrSearchersConfig clusters,
            final SpecialtokensConfig specialtokens,
            final Statistics statistics,
            final Linguistics linguistics,
            final Metric metric,
            final ComponentRegistry<Renderer> renderers,
            final Executor executor,
            final AccessLog accessLog,
            final QueryProfilesConfig queryProfileConfig,
            final ComponentRegistry<Searcher> searchers,
            final ContainerHttpConfig containerHttpConfig) {
        super(executor, accessLog, metric, true);
        log.log(LogLevel.DEBUG, "SearchHandler.init " + System.identityHashCode(this));
        searchChainRegistry = new SearchChainRegistry(searchers);
        setupSearchChainRegistry(searchers, chainsConfig);
        indexFacts = new IndexFacts(new IndexModel(indexInfo, clusters));
        indexFacts.freeze();
        specialTokens = new SpecialTokenRegistry(specialtokens);
        rendererRegistry = new RendererRegistry(renderers.allComponents());
        QueryProfileRegistry queryProfileRegistry = QueryProfileConfigurer.createFromConfig(queryProfileConfig);
        this.queryProfileRegistry = queryProfileRegistry.compile();

        this.linguistics = linguistics;
        this.maxThreads = examineExecutor(executor);

        searchConnections = new Value(SEARCH_CONNECTIONS, statistics,
                                      new Value.Parameters().setLogRaw(true).setLogMax(true)
                                              .setLogMean(true).setLogMin(true)
                                              .setNameExtension(true)
                                              .setCallback(new MeanConnections()));
        
        this.hostResponseHeaderKey = containerHttpConfig.hostResponseHeaderKey().equals("") ?
                                     Optional.empty() : Optional.of( containerHttpConfig.hostResponseHeaderKey());
    }

    /** @deprecated use the constructor with ContainerHttpConfig */
    // TODO: Remove on Vespa 7
    @Deprecated
    public SearchHandler(
            final ChainsConfig chainsConfig,
            final IndexInfoConfig indexInfo,
            final QrSearchersConfig clusters,
            final SpecialtokensConfig specialtokens,
            final Statistics statistics,
            final Linguistics linguistics,
            final Metric metric,
            final ComponentRegistry<Renderer> renderers,
            final Executor executor,
            final AccessLog accessLog,
            final QueryProfilesConfig queryProfileConfig,
            final ComponentRegistry<Searcher> searchers) {
        this (chainsConfig, indexInfo, clusters, specialtokens, statistics, linguistics, metric, renderers, executor,
              accessLog, queryProfileConfig, searchers, new ContainerHttpConfig(new ContainerHttpConfig.Builder()));
    }

    /** @deprecated use the constructor without deprecated parameters */
    // TODO: Remove on Vespa 7
    @Deprecated
    public SearchHandler(
            final ChainsConfig chainsConfig,
            final IndexInfoConfig indexInfo,
            final QrSearchersConfig clusters,
            final SpecialtokensConfig specialTokens,
            final QrTemplatesConfig ignored,
            final FreezeDetector ignored2,
            final Statistics statistics,
            final Linguistics linguistics,
            final Metric metric,
            final ComponentRegistry<Renderer> renderers,
            final Executor executor,
            final AccessLog accessLog,
            final QueryProfilesConfig queryProfileConfig,
            final ComponentRegistry<Searcher> searchers) {
        this(chainsConfig, indexInfo, clusters, specialTokens, statistics, linguistics, metric, renderers,
             executor, accessLog, queryProfileConfig, searchers);
    }

    @Override
    protected void destroy() {
        super.destroy();
        rendererRegistry.deconstruct();
    }

    private void setupSearchChainRegistry(ComponentRegistry<Searcher> searchers, ChainsConfig chainsConfig) {
        ChainsModel chainsModel = ChainsModelBuilder.buildFromConfig(chainsConfig);
        ChainsConfigurer.prepareChainRegistry(searchChainRegistry, chainsModel, searchers);
        searchChainRegistry.freeze();
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
            } catch (QueryException e) {
                return (e.getCause() instanceof IllegalArgumentException)
                        ? invalidParameterResponse(request, e)
                        : illegalQueryResponse(request, e);
            } catch (RuntimeException e) { // Make sure we generate a valid response even on unexpected errors
                log.log(Level.WARNING, "Failed handling " + request, e);
                return internalServerErrorResponse(request, e);
            }
        } finally {
            requestsInFlight.decrementAndGet();
        }
    }

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

    @SuppressWarnings("unchecked")
    private HttpResponse errorResponse(HttpRequest request, ErrorMessage errorMessage) {
        Query query = new Query();
        Result result = new Result(query, errorMessage);
        Renderer renderer = getRendererCopy(ComponentSpecification.fromString(request.getProperty("format")));

        result.getTemplating().setRenderer(renderer); // Pre-Vespa 6 Result.getEncoding() expects this TODO: Remove

        return new HttpSearchResponse(getHttpResponseStatus(request, result), result, query, renderer);
    }

    private HttpResponse invalidParameterResponse(HttpRequest request, RuntimeException e) {
        return errorResponse(request, ErrorMessage.createInvalidQueryParameter(Exceptions.toMessageString(e)));
    }

    private HttpResponse illegalQueryResponse(HttpRequest request, RuntimeException e) {
        return errorResponse(request, ErrorMessage.createIllegalQuery(Exceptions.toMessageString(e)));
    }

    private HttpResponse internalServerErrorResponse(HttpRequest request, RuntimeException e) {
        return errorResponse(request, ErrorMessage.createInternalServerError(Exceptions.toMessageString(e)));
    }

    private HttpSearchResponse handleBody(HttpRequest request) {
        // Find query profile
        String queryProfileName = request.getProperty("queryProfile");
        CompiledQueryProfile queryProfile = queryProfileRegistry.findQueryProfile(queryProfileName);
        boolean benchmarkOutput = VespaHeaders.benchmarkOutput(request);

        // Create query
        Query query = new Query(request, queryProfile);

        boolean benchmarkCoverage = VespaHeaders.benchmarkCoverage(benchmarkOutput, request.getJDiscRequest().headers());

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
            result = new Result(
                    query,
                    ErrorMessage.createIllegalQuery("Could not resolve query profile '" + queryProfileName + "'"));
        } else if (searchChain == null) {
            result = new Result(
                    query,
                    ErrorMessage.createInvalidQueryParameter("No search chain named '" + searchChainName + "' was found"));
        } else {
            String pathAndQuery = UriTools.rawRequest(request.getUri());
            result = search(pathAndQuery, query, searchChain, searchChainRegistry);
        }

        Renderer renderer;
        if (result.getTemplating().usesDefaultTemplate()) {
            renderer = toRendererCopy(query.getPresentation().getRenderer());
            result.getTemplating().setRenderer(renderer); // pre-Vespa 6 Result.getEncoding() expects this to be set. TODO: Remove
        }
        else { // somebody explicitly assigned a old style template
            renderer = perRenderingCopy(result.getTemplating().getRenderer());
        }

        // Transform result to response
        HttpSearchResponse response = new HttpSearchResponse(getHttpResponseStatus(request, result), 
                                                             result, query, renderer);
        if (hostResponseHeaderKey.isPresent())
            response.headers().add(hostResponseHeaderKey.get(), selfHostname);

        if (benchmarkOutput)
            VespaHeaders.benchmarkOutput(response.headers(), benchmarkCoverage, response.getTiming(),
                                         response.getHitCounts(), getErrors(result), response.getCoverage());

        return response;
    }

    private static int getErrors(Result result) {
        return result.hits().getErrorHit() == null ? 0 : 1;
    }

    @NonNull
    private Renderer<Result> toRendererCopy(ComponentSpecification format) {
        Renderer<Result> renderer = rendererRegistry.getRenderer(format);
        renderer = perRenderingCopy(renderer);
        return renderer;
    }

    private Tuple2<String, Chain<Searcher>> resolveChain(String explicitChainName) {
        String chainName = explicitChainName;
        if (chainName == null) {
            chainName = defaultSearchChainName;
        }

        Chain<Searcher> searchChain = searchChainRegistry.getChain(chainName);
        if (searchChain == null && explicitChainName == null) { // explicit chain not found should cause error
            chainName = fallbackSearchChain;
            searchChain = searchChainRegistry.getChain(chainName);
        }
        return new Tuple2<>(chainName, searchChain);
    }

    /** Used from container SDK, for internal use only */
    public Result searchAndFill(Query query, Chain<? extends Searcher> searchChain, SearchChainRegistry registry) {
        Result errorResult = validateQuery(query);
        if (errorResult != null) return errorResult;

        Renderer<Result> renderer = rendererRegistry.getRenderer(query.getPresentation().getRenderer());

        // docsumClass null means "unset", so we set it (it might be null
        // here too in which case it will still be "unset" after we set it :-)
        if (query.getPresentation().getSummary() == null && renderer instanceof com.yahoo.search.rendering.Renderer)
            query.getPresentation().setSummary(((com.yahoo.search.rendering.Renderer) renderer).getDefaultSummaryClass());

        Execution execution = new Execution(searchChain,
                                            new Execution.Context(registry, indexFacts, specialTokens, rendererRegistry, linguistics));
        query.getModel().setExecution(execution);
        execution.trace().setForceTimestamps(query.properties().getBoolean(FORCE_TIMESTAMPS, false));
        if (query.properties().getBoolean(DETAILED_TIMING_LOGGING, false)) {
            // check and set (instead of set directly) to avoid overwriting stuff from prepareForBreakdownAnalysis()
            execution.context().setDetailedDiagnostics(true);
        }
        Result result = execution.search(query);

        if (result.getTemplating() == null)
            result.getTemplating().setRenderer(renderer);

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

    /**
     * For internal use only
     * 
     * @deprecated remove on Vespa 7
     */
    @Deprecated
    public Renderer<Result> getRendererCopy(ComponentSpecification spec) { // TODO: Deprecate this
        Renderer<Result> renderer = rendererRegistry.getRenderer(spec);
        return perRenderingCopy(renderer);
    }

    @NonNull
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

    private Result search(String request, Query query, Chain<Searcher> searchChain, SearchChainRegistry registry) {
        if (query.getTraceLevel() >= 2) {
            query.trace("Invoking " + searchChain, false, 2);
        }

        if (searchConnections != null) {
            connectionStatistics();
        } else {
            log.log(LogLevel.WARNING,
                    "searchConnections is a null reference, probably a known race condition during startup.",
                    new IllegalStateException("searchConnections reference is null."));
        }
        try {
            return searchAndFill(query, searchChain, registry);
        } catch (ParseException e) {
            ErrorMessage error = ErrorMessage.createIllegalQuery("Could not parse query [" + request + "]: "
                                                                 + Exceptions.toMessageString(e));
            log.log(LogLevel.DEBUG, () -> error.getDetailedMessage());
            return new Result(query, error);
        } catch (IllegalArgumentException e) {
            ErrorMessage error = ErrorMessage.createBadRequest("Invalid search request [" + request + "]: "
                                                               + Exceptions.toMessageString(e));
            log.log(LogLevel.DEBUG, () -> error.getDetailedMessage());
            return new Result(query, error);
        } catch (LinkageError e) {
            // Should have been an Exception in an OSGi world - typical bundle dependency issue problem
            ErrorMessage error = ErrorMessage.createErrorInPluginSearcher(
                            "Error executing " + searchChain + "]: " + Exceptions.toMessageString(e), e);
            log(request, query, e);
            return new Result(query, error);
        } catch (StackOverflowError e) { // Also recoverable
            ErrorMessage error = ErrorMessage.createErrorInPluginSearcher(
                            "Error executing " + searchChain + "]: " + Exceptions.toMessageString(e), e);
            log(request, query, e);
            return new Result(query, error);
        } catch (Exception e) {
            Result result = new Result(query);
            log(request, query, e);
            result.hits().addError(
                    ErrorMessage.createUnspecifiedError("Failed searching: " + Exceptions.toMessageString(e), e));
            return result;
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
            log.log(LogLevel.ERROR,
                    "Failed executing " + query.toDetailString() + " [" + request
                            + "], received exception with no context", e);
        } else {
            log.log(LogLevel.ERROR,
                    "Failed executing " + query.toDetailString() + " [" + request + "]", e);
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

    public SearchChainRegistry getSearchChainRegistry() {
        return searchChainRegistry;
    }

}
