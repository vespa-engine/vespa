// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cluster;

import com.yahoo.collections.TinyIdentitySet;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.documentapi.VespaDocumentAccess;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.IndexedBackend;
import com.yahoo.prelude.fastsearch.VespaBackend;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.query.ParameterParser;
import com.yahoo.search.query.ranking.SecondPhase;
import com.yahoo.search.ranking.GlobalPhaseRanker;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.schema.Cluster;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vespa.streamingvisitors.StreamingBackend;
import com.yahoo.yolean.Exceptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * A searcher which forwards to a cluster of monitored native Vespa backends.
 *
 * @author bratseth
 * @author Steinar Knutsen
 * @author geirst
 */
@After("*")
public class ClusterSearcher extends Searcher {

    private final static long DEFAULT_MAX_QUERY_TIMEOUT = 600000L;
    private final static long DEFAULT_MAX_QUERY_CACHE_TIMEOUT = 10000L;

    private final String searchClusterName;

    // The set of document types contained in this search cluster
    private final Map<String, VespaBackend> schema2Searcher;
    private final SchemaInfo schemaInfo;

    private final long maxQueryTimeout; // in milliseconds
    private final long maxQueryCacheTimeout; // in milliseconds

    private final Executor executor;
    private final GlobalPhaseRanker globalPhaseRanker;

    @Inject
    public ClusterSearcher(ComponentId id,
                           Executor executor,
                           ClusterConfig clusterConfig,
                           DocumentdbInfoConfig documentDbConfig,
                           SchemaInfo schemaInfo,
                           QrSearchersConfig qrSearchersConfig,
                           ComponentRegistry<Dispatcher> dispatchers,
                           GlobalPhaseRanker globalPhaseRanker,
                           VipStatus vipStatus,
                           VespaDocumentAccess access) {
        super(id);
        this.executor = executor;
        this.schemaInfo = schemaInfo;
        searchClusterName = clusterConfig.clusterName();
        this.globalPhaseRanker = globalPhaseRanker;
        schema2Searcher = new LinkedHashMap<>();

        maxQueryTimeout = ParameterParser.asMilliSeconds(clusterConfig.maxQueryTimeout(), DEFAULT_MAX_QUERY_TIMEOUT);
        maxQueryCacheTimeout = ParameterParser.asMilliSeconds(clusterConfig.maxQueryCacheTimeout(), DEFAULT_MAX_QUERY_CACHE_TIMEOUT);

        VespaBackend streaming = null, indexed = null;
        ClusterParams clusterParams = makeClusterParams(searchClusterName, documentDbConfig, schemaInfo, qrSearchersConfig);
        for (DocumentdbInfoConfig.Documentdb docDb : documentDbConfig.documentdb()) {
            if (docDb.mode() == DocumentdbInfoConfig.Documentdb.Mode.Enum.INDEX) {
                if (indexed == null) {
                    indexed = searchDispatch(clusterParams, searchClusterName, dispatchers);
                }
                schema2Searcher.put(docDb.name(), indexed);
            } else if (docDb.mode() == DocumentdbInfoConfig.Documentdb.Mode.Enum.STREAMING) {
                if (streaming == null) {
                    streaming = streamingCluster(clusterParams, clusterConfig, access);
                    vipStatus.addToRotation(streaming.getName());
                }
                schema2Searcher.put(docDb.name(), streaming);
            }
        }
    }

    private static ClusterParams makeClusterParams(String searchclusterName,
                                                   DocumentdbInfoConfig documentDbConfig,
                                                   SchemaInfo schemaInfo,
                                                   QrSearchersConfig qrSearchersConfig) {
        return new ClusterParams(searchclusterName, UUID.randomUUID().toString(),
                                 null, documentDbConfig, schemaInfo, qrSearchersConfig);
    }

    private static IndexedBackend searchDispatch(ClusterParams clusterParams,
                                                 String searchClusterName,
                                                 ComponentRegistry<Dispatcher> dispatchers) {
        ComponentId dispatcherComponentId = new ComponentId("dispatcher." + searchClusterName);
        Dispatcher dispatcher = dispatchers.getComponent(dispatcherComponentId);
        if (dispatcher == null)
            throw new IllegalArgumentException("Configuration error: No dispatcher " + dispatcherComponentId + " is configured");
        return new IndexedBackend(clusterParams, dispatcher);
    }

    private static StreamingBackend streamingCluster(ClusterParams clusterParams,
                                                     ClusterConfig clusterConfig,
                                                     VespaDocumentAccess access) {
        return new StreamingBackend(clusterParams, clusterConfig.configid(),
                                    access, clusterConfig.storageRoute());
    }

    /** Do not use, for internal testing purposes only. **/
    ClusterSearcher(SchemaInfo schemaInfo, Map<String, VespaBackend> schema2Searcher, Executor executor) {
        this.schemaInfo = schemaInfo;
        searchClusterName = "testScenario";
        maxQueryTimeout = DEFAULT_MAX_QUERY_TIMEOUT;
        maxQueryCacheTimeout = DEFAULT_MAX_QUERY_CACHE_TIMEOUT;
        this.executor = executor;
        this.globalPhaseRanker = null;
        this.schema2Searcher = schema2Searcher;
    }

    /** Do not use, for internal testing purposes only. **/
    ClusterSearcher(SchemaInfo schemaInfo, Map<String, VespaBackend> schema2Searcher) {
        this(schemaInfo, schema2Searcher, null);
    }

    @Override
    public Result search(Query query, Execution execution) {
        validateQueryTimeout(query);
        validateQueryCache(query);
        if (schema2Searcher.isEmpty()) {
            return new Result(query, ErrorMessage.createNoBackendsInService("Could not search"));
        }
        if (query.getTimeLeft() <= 0) {
            return new Result(query, ErrorMessage.createTimeout("No time left for searching"));
        }

        return doSearch(query);
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        fill(result, summaryClass);
    }
    private void fill(Result result, String summaryClass) {
        Query query = result.getQuery();
        var restrict = query.getModel().getRestrict();
        Collection<VespaBackend> servers = (restrict != null && ! restrict.isEmpty())
                ? query.getModel().getRestrict().stream()
                    .map(schema2Searcher::get)
                    .collect(Collectors.toCollection(TinyIdentitySet::new))
                : schema2Searcher.values().stream().collect(Collectors.toCollection(TinyIdentitySet::new));

        if ( ! servers.isEmpty() ) {
            for (var server : servers) {
                if (query.getTimeLeft() > 0) {
                    server.fill(result, summaryClass);
                } else {
                    if (result.hits().getErrorHit() == null) {
                        result.hits().addError(ErrorMessage.createTimeout("No time left to get summaries, query timeout was " +
                                                                          query.getTimeout() + " ms"));
                    }
                }
            }
        } else {
            if (result.hits().getErrorHit() == null) {
                result.hits().addError(ErrorMessage.createNoBackendsInService("Could not fill result"));
            }
        }
    }

    private void validateQueryTimeout(Query query) {
        if (query.getTimeout() <= maxQueryTimeout) return;

        if (query.getTrace().isTraceable(2)) {
            query.trace("Query timeout (" + query.getTimeout() + " ms) > max query timeout (" +
                        maxQueryTimeout + " ms). Setting timeout to " + maxQueryTimeout + " ms.", 2);
        }
        query.setTimeout(maxQueryTimeout);
    }

    private void validateQueryCache(Query query) {
        if ( ! query.getRanking().getQueryCache() ) return;
        if (query.getTimeout() <= maxQueryCacheTimeout) return;

        if (query.getTrace().isTraceable(2)) {
            query.trace("Query timeout (" + query.getTimeout() + " ms) > max query cache timeout (" +
                        maxQueryCacheTimeout + " ms). Disabling query cache.", 2);
        }
        query.getRanking().setQueryCache(false);
    }

    private Result doSearch(Query query) {
        if (schema2Searcher.size() > 1) {
            return searchMultipleDocumentTypes(query);
        } else {
            String schema = schema2Searcher.keySet().iterator().next();
            query.getModel().setRestrict(schema);
            return perSchemaSearch(schema, query);
        }
    }

    // TODO: Make this a search chain
    private Result perSchemaSearch(String schema, Query query) {
        if (query.getModel().getRestrict().size() != 1) {
            throw new IllegalStateException("perSchemaSearch must always be called with 1 schema, got: " +
                                            query.getModel().getRestrict());
        }

        transferRerankCounts(query, schemaInfo);

        int rerankCount = globalPhaseRanker != null ? globalPhaseRanker.getRerankCount(query, schema) : 0;
        boolean useGlobalPhase = rerankCount > 0;
        final int wantOffset = query.getOffset();
        final int wantHits = query.getHits();
        if (useGlobalPhase) {
            var error = globalPhaseRanker.validateNoSorting(query, schema).orElse(null);
            if (error != null) return new Result(query, error);
            int useHits = Math.max(wantOffset + wantHits, rerankCount);
            query.setOffset(0);
            query.setHits(useHits);
        }
        Result result = schema2Searcher.get(schema).search(schema, query);
        if (useGlobalPhase) {
            if (query.getTrace().isTraceable(3)) {
                query.trace("Use global-phase from [" + schema + "] to re-rank " + rerankCount + " hits", 3);
            }
            globalPhaseRanker.rerankHits(query, result, schema);
            result.hits().trim(wantOffset, wantHits);
            query.setOffset(wantOffset);
            query.setHits(wantHits);
        }
        return result;
    }

    // Transfer second-phase rerankCount/totalRerankCount
    public static void transferRerankCounts(Query query, SchemaInfo schemaInfo) {
        if (query.getModel().getRestrict().size() != 1) {
            throw new IllegalStateException("perSchemaSearch must always be called with 1 schema, got: " +
                                            query.getModel().getRestrict());
        }
        OptionalInt rerankCount = asOptional(query.getRanking().getSecondPhase().getRerankCount());
        OptionalInt totalRerankCount = asOptional(query.getRanking().getSecondPhase().getTotalRerankCount());
        if (rerankCount.isEmpty() && totalRerankCount.isEmpty()) { // fall back to rank profile defaults
            String schemaName = query.getModel().getRestrict().iterator().next();
            var schema = schemaInfo.newSession(query).schema(schemaName);
            if (schema.isPresent()) {
                var profile = schema.get().rankProfiles().get(query.getRanking().getProfile());
                if (profile != null) {
                    rerankCount = profile.secondPhase().rerankCount();
                    totalRerankCount = profile.secondPhase().totalRerankCount();
                }
            }
        }
        rerankCount.ifPresent(count -> query.getRanking().getProperties().put(SecondPhase.rerankCountProperty, count));
        totalRerankCount.ifPresent(count -> query.getRanking().getProperties().put(SecondPhase.totalRerankCountProperty, count));
    }

    private static OptionalInt asOptional(Integer nullable) {
        return nullable == null ? OptionalInt.empty() : OptionalInt.of(nullable);
    }

    private static void processResult(Query query, FutureTask<Result> task, Result mergedResult) {
        try {
            Result result = task.get();
            mergedResult.mergeWith(result);
            mergedResult.hits().addAll(result.hits().asUnorderedHits());
        } catch (ExecutionException e) {
            mergedResult.hits().addError(ErrorMessage.createInternalServerError("Failed querying '" +
                                                                                query.getModel().getRestrict() + "': " +
                                                                                Exceptions.toMessageString(e),
                                                                                e));
        } catch (InterruptedException e) {
            mergedResult.hits().addError(ErrorMessage.createInternalServerError("Failed querying '" +
                                                                                query.getModel().getRestrict() + "': " +
                                                                                Exceptions.toMessageString(e)));
        }
    }

    private Result searchMultipleDocumentTypes(Query query) {
        Set<String> schemas = resolveSchemas(query);
        Map<String, Query> schemaQueries = createQueries(query, schemas);
        if (schemaQueries.size() == 1) {
            var entry = schemaQueries.entrySet().iterator().next();
            return perSchemaSearch(entry.getKey(), entry.getValue());
        } else {
            Result mergedResult = new Result(query);
            List<FutureTask<Result>> pending = new ArrayList<>(schemaQueries.size());
            for (var entry : schemaQueries.entrySet()) {
                FutureTask<Result> task = new FutureTask<>(() -> perSchemaSearch(entry.getKey(), entry.getValue()));
                try {
                    executor.execute(task);
                    pending.add(task);
                } catch (RejectedExecutionException rej) {
                    task.run();
                    processResult(query, task, mergedResult);
                }
            }
            for (FutureTask<Result> task : pending) {
                processResult(query, task, mergedResult);
            }
            // Should we trim the merged result?
            if (query.getOffset() > 0 || query.getHits() < mergedResult.hits().size()) {
                if (mergedResult.getHitOrderer() != null) {
                    // Make sure we have the necessary data for sorting
                    fill(mergedResult, VespaBackend.SORTABLE_ATTRIBUTES_SUMMARY_CLASS);
                }
                mergedResult.hits().trim(query.getOffset(), query.getHits());
                query.setOffset(0); // Needed when doing a trim
            }
            return mergedResult;
        }
    }

    private Set<String> resolveSourceSubset(Set<String> sources) {
        Set<String> candidates = new HashSet<>();
        for (String source : sources) {
            Cluster cluster = schemaInfo.clusters().get(source);
            if (cluster != null)
                candidates.addAll(cluster.schemas());
        }
        return (candidates.isEmpty() ? sources : candidates).stream()
                .filter(schema2Searcher::containsKey).collect(Collectors.toUnmodifiableSet());
    }

    Set<String> resolveSchemas(Query query) {
        Set<String> restrict = query.getModel().getRestrict();
        if (restrict == null || restrict.isEmpty()) {
            Set<String> sources = query.getModel().getSources();
            return (sources == null || sources.isEmpty())
                    ? schema2Searcher.keySet()
                    : resolveSourceSubset(sources);
        } else {
            return filterValidDocumentTypes(restrict);
        }
    }

    private Set<String> filterValidDocumentTypes(Collection<String> restrict) {
        Set<String> retval = new LinkedHashSet<>();
        for (String docType : restrict) {
            if (docType != null && schema2Searcher.containsKey(docType)) {
                retval.add(docType);
            }
        }
        return retval;
    }

    private Map<String, Query> createQueries(Query query, Set<String> schemas) {
        query.getModel().getQueryTree(); // performance: parse query before cloning such that it is only done once
        if (schemas.size() == 1) {
            String schema = schemas.iterator().next();
            query.getModel().setRestrict(schema);
            return Map.of(schema, query);
        } else if ( ! schemas.isEmpty() ) {
            var schemaQueries = new HashMap<String, Query>();
            for (String schema : schemas) {
                Query q = query.clone();
                q.setOffset(0);
                q.setHits(query.getOffset() + query.getHits());
                q.getModel().setRestrict(schema);
                schemaQueries.put(schema, q);
            }
            return schemaQueries;
        }
        return Map.of();
    }

    @Override
    public void deconstruct() {
        Map<String, VespaBackend> servers = new HashMap<>();
        for (var server : schema2Searcher.values()) {
            servers.put(server.getName(), server);
        }
        for (var server : servers.values()) {
            server.shutDown();
        }
    }

}
