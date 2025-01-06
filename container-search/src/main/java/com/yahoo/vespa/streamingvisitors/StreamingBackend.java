// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import com.yahoo.container.core.documentapi.VespaDocumentAccess;
import com.yahoo.document.DocumentId;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.document.select.parser.TokenMgrException;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.fs4.DocsumPacket;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.prelude.fastsearch.ClusterParams;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.prelude.fastsearch.TimeoutException;
import com.yahoo.prelude.fastsearch.VespaBackend;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Relevance;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.vdslib.DocumentSummary;
import com.yahoo.vdslib.SearchResult;
import com.yahoo.vdslib.VisitorStatistics;
import com.yahoo.vespa.streamingvisitors.tracing.TraceDescription;
import com.yahoo.yolean.Exceptions;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The searcher which forwards queries to storage nodes using visiting.
 * The searcher is a visitor client responsible for starting search
 * visitors in storage and collecting and merging the results.
 *
 * @author  baldersheim
 * @author  Ulf Carlin
 */
public class StreamingBackend extends VespaBackend {

    private static final Logger log = Logger.getLogger(StreamingBackend.class.getName());
    private static final CompoundName streamingUserid = CompoundName.from("streaming.userid");
    private static final CompoundName streamingGroupname = CompoundName.from("streaming.groupname");
    private static final CompoundName streamingSelection = CompoundName.from("streaming.selection");

    static final String STREAMING_STATISTICS = "streaming.statistics";
    private final VisitorFactory visitorFactory;
    private final TracingOptions tracingOptions;

    private final Route route;

    /** The configId used to access the searchcluster. */
    private final String searchClusterName;

    /** The route to the storage cluster. */
    private final String storageClusterRouteSpec;

    StreamingBackend(ClusterParams clusterParams, String searchClusterName, VisitorFactory visitorFactory, String storageClusterRouteSpec) {
        this(clusterParams, searchClusterName, visitorFactory, storageClusterRouteSpec, TracingOptions.DEFAULT);
    }

    StreamingBackend(ClusterParams clusterParams, String searchClusterName, VisitorFactory visitorFactory, String storageClusterRouteSpec, TracingOptions tracingOptions) {
        super(clusterParams);
        this.visitorFactory = visitorFactory;
        this.tracingOptions = tracingOptions;
        this.searchClusterName = searchClusterName;
        this.storageClusterRouteSpec = storageClusterRouteSpec;
        this.route = Route.parse(storageClusterRouteSpec);
    }

    public StreamingBackend(ClusterParams clusterParams, String searchClusterName, VespaDocumentAccess access, String storageClusterRouteSpec) {
        this(clusterParams, searchClusterName, new VespaVisitorFactory(access), storageClusterRouteSpec);
    }

    private String getSearchClusterName() { return searchClusterName; }

    @Override protected void doPartialFill(Result result, String summaryClass) { }

    private double durationInMillisFromNanoTime(long startTimeNanos) {
        return (tracingOptions.getClock().nanoTimeNow() - startTimeNanos) / (double)TimeUnit.MILLISECONDS.toNanos(1);
    }

    private boolean timeoutBadEnoughToBeReported(Query query, double durationMillis) {
        return (durationMillis > (query.getTimeout() * tracingOptions.getTraceTimeoutMultiplierThreshold()));
    }

    private static boolean queryIsLocationConstrained(Query query) {
        return ((query.properties().getString(streamingUserid) != null) ||
                (query.properties().getString(streamingGroupname) != null));
    }

    private static int documentSelectionQueryParameterCount(Query query) {
        int paramCount = 0;
        if (query.properties().getString(streamingUserid) != null)
            paramCount++;
        if (query.properties().getString(streamingGroupname) != null)
            paramCount++;
        if (query.properties().getString(streamingSelection) != null)
            paramCount++;
        return paramCount;
    }

    private boolean shouldTraceQuery(Query query) {
        // Only trace for explicit bucket subset queries, as otherwise we'd get a trace entry for every superbucket in the system.
        return (queryIsLocationConstrained(query) &&
                ((query.getTrace().getLevel() > 0) || tracingOptions.getSamplingStrategy().shouldSample()));
    }

    private int inferEffectiveQueryTraceLevel(Query query) {
        return ((query.getTrace().getLevel() == 0) && shouldTraceQuery(query)) // Honor query's explicit trace level if present.
                ? tracingOptions.getTraceLevelOverride()
                : query.getTrace().getLevel();
    }

    @Override
    public Result doSearch2(String schema, Query query) {
        if (query.getTimeLeft() <= 0)
            return new Result(query, ErrorMessage.createTimeout(String.format("No time left for searching (timeout=%d)", query.getTimeout())));

        initializeMissingQueryFields(query);
        if (documentSelectionQueryParameterCount(query) != 1) {
            return new Result(query, ErrorMessage.createIllegalQuery("Streaming search requires either " +
                                                                     "streaming.groupname or streaming.selection"));
        }
        try {
            ensureLegalSummaryClass(query, query.getPresentation().getSummary());
        } catch (IllegalInputException e) {
            return new Result(query, ErrorMessage.createIllegalQuery(Exceptions.toMessageString(e)));
        }

        if (query.getTrace().isTraceable(4))
            query.trace("Routing to search cluster " + getSearchClusterName() + " and document type " + schema, 4);
        long timeStartedNanos = tracingOptions.getClock().nanoTimeNow();
        int effectiveTraceLevel = inferEffectiveQueryTraceLevel(query);

        Visitor visitor = visitorFactory.createVisitor(query, getSearchClusterName(), route, schema, effectiveTraceLevel);
        try {
            visitor.doSearch();
        } catch (ParseException e) {
            return new Result(query, ErrorMessage.createInvalidQueryParameter("Failed to parse document selection string: " +
                                                                              e.getMessage()));
        } catch (TokenMgrException e) {
            return new Result(query, ErrorMessage.createInvalidQueryParameter("Failed to tokenize document selection string: " +
                                                                              e.getMessage()));
        } catch (TimeoutException e) {
            double elapsedMillis = durationInMillisFromNanoTime(timeStartedNanos);
            if ((effectiveTraceLevel > 0) && timeoutBadEnoughToBeReported(query, elapsedMillis)) {
                tracingOptions.getTraceExporter().maybeExport(() -> new TraceDescription(visitor.getTrace(),
                                                                                         String.format("Trace of %s which timed out after %.3g seconds",
                                                                                                       query, elapsedMillis / 1000.0)));
            }
            return new Result(query, ErrorMessage.createTimeout(e.getMessage()));
        } catch (InterruptedException e) {
            return new Result(query, ErrorMessage.createBackendCommunicationError(e.getMessage()));
        }
        return buildResultFromCompletedVisitor(query, visitor);
    }

    private void initializeMissingQueryFields(Query query) {
        lazyTrace(query, 7, "Routing to storage cluster ", storageClusterRouteSpec);
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
    }

    private Result buildResultFromCompletedVisitor(Query query, Visitor visitor) {
        lazyTrace(query, 8, "offset=", query.getOffset(), ", hits=", query.getHits());

        Result result = new Result(query);
        List<SearchResult.Hit> hits = visitor.getHits(); // Sorted on rank
        Map<String, DocumentSummary.Summary> summaryMap = visitor.getSummaryMap();

        lazyTrace(query, 7, "total hit count = ", visitor.getTotalHitCount(),
                  ", returned hit count = ", hits.size(), ", summary count = ",
                  summaryMap.size());

        VisitorStatistics stats = visitor.getStatistics();
        result.setTotalHitCount(visitor.getTotalHitCount());
        result.setCoverage(new Coverage(stats.getDocumentsVisited(), stats.getDocumentsVisited(), 1, 1));
        query.trace(visitor.getStatistics().toString(), false, 2);
        query.getContext(true).setProperty(STREAMING_STATISTICS, stats);

        DocsumPacket[] summaryPackets = new DocsumPacket [hits.size()];

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
                summaryPackets[index] = dp;
            } else {
                return new Result(query, ErrorMessage.createBackendCommunicationError("Did not find summary for hit with document id " +
                                                                                      hit.getDocId()));
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
            GroupingListHit groupHit = new GroupingListHit(groupingList, getDocumentDatabase(query), query);
            result.hits().add(groupHit);
        }

        FillHitsResult fillHitsResult = fillHits(result, summaryPackets, query.getPresentation().getSummary());
        int skippedHits = fillHitsResult.skippedHits;
        if (fillHitsResult.error != null) {
            result.hits().addError(ErrorMessage.createTimeout(fillHitsResult.error));
            return result;
        }

        if (skippedHits == 0) {
            query.trace("All hits have been filled",4); // TODO: cache results or result.analyzeHits(); ?
        } else {
            lazyTrace(query, 8, "Skipping some hits for query: ", result.getQuery());
        }

        lazyTrace(query, 8, "Returning result ", result);

        if (skippedHits > 0) {
            log.info("skipping " + skippedHits + " hits for query: " + result.getQuery());
            result.hits().addError(ErrorMessage.createTimeout("Missing hit summary data for " + skippedHits + " hits"));
        }

        var errors = visitor.getErrors();
        for (var error : errors) {
            result.hits().addError(ErrorMessage.createSearchReplyError(error));
        }

        return result;
    }

    private FastHit buildSummaryHit(Query query, SearchResult.Hit hit) {
        FastHit fastHit = new FastHit();
        fastHit.setQuery(query);
        fastHit.setSource(getName());
        fastHit.setId(hit.getDocId());
        fastHit.setRelevance(new Relevance(hit.getRank()));
        if (hit instanceof SearchResult.HitWithSortBlob sortedHit) {
            fastHit.setSortData(sortedHit.getSortBlob(), query.getRanking().getSorting());
        }
        if (hit.getMatchFeatures().isPresent()) {
            fastHit.setField("matchfeatures", new FeatureData(hit.getMatchFeatures().get()));
        }

        fastHit.setFillable();
        return fastHit;
    }

    private static void lazyTrace(Query query, int level, Object... args) {
        if (query.getTrace().isTraceable(level)) {
            StringBuilder s = new StringBuilder();
            for (Object arg : args) {
                s.append(arg);
            }
            query.trace(s.toString(), level);
        }
    }

    static boolean verifyDocId(String id, Query query, boolean skippedEarlierResult) {
        String expectedUserId = query.properties().getString(streamingUserid);
        String expectedGroupName = query.properties().getString(streamingGroupname);

        Level logLevel = Level.SEVERE;
        if (skippedEarlierResult) {
            logLevel = Level.FINE;
        }

        DocumentId docId;
        try {
            docId = new DocumentId(id);
        } catch (IllegalArgumentException iae)  {
            log.log(logLevel, "Bad result for " + query + ": " + iae.getMessage());
            return false;
        }

        if (expectedUserId != null) {
            long userId;

            if (docId.getScheme().hasNumber()) {
                userId = docId.getScheme().getNumber();
            } else {
                log.log(logLevel, "Got result with wrong scheme  in document ID (" + id + ") for " + query);
                return false;
            }
            if (new BigInteger(expectedUserId).longValue() != userId) {
                log.log(logLevel, "Got result with wrong user ID (expected " + expectedUserId + ") in document ID (" +
                                  id + ") for " + query);
                return false;
            }
        } else if (expectedGroupName != null) {
            String groupName;

            if (docId.getScheme().hasGroup()) {
                groupName = docId.getScheme().getGroup();
            } else {
                log.log(logLevel, "Got result with wrong scheme  in document ID (" + id + ") for " + query);
                return false;
            }
            if (!expectedGroupName.equals(groupName)) {
                log.log(logLevel, "Got result with wrong group name (expected " + expectedGroupName + ") in document ID (" +
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

    private static class VespaVisitorFactory implements StreamingVisitor.VisitorSessionFactory, VisitorFactory {

        private final VespaDocumentAccess access;

        private VespaVisitorFactory(VespaDocumentAccess access) {
            this.access = access;
        }

        @Override
        public VisitorSession createVisitorSession(VisitorParameters params) throws ParseException {
            return access.createVisitorSession(params);
        }

        @Override
        public Visitor createVisitor(Query query, String searchCluster, Route route, String schema, int traceLevelOverride) {
            return new StreamingVisitor(query, searchCluster, route, schema, this, traceLevelOverride);
        }

    }

}
