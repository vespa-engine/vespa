// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cluster;

import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.collections.Tuple2;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.concurrent.Receiver;
import com.yahoo.concurrent.Receiver.MessageState;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.fs4.mplex.Backend;
import com.yahoo.container.search.LegacyEmulationConfig;
import com.yahoo.net.HostName;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.prelude.fastsearch.CacheControl;
import com.yahoo.prelude.fastsearch.CacheParams;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.FastSearcher;
import com.yahoo.prelude.fastsearch.SummaryParameters;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.query.ParameterParser;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.statistics.Statistics;
import com.yahoo.statistics.Value;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.streamingvisitors.VdsStreamingSearcher;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;

import static com.yahoo.container.QrSearchersConfig.Searchcluster.Indexingmode.STREAMING;

/**
 * A searcher which forwards to a cluster of monitored native Vespa backends.
 *
 * @author bratseth
 * @author Steinar Knutsen
 * @author geirst
 */
@After("*")
public class ClusterSearcher extends Searcher {

    private final static Logger log = Logger.getLogger(ClusterSearcher.class.getName());

    private final ClusterMonitor monitor;

    private final Value cacheHitRatio;

    private final String clusterModelName;

    // The set of document types contained in this search cluster
    private final Set<String> documentTypes;

    // Mapping from rank profile names to document types containing them
    private final Map<String, Set<String>> rankProfiles = new HashMap<>();

    private final boolean failoverToRemote;

    private final FS4ResourcePool fs4ResourcePool;

    private final long maxQueryTimeout; // in milliseconds
    private final static long DEFAULT_MAX_QUERY_TIMEOUT = 600000L;

    private final long maxQueryCacheTimeout; // in milliseconds
    private final static long DEFAULT_MAX_QUERY_CACHE_TIMEOUT = 10000L;

    private VespaBackEndSearcher server = null;


    /**
     * Creates a new ClusterSearcher.
     */
    public ClusterSearcher(ComponentId id,
                           QrSearchersConfig qrsConfig,
                           ClusterConfig clusterConfig,
                           DocumentdbInfoConfig documentDbConfig,
                           LegacyEmulationConfig emulationConfig,
                           QrMonitorConfig monitorConfig,
                           DispatchConfig dispatchConfig,
                           ClusterInfoConfig clusterInfoConfig,
                           Statistics manager,
                           FS4ResourcePool fs4ResourcePool,
                           VipStatus vipStatus) {
        super(id);
        this.fs4ResourcePool = fs4ResourcePool;

        Dispatcher dispatcher = new Dispatcher(dispatchConfig, fs4ResourcePool, clusterInfoConfig.nodeCount(), vipStatus);

        monitor = (dispatcher.searchCluster().directDispatchTarget().isPresent()) // dispatcher should decide vip status instead
                ? new ClusterMonitor(this, monitorConfig, Optional.empty())
                : new ClusterMonitor(this, monitorConfig, Optional.of(vipStatus));

        int searchClusterIndex = clusterConfig.clusterId();
        clusterModelName = clusterConfig.clusterName();
        QrSearchersConfig.Searchcluster searchClusterConfig = getSearchClusterConfigFromClusterName(qrsConfig, clusterModelName);
        documentTypes = new LinkedHashSet<>();
        failoverToRemote = clusterConfig.failoverToRemote();

        String eventName = clusterModelName + ".cache_hit_ratio";
        cacheHitRatio = new Value(eventName, manager, new Value.Parameters().setNameExtension(false)
                                                                            .setLogRaw(false).setLogMean(true));

        maxQueryTimeout = ParameterParser.asMilliSeconds(clusterConfig.maxQueryTimeout(), DEFAULT_MAX_QUERY_TIMEOUT);
        maxQueryCacheTimeout = ParameterParser.asMilliSeconds(clusterConfig.maxQueryCacheTimeout(),
                                                              DEFAULT_MAX_QUERY_CACHE_TIMEOUT);

        CacheParams cacheParams = new CacheParams(createCache(clusterConfig, clusterModelName));
        SummaryParameters docSumParams = new SummaryParameters(qrsConfig
                .com().yahoo().prelude().fastsearch().FastSearcher().docsum()
                .defaultclass());
        
        for (DocumentdbInfoConfig.Documentdb docDb : documentDbConfig.documentdb()) {
            String docTypeName = docDb.name();
            documentTypes.add(docTypeName);

            for (DocumentdbInfoConfig.Documentdb.Rankprofile profile : docDb.rankprofile()) {
                addValidRankProfile(profile.name(), docTypeName);
            }
        }

        if (searchClusterConfig.indexingmode() == STREAMING) {
            VdsStreamingSearcher searcher = vdsCluster(searchClusterIndex,
                                                       searchClusterConfig, cacheParams, emulationConfig, docSumParams,
                                                       documentDbConfig);
            addBackendSearcher(searcher);
        } else {
            for (int dispatcherIndex = 0; dispatcherIndex < searchClusterConfig.dispatcher().size(); dispatcherIndex++) {
                try {
                    if (! isRemote(searchClusterConfig.dispatcher(dispatcherIndex).host())) {
                        Backend b = createBackend(searchClusterConfig.dispatcher(dispatcherIndex));
                        FastSearcher searcher = searchDispatch(searchClusterIndex, fs4ResourcePool,
                                searchClusterConfig, cacheParams, emulationConfig, docSumParams,
                                documentDbConfig, b, dispatcher, dispatcherIndex);
                        addBackendSearcher(searcher);
                    }
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if ( server == null ) {
            log.log(Level.SEVERE, "ClusterSearcher should have a top level dispatch.");
        }
        monitor.freeze();
        monitor.startPingThread();
    }

    private static QrSearchersConfig.Searchcluster getSearchClusterConfigFromClusterName(QrSearchersConfig config, String name) {
        for (QrSearchersConfig.Searchcluster searchCluster : config.searchcluster()) {
            if (searchCluster.name().equals(name)) {
                return searchCluster;
            }
        }
        return null;
    }

    /**
     * Returns false if this host is local.
     */
    boolean isRemote(String host) throws UnknownHostException {
        return (InetAddress.getByName(host).isLoopbackAddress())
                ? false
                : !host.equals(HostName.getLocalhost());
    }

    private static ClusterParams makeClusterParams(int searchclusterIndex,
                                                   QrSearchersConfig.Searchcluster searchClusterConfig,
                                                   LegacyEmulationConfig emulConfig,
                                                   int dispatchIndex) {
        return new ClusterParams(searchclusterIndex,
                                 "sc" + searchclusterIndex + ".num" + dispatchIndex,
                                 searchClusterConfig.rowbits(),
                                 emulConfig);
    }

    private static FastSearcher searchDispatch(int searchclusterIndex,
                                               FS4ResourcePool fs4ResourcePool,
                                               QrSearchersConfig.Searchcluster searchClusterConfig,
                                               CacheParams cacheParams,
                                               LegacyEmulationConfig emulConfig,
                                               SummaryParameters docSumParams,
                                               DocumentdbInfoConfig documentdbInfoConfig,
                                               Backend backend,
                                               Dispatcher dispatcher,
                                               int dispatcherIndex) {
        ClusterParams clusterParams = makeClusterParams(searchclusterIndex, searchClusterConfig,
                                                        emulConfig, dispatcherIndex);
        return new FastSearcher(backend, fs4ResourcePool, dispatcher, docSumParams, clusterParams, cacheParams, 
                                documentdbInfoConfig);
    }

    private static VdsStreamingSearcher vdsCluster(int searchclusterIndex,
                                                   QrSearchersConfig.Searchcluster searchClusterConfig,
                                                   CacheParams cacheParams,
                                                   LegacyEmulationConfig emulConfig,
                                                   SummaryParameters docSumParams,
                                                   DocumentdbInfoConfig documentdbInfoConfig) {
        ClusterParams clusterParams = makeClusterParams(searchclusterIndex, searchClusterConfig, emulConfig, 0);
        VdsStreamingSearcher searcher = (VdsStreamingSearcher) VespaBackEndSearcher
                .getSearcher("com.yahoo.vespa.streamingvisitors.VdsStreamingSearcher");
        searcher.setSearchClusterConfigId(searchClusterConfig.rankprofiles().configid());
        searcher.setStorageClusterRouteSpec(searchClusterConfig.storagecluster().routespec());
        searcher.init(docSumParams, clusterParams, cacheParams, documentdbInfoConfig);
        return searcher;
    }

    /** Do not use, for internal testing purposes only. **/
    ClusterSearcher(Set<String> documentTypes) {
        this.failoverToRemote = false;
        this.documentTypes = documentTypes;
        monitor = new ClusterMonitor(this, new QrMonitorConfig(new QrMonitorConfig.Builder()), Optional.of(new VipStatus()));
        cacheHitRatio = new Value("com.yahoo.prelude.cluster.ClusterSearcher.ClusterSearcher().dummy",
                                  Statistics.nullImplementation, new Value.Parameters());
        clusterModelName = "testScenario";
        fs4ResourcePool = null;
        maxQueryTimeout = DEFAULT_MAX_QUERY_TIMEOUT;
        maxQueryCacheTimeout = DEFAULT_MAX_QUERY_CACHE_TIMEOUT;
    }

    private Backend createBackend(QrSearchersConfig.Searchcluster.Dispatcher disp) {
        return fs4ResourcePool.getBackend(disp.host(), disp.port());
    }

    private static CacheControl createCache(ClusterConfig config, String clusterModelName) {
        log.log(Level.INFO, "Enabling cache for search cluster "
                            + clusterModelName + " (size=" + config.cacheSize()
                            + ", timeout=" + config.cacheTimeout() + ")");

        return new CacheControl(config.cacheSize(), config.cacheTimeout());
    }

    public String getClusterModelName() {
        return clusterModelName;
    }

    ClusterMonitor getMonitor() {
        return monitor;
    }

    void addBackendSearcher(VespaBackEndSearcher searcher) {
        monitor.add(searcher);
        server = searcher;
    }

    void addValidRankProfile(String profileName, String docTypeName) {
        if (!rankProfiles.containsKey(profileName)) {
            rankProfiles.put(profileName, new HashSet<>());
        }
        rankProfiles.get(profileName).add(docTypeName);
    }

    void setValidRankProfile(String profileName, Set<String> documentTypes) {
        rankProfiles.put(profileName, documentTypes);
    }

    /**
     * Returns an error if the document types do not have the requested rank
     * profile. For the case of multiple document types, only returns an
     * error if we have restricted the set of documents somehow. This is
     * because when searching over all doc types, common ancestors might
     * not have the requested rank profile and failing on that basis is
     * probably not reasonable.
     *
     * @param  query    query
     * @param  docTypes set of requested doc types for this query
     * @return          null if request rank profile is ok for the requested
     *                  doc types, a result with error message if not.
     */
    // TODO: This should be in a separate searcher
    private Result checkValidRankProfiles(Query query, Set<String> docTypes) {
        String rankProfile = query.getRanking().getProfile();
        Set<String> invalidInDocTypes = null;
        Set<String> rankDocTypes = rankProfiles.get(rankProfile);

        if (rankDocTypes == null) {
            // ranking profile does not exist in any document type
            invalidInDocTypes = docTypes;
        }
        else if (docTypes.size() == 1) {
            // one document type, fails if invalid rank profile
            if (!rankDocTypes.contains(docTypes.iterator().next())) {
                invalidInDocTypes = docTypes;
            }
        }
        else {
            // multiple document types, only fail when restricting doc types
            Set<String> restrict = query.getModel().getRestrict();
            Set<String> sources = query.getModel().getSources();
            boolean validate = restrict != null && !restrict.isEmpty();
            validate = validate || sources != null && !sources.isEmpty();
            if (validate && !rankDocTypes.containsAll(docTypes)) {
                invalidInDocTypes = new HashSet<>(docTypes);
                invalidInDocTypes.removeAll(rankDocTypes);
            }
        }

        if (invalidInDocTypes != null && !invalidInDocTypes.isEmpty()) {
            String plural = invalidInDocTypes.size() > 1 ? "s" : "";
            return new Result(query, 
                              ErrorMessage.createInvalidQueryParameter("Requested rank profile '" + rankProfile +
                                                                       "' is undefined for document type" + plural + " '" +
                                                                       StringUtils.join(invalidInDocTypes.iterator(), ", ") + "'"));
        }

        return null;
    }

    @Override
    public void fill(com.yahoo.search.Result result, String summaryClass, Execution execution) {
        Query query = result.getQuery();

        VespaBackEndSearcher searcher = server;
        if (searcher != null) {
            if (query.getTimeLeft() > 0) {
                doFill(searcher, result, summaryClass, execution);
            } else {
                if (result.hits().getErrorHit() == null) {
                    result.hits().addError(ErrorMessage.createTimeout("No time left to get summaries"));
                }
            }
        } else {
            if (result.hits().getErrorHit() == null) {
                result.hits().addError(ErrorMessage.createNoBackendsInService("Could not fill result"));
            }
        }
    }

    public void doFill(Searcher searcher, Result result, String summaryClass, Execution execution) {
        searcher.fill(result, summaryClass, execution);
        updateCacheHitRatio(result, result.getQuery());
    }

    private void updateCacheHitRatio(Result result, Query query) {
        // result.isCached() looks at the contained hits, so if there are no
        // hits, the result will be treated as cached, even though the backend was queried.
        if (result.hits().getError() == null && result.hits().getConcreteSize() > 0) {
            if (result.isCached()) {
                cacheHit();
            } else if (!query.getNoCache()) {
                cacheMiss();
            }
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        validateQueryTimeout(query);
        validateQueryCache(query);
        VespaBackEndSearcher searcher = server;
        if (searcher == null) {
            return new Result(query, ErrorMessage.createNoBackendsInService("Could not search"));
        }
        if (query.getTimeLeft() <= 0) {
            return new Result(query, ErrorMessage.createTimeout("No time left for searching"));
        }

        return doSearch(searcher, query, execution);
    }

    private void validateQueryTimeout(Query query) {
        if (query.getTimeout() <= maxQueryTimeout) return;

        if (query.isTraceable(2)) {
            query.trace("Query timeout (" + query.getTimeout() + " ms) > max query timeout (" + 
                        maxQueryTimeout + " ms). Setting timeout to " + maxQueryTimeout + " ms.", 2);
        }
        query.setTimeout(maxQueryTimeout);
    }

    private void validateQueryCache(Query query) {
        if ( ! query.getRanking().getQueryCache() ) return;
        if (query.getTimeout() <= maxQueryCacheTimeout) return;

        if (query.isTraceable(2)) {
            query.trace("Query timeout (" + query.getTimeout() + " ms) > max query cache timeout (" +
                        maxQueryCacheTimeout + " ms). Disabling query cache.", 2);
        }
        query.getRanking().setQueryCache(false);
    }

    private Result doSearch(Searcher searcher, Query query, Execution execution) {
        Result result;
        if (documentTypes.size() > 1) {
            result = searchMultipleDocumentTypes(searcher, query, execution);
        } else {
            String docType = documentTypes.iterator().next();

            Result invalidRankProfile = checkValidRankProfiles(query, documentTypes);
            if (invalidRankProfile != null) {
                return invalidRankProfile;
            }

            query.getModel().setRestrict(docType);
            result = searcher.search(query, execution);
        }
        updateCacheHitRatio(result, query);
        return result;
    }


    private Result searchMultipleDocumentTypes(Searcher searcher, Query query, Execution execution) {
        Set<String> docTypes = resolveDocumentTypes(query, execution.context().getIndexFacts());

        Result invalidRankProfile = checkValidRankProfiles(query, docTypes);
        if (invalidRankProfile != null) {
            return invalidRankProfile;
        }

        List<Query> queries = createQueries(query, docTypes);
        if (queries.size() == 1) {
            return searcher.search(queries.get(0), execution);
        } else {
            Result mergedResult = new Result(query.clone());
            for (Query q : queries) {
                Result result = searcher.search(q, execution);
                mergedResult.mergeWith(result);
                mergedResult.hits().addAll(result.hits().asUnorderedHits());
            }
            // Should we trim the merged result?
            if (query.getOffset() > 0 || query.getHits() < mergedResult.hits().size()) {
                if (mergedResult.getHitOrderer() != null) {
                    // Make sure we have the necessary data for sorting
                    searcher.fill(mergedResult, Execution.ATTRIBUTEPREFETCH, execution);
                }
                mergedResult.hits().trim(query.getOffset(), query.getHits());
            }
            return mergedResult;
        }
    }

    Set<String> resolveDocumentTypes(Query query, IndexFacts indexFacts) {
        Set<String> restrict = query.getModel().getRestrict();
        if (restrict == null || restrict.isEmpty()) {
            Set<String> sources = query.getModel().getSources();
            return (sources == null || sources.isEmpty())
                    ? documentTypes
                    : new HashSet<>(indexFacts.newSession(sources, Collections.emptyList(), documentTypes).documentTypes());
        } else {
            return filterValidDocumentTypes(restrict);
        }
    }

    private Set<String> filterValidDocumentTypes(Collection<String> restrict) {
        Set<String> retval = new LinkedHashSet<>();
        for (String docType : restrict) {
            if (docType != null && documentTypes.contains(docType)) {
                retval.add(docType);
            }
        }
        return retval;
    }

    private List<Query> createQueries(Query query, Set<String> docTypes) {
        List<Query> retval = new ArrayList<>(docTypes.size());
        if (docTypes.size() == 1) {
            query.getModel().setRestrict(docTypes.iterator().next());
            retval.add(query);
        } else if ( ! docTypes.isEmpty() ) {
            for (String docType : docTypes) {
                Query q = query.clone();
                q.setOffset(0);
                q.setHits(query.getOffset() + query.getHits());
                q.getModel().setRestrict(docType);
                retval.add(q);
            }
        }
        return retval;
    }

    private void cacheHit() {
        cacheHitRatio.put(1.0);
    }

    private void cacheMiss() {
        cacheHitRatio.put(0.0);
    }

    /** NodeManager method, called from ClusterMonitor. */
    void working(VespaBackEndSearcher node) {
        server = node;
    }

    /** Called from ClusterMonitor. */
    void failed(VespaBackEndSearcher node) {
        server = null;
    }

    /**
     * Pinging a node, called from ClusterMonitor.
     */
    void ping(VespaBackEndSearcher node) throws InterruptedException {
        log.fine("Sending ping to: " + node);
        Pinger pinger = new Pinger(node);

        getExecutor().execute(pinger);
        Pong pong = pinger.getPong(); // handles timeout
        if (pong == null) {
            monitor.failed(node, ErrorMessage.createNoAnswerWhenPingingNode("Ping thread timed out."));
        } else if (pong.badResponse()) {
            monitor.failed(node, pong.getError(0));
        } else {
            monitor.responded(node, backendCanServeDocuments(pong));
        }
    }

    private boolean backendCanServeDocuments(Pong pong) {
        if ( ! pong.activeNodes().isPresent()) return true; // no information; assume true
        return pong.activeNodes().get() > 0;
    }

    @Override
    public void deconstruct() {
        monitor.shutdown();
    }

    ExecutorService getExecutor() {
        return fs4ResourcePool.getExecutor();
    }

    ScheduledExecutorService getScheduledExecutor() {
        return fs4ResourcePool.getScheduledExecutor();
    }

    private class Pinger implements Runnable {

        private final Searcher searcher;
        private final Ping pingChallenge = new Ping(monitor.getConfiguration().getRequestTimeout());
        private final Receiver<Pong> pong = new Receiver<>();

        public Pinger(final Searcher searcher) {
            this.searcher = searcher;
        }

        @Override
        public void run() {
            pong.put(createExecution().ping(pingChallenge));
        }

        private Execution createExecution() {
            return new Execution(new Chain<>(searcher),
                                 new Execution.Context(null, null, null, null, null));
        }

        public Pong getPong() throws InterruptedException {
            Tuple2<MessageState, Pong> reply = pong.get(pingChallenge.getTimeout() + 150);
            return (reply.first != MessageState.VALID) ? null : reply.second;
        }

    }

}
