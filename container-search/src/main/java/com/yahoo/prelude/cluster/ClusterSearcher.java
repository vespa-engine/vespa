// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.cluster;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.prelude.fastsearch.FS4ResourcePool;
import com.yahoo.prelude.fastsearch.FastSearcher;
import com.yahoo.prelude.fastsearch.SummaryParameters;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.query.ParameterParser;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vespa.streamingvisitors.VdsStreamingSearcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private final String searchClusterName;

    // The set of document types contained in this search cluster
    private final Set<String> documentTypes;

    // Mapping from rank profile names to document types containing them
    private final Map<String, Set<String>> rankProfiles = new HashMap<>();

    private final long maxQueryTimeout; // in milliseconds
    private final static long DEFAULT_MAX_QUERY_TIMEOUT = 600000L;

    private final long maxQueryCacheTimeout; // in milliseconds
    private final static long DEFAULT_MAX_QUERY_CACHE_TIMEOUT = 10000L;

    private VespaBackEndSearcher server = null;

    public ClusterSearcher(ComponentId id,
                           QrSearchersConfig qrsConfig,
                           ClusterConfig clusterConfig,
                           DocumentdbInfoConfig documentDbConfig,
                           ComponentRegistry<Dispatcher> dispatchers,
                           FS4ResourcePool fs4ResourcePool,
                           VipStatus vipStatus) {
        super(id);

        int searchClusterIndex = clusterConfig.clusterId();
        searchClusterName = clusterConfig.clusterName();
        QrSearchersConfig.Searchcluster searchClusterConfig = getSearchClusterConfigFromClusterName(qrsConfig, searchClusterName);
        documentTypes = new LinkedHashSet<>();

        maxQueryTimeout = ParameterParser.asMilliSeconds(clusterConfig.maxQueryTimeout(), DEFAULT_MAX_QUERY_TIMEOUT);
        maxQueryCacheTimeout = ParameterParser.asMilliSeconds(clusterConfig.maxQueryCacheTimeout(), DEFAULT_MAX_QUERY_CACHE_TIMEOUT);

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
            VdsStreamingSearcher searcher = vdsCluster(fs4ResourcePool.getServerId(), searchClusterIndex,
                                                       searchClusterConfig, docSumParams, documentDbConfig);
            addBackendSearcher(searcher);
            vipStatus.addToRotation(searcher.getName());
        } else {
            FastSearcher searcher = searchDispatch(searchClusterIndex, searchClusterName, fs4ResourcePool.getServerId(),
                                                   docSumParams, documentDbConfig, dispatchers);
            addBackendSearcher(searcher);

        }
        if ( server == null ) {
            throw new IllegalStateException("ClusterSearcher should have backend.");
        }
    }

    private static QrSearchersConfig.Searchcluster getSearchClusterConfigFromClusterName(QrSearchersConfig config, String name) {
        for (QrSearchersConfig.Searchcluster searchCluster : config.searchcluster()) {
            if (searchCluster.name().equals(name)) {
                return searchCluster;
            }
        }
        return null;
    }

    private static ClusterParams makeClusterParams(int searchclusterIndex) {
        return new ClusterParams("sc" + searchclusterIndex + ".num" + 0);
    }

    private static FastSearcher searchDispatch(int searchclusterIndex,
                                               String searchClusterName,
                                               String serverId,
                                               SummaryParameters docSumParams,
                                               DocumentdbInfoConfig documentdbInfoConfig,
                                               ComponentRegistry<Dispatcher> dispatchers) {
        ClusterParams clusterParams = makeClusterParams(searchclusterIndex);
        ComponentId dispatcherComponentId = new ComponentId("dispatcher." + searchClusterName);
        Dispatcher dispatcher = dispatchers.getComponent(dispatcherComponentId);
        if (dispatcher == null)
            throw new IllegalArgumentException("Configuration error: No dispatcher " + dispatcherComponentId +
                                               " is configured");
        return new FastSearcher(serverId, dispatcher, docSumParams, clusterParams, documentdbInfoConfig);
    }

    private static VdsStreamingSearcher vdsCluster(String serverId,
                                                   int searchclusterIndex,
                                                   QrSearchersConfig.Searchcluster searchClusterConfig,
                                                   SummaryParameters docSumParams,
                                                   DocumentdbInfoConfig documentdbInfoConfig) {
        if (searchClusterConfig.searchdef().size() != 1) {
            throw new IllegalArgumentException("Search clusters in streaming search shall only contain a single searchdefinition : " + searchClusterConfig.searchdef());
        }
        ClusterParams clusterParams = makeClusterParams(searchclusterIndex);
        VdsStreamingSearcher searcher = new VdsStreamingSearcher();
        searcher.setSearchClusterConfigId(searchClusterConfig.rankprofiles().configid());
        searcher.setDocumentType(searchClusterConfig.searchdef(0));
        searcher.setStorageClusterRouteSpec(searchClusterConfig.storagecluster().routespec());
        searcher.init(serverId, docSumParams, clusterParams, documentdbInfoConfig);
        return searcher;
    }

    /** Do not use, for internal testing purposes only. **/
    ClusterSearcher(Set<String> documentTypes) {
        this.documentTypes = documentTypes;
        searchClusterName = "testScenario";
        maxQueryTimeout = DEFAULT_MAX_QUERY_TIMEOUT;
        maxQueryCacheTimeout = DEFAULT_MAX_QUERY_CACHE_TIMEOUT;
    }

    void addBackendSearcher(VespaBackEndSearcher searcher) {
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
                                                                       String.join(", ", invalidInDocTypes) + "'"));
        }

        return null;
    }

    @Override
    public void fill(com.yahoo.search.Result result, String summaryClass, Execution execution) {
        Query query = result.getQuery();

        VespaBackEndSearcher searcher = server;
        if (searcher != null) {
            if (query.getTimeLeft() > 0) {
                searcher.fill(result, summaryClass, execution);
            } else {
                if (result.hits().getErrorHit() == null) {
                    result.hits().addError(ErrorMessage.createTimeout("No time left to get summaries, query timeout was " + query.getTimeout() + " ms"));
                }
            }
        } else {
            if (result.hits().getErrorHit() == null) {
                result.hits().addError(ErrorMessage.createNoBackendsInService("Could not fill result"));
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
        if (documentTypes.size() > 1) {
            return searchMultipleDocumentTypes(searcher, query, execution);
        } else {
            String docType = documentTypes.iterator().next();

            Result invalidRankProfile = checkValidRankProfiles(query, documentTypes);
            if (invalidRankProfile != null) {
                return invalidRankProfile;
            }

            query.getModel().setRestrict(docType);
            return searcher.search(query, execution);
        }
    }

    private Result searchMultipleDocumentTypes(Searcher searcher, Query query, Execution execution) {
        Set<String> docTypes = resolveDocumentTypes(query, execution.context().getIndexFacts());

        Result invalidRankProfile = checkValidRankProfiles(query, docTypes);
        if (invalidRankProfile != null) return invalidRankProfile;

        List<Query> queries = createQueries(query, docTypes);
        if (queries.size() == 1) {
            return searcher.search(queries.get(0), execution);
        } else {
            Result mergedResult = new Result(query);
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
                query.setOffset(0); // Needed when doing a trim
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
        query.getModel().getQueryTree(); // performance: parse query before cloning such that it is only done once
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

    @Override
    public void deconstruct() {
        if (server != null) {
            server.shutDown();
        }
    }

}
