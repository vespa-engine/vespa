// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.yahoo.document.DocumentId;
import com.yahoo.document.idstring.IdString;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.select.parser.TokenMgrError;
import com.yahoo.fs4.DocsumPacket;
import com.yahoo.fs4.Packet;
import com.yahoo.fs4.QueryPacket;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.prelude.fastsearch.CacheKey;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.prelude.fastsearch.TimeoutException;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.vdslib.DocumentSummary;
import com.yahoo.vdslib.SearchResult;

/**
 * The searcher which forwards queries to storage nodes using visiting.
 * The searcher is a visitor client responsible for starting search
 * visitors in storage and collecting and merging the results.
 *
 * @author  baldersheim
 * @author  Ulf Carlin
 */
@SuppressWarnings("deprecation")
public class VdsStreamingSearcher extends VespaBackEndSearcher {

    private static final CompoundName streamingUserid=new CompoundName("streaming.userid");
    private static final CompoundName streamingGroupname=new CompoundName("streaming.groupname");
    private static final CompoundName streamingSelection=new CompoundName("streaming.selection");

    public static final String STREAMING_STATISTICS = "streaming.statistics";
    private VisitorFactory visitorFactory;
    private static final Logger log = Logger.getLogger(VdsStreamingSearcher.class.getName());
    private Route route;
    /** The configId used to access the searchcluster. */
    private String searchClusterConfigId = null;
    /** The route to the storage cluster. */
    private String storageClusterRouteSpec = null;

    String getSearchClusterConfigId() { return searchClusterConfigId; }
    String getStorageClusterRouteSpec() { return storageClusterRouteSpec; }
    public final void setSearchClusterConfigId(String clusterName) {
        this.searchClusterConfigId = clusterName;
    }

    public final void setStorageClusterRouteSpec(String storageClusterRouteSpec) {
        this.storageClusterRouteSpec = storageClusterRouteSpec;
    }

    private static class VdsVisitorFactory implements VisitorFactory {
        @Override
        public Visitor createVisitor(Query query, String searchCluster, Route route) {
            return new VdsVisitor(query, searchCluster, route);
        }
    }

    public VdsStreamingSearcher() {
        visitorFactory = new VdsVisitorFactory();
    }

    public VdsStreamingSearcher(VisitorFactory visitorFactory) {
        this.visitorFactory = visitorFactory;
    }

    @Override
    protected void doPartialFill(Result result, String summaryClass) {
    }

    @Override
    public Result doSearch2(Query query, QueryPacket queryPacket, CacheKey cacheKey, Execution execution) {
        // TODO refactor this method into smaller methods, it's hard to see the actual code
        lazyTrace(query, 7, "Routing to storage cluster ", getStorageClusterRouteSpec());

        if (route == null) {
            route = Route.parse(getStorageClusterRouteSpec());
        }
        lazyTrace(query, 8, "Route is ", route);

        lazyTrace(query, 7, "doSearch2(): query docsum class=",
                query.getPresentation().getSummary(), ", default docsum class=",
                getDefaultDocsumClass());

        if (query.getPresentation().getSummary() == null) {
            lazyTrace(query, 6,
                    "doSearch2(): No summary class specified in query, using default: ",
                    getDefaultDocsumClass());
            query.getPresentation().setSummary(getDefaultDocsumClass());
        } else {
            lazyTrace(query, 6,
                    "doSearch2(): Summary class has been specified in query: ",
                    query.getPresentation().getSummary());
        }

        lazyTrace(query, 8, "doSearch2(): rank properties=", query.getRanking());
        lazyTrace(query, 8, "doSearch2(): sort specification=", query
                .getRanking().getSorting() == null ? null : query.getRanking()
                .getSorting().fieldOrders());

        int documentSelectionQueryParameterCount = 0;
        if (query.properties().getString(streamingUserid) != null) documentSelectionQueryParameterCount++;
        if (query.properties().getString(streamingGroupname) != null) documentSelectionQueryParameterCount++;
        if (query.properties().getString(streamingSelection) != null) documentSelectionQueryParameterCount++;
        if (documentSelectionQueryParameterCount != 1) {
            return new Result(query, ErrorMessage.createBackendCommunicationError("Streaming search needs one and " +
                    "only one of these query parameters to be set: streaming.userid, streaming.groupname, " +
                    "streaming.selection"));
        }
        query.trace("Routing to search cluster " + getSearchClusterConfigId(), 4);
        Visitor visitor = visitorFactory.createVisitor(query, getSearchClusterConfigId(), route);
        try {
            visitor.doSearch();
        } catch (ParseException e) {
            return new Result(query, ErrorMessage.createBackendCommunicationError(
                    "Failed to parse document selection string: " + e.getMessage() + "'."));
        } catch (TokenMgrError e) {
            return new Result(query, ErrorMessage.createBackendCommunicationError(
                    "Failed to tokenize document selection string: " + e.getMessage() + "'."));
        } catch (TimeoutException e) {
            return new Result(query, ErrorMessage.createTimeout(e.getMessage()));
        } catch (InterruptedException|IllegalArgumentException e) {
            return new Result(query, ErrorMessage.createBackendCommunicationError(e.getMessage()));
        }

        lazyTrace(query, 8, "offset=", query.getOffset(), ", hits=", query.getHits());

        Result result = new Result(query);
        List<SearchResult.Hit> hits = visitor.getHits(); // Sorted on rank
        Map<String, DocumentSummary.Summary> summaryMap = visitor.getSummaryMap();

        lazyTrace(query, 7, "total hit count = ", visitor.getTotalHitCount(),
                ", returned hit count = ", hits.size(), ", summary count = ",
                summaryMap.size());

        result.setTotalHitCount(visitor.getTotalHitCount());
        query.trace(visitor.getStatistics().toString(), false, 2);
        query.getContext(true).setProperty(STREAMING_STATISTICS, visitor.getStatistics());

        Packet[] summaryPackets = new Packet [hits.size()];

        int index = 0;
        boolean skippedEarlierResult = false;
        for (SearchResult.Hit hit : hits) {
            if (!verifyDocId(hit.getDocId(), query, skippedEarlierResult)) {
                skippedEarlierResult = true;
                continue;
            }
            FastHit fastHit = buildSummaryHit(query, hit);
            result.hits().add(fastHit);

            DocumentSummary.Summary summary = summaryMap.get(hit.getDocId());
            if (summary != null) {
                DocsumPacket dp = new DocsumPacket(summary.getSummary());
                //log.log(LogLevel.SPAM, "DocsumPacket: " + dp);
                summaryPackets[index] = dp;
            } else {
                return new Result(query, ErrorMessage.createBackendCommunicationError(
                        "Did not find summary for hit with document id " + hit.getDocId()));
            }

            index++;
        }
        if (result.isFilled(query.getPresentation().getSummary())) {
            lazyTrace(query, 8, "Result is filled for summary class ", query.getPresentation().getSummary());
        } else {
            lazyTrace(query, 8, "Result is not filled for summary class ", query.getPresentation().getSummary());
        }

        List<Grouping> groupingList = visitor.getGroupings();
        lazyTrace(query, 8, "Grouping list=", groupingList);
        if ( ! groupingList.isEmpty() ) {
            GroupingListHit groupHit = new GroupingListHit(groupingList, getDocsumDefinitionSet(query));
            result.hits().add(groupHit);
        }

        int skippedHits;
        try {
            FillHitsResult fillHitsResult = fillHits(result, summaryPackets, query.getPresentation().getSummary());
            skippedHits = fillHitsResult.skippedHits;
            if (fillHitsResult.error != null) {
                result.hits().addError(ErrorMessage.createTimeout(fillHitsResult.error));
                return result;
            }
        } catch (TimeoutException e) {
            result.hits().addError(ErrorMessage.createTimeout(e.getMessage()));
            return result;
        } catch (IOException e) {
            return new Result(query, ErrorMessage.createBackendCommunicationError("Error filling hits with summary fields"));
        }

        if (skippedHits==0) {
            query.trace("All hits have been filled",4); // TODO: cache results or result.analyzeHits(); ?
        } else {
            lazyTrace(query, 8, "Skipping some hits for query: ", result.getQuery());
        }

        lazyTrace(query, 8, "Returning result ", result);

        if ( skippedHits>0 ) {
            getLogger().info("skipping " + skippedHits + " hits for query: " + result.getQuery());
            result.hits().addError(com.yahoo.search.result.ErrorMessage.createTimeout("Missing hit summary data for " + skippedHits + " hits"));
        }

        return result;
    }

    private FastHit buildSummaryHit(Query query, SearchResult.Hit hit) {
        FastHit fastHit = new FastHit();
        fastHit.setQuery(query);
        fastHit.setSource(getName());
        fastHit.setId(hit.getDocId());
        fastHit.setRelevance(new Relevance(hit.getRank()));

        fastHit.setFillable();
        return fastHit;
    }

    private static void lazyTrace(Query query, int level, Object... args) {
        if (query.isTraceable(level)) {
            StringBuilder s = new StringBuilder();
            for (Object arg : args) {
                s.append(arg);
            }
            query.trace(s.toString(), level);
        }
    }

    static boolean verifyDocId(String id, Query query, boolean skippedEarlierResult) {
        String expUserId = query.properties().getString(streamingUserid);
        String expGroupName = query.properties().getString(streamingGroupname);

        LogLevel logLevel = LogLevel.ERROR;
        if (skippedEarlierResult) {
            logLevel = LogLevel.DEBUG;
        }

        DocumentId docId;
        try {
            docId = new DocumentId(id);
        } catch (IllegalArgumentException iae)  {
            log.log(logLevel, "Bad result for " + query + ": " + iae.getMessage());
            return false;
        }

        if (expUserId != null) {
            long userId;

            if (docId.getScheme().hasNumber()) {
                userId = docId.getScheme().getNumber();
            } else {
                log.log(logLevel, "Got result with wrong scheme (expected " + IdString.Scheme.userdoc +
                        " or " + IdString.Scheme.orderdoc + ") in document ID (" + id + ") for " + query);
                return false;
            }
            if (new BigInteger(expUserId).longValue() != userId) {
                log.log(logLevel, "Got result with wrong user ID (expected " + expUserId + ") in document ID (" +
                         id + ") for " + query);
                return false;
            }
        } else if (expGroupName != null) {
            String groupName;

            if (docId.getScheme().hasGroup()) {
                groupName = docId.getScheme().getGroup();
            } else {
                log.log(logLevel, "Got result with wrong scheme (expected " + IdString.Scheme.groupdoc +
                        " or " + IdString.Scheme.orderdoc + ") in document ID (" + id + ") for " + query);
                return false;
            }
            if (!expGroupName.equals(groupName)) {
                log.log(logLevel, "Got result with wrong group name (expected " + expGroupName + ") in document ID (" +
                        id + ") for " + query);
                return false;
            }
        }
        return true;
    }

    public Pong ping(Ping ping, Execution execution) {
        // TODO add a real pong
        return new Pong();
    }
}
