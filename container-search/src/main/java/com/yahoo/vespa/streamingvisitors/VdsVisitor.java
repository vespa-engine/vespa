// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import com.yahoo.document.fieldset.AllFields;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.AckToken;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorDataHandler;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.messagebus.protocol.QueryResultMessage;
import com.yahoo.io.GrowableByteBuffer;

import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Trace;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.prelude.fastsearch.TimeoutException;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.grouping.vespa.GroupingExecutor;
import com.yahoo.search.query.Model;
import com.yahoo.search.query.Ranking;
import com.yahoo.searchlib.aggregation.Grouping;
import com.yahoo.vdslib.DocumentSummary;
import com.yahoo.vdslib.SearchResult;
import com.yahoo.vdslib.VisitorStatistics;
import com.yahoo.vespa.objects.BufferSerializer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A visitor data handler that performs a query in VDS with the
 * searchvisitor visitor plugin. It collects and merges hits (sorted
 * descending on rank), summaries (sorted on document id), and
 * groupings. The resulting data can be fetched when the query has
 * completed.
 *
 * @author Ulf Carlin
 */
class VdsVisitor extends VisitorDataHandler implements Visitor {

    private static final CompoundName streamingUserid = CompoundName.from("streaming.userid");
    private static final CompoundName streamingGroupname = CompoundName.from("streaming.groupname");
    private static final CompoundName streamingSelection = CompoundName.from("streaming.selection");
    private static final CompoundName streamingFromtimestamp = CompoundName.from("streaming.fromtimestamp");
    private static final CompoundName streamingTotimestamp = CompoundName.from("streaming.totimestamp");
    private static final CompoundName streamingPriority = CompoundName.from("streaming.priority");
    private static final CompoundName streamingMaxbucketspervisitor = CompoundName.from("streaming.maxbucketspervisitor");

    protected static final int MAX_BUCKETS_PER_VISITOR = 1024;

    private static final Logger log = Logger.getLogger(VdsVisitor.class.getName());
    private final VisitorParameters params = new VisitorParameters("");
    private List<SearchResult.Hit> hits = new ArrayList<>();
    private int totalHitCount = 0;

    private final Map<String, DocumentSummary.Summary> summaryMap = new HashMap<>();
    private final Map<Integer, Grouping> groupingMap = new ConcurrentHashMap<>();
    private Query query = null;
    private final VisitorSessionFactory visitorSessionFactory;
    private final int traceLevelOverride;
    private Trace sessionTrace;

    public interface VisitorSessionFactory {
        VisitorSession createVisitorSession(VisitorParameters params) throws ParseException;
    }

    public VdsVisitor(Query query, String searchCluster, Route route,
                      String documentType, VisitorSessionFactory visitorSessionFactory,
                      int traceLevelOverride)
    {
        this.query = query;
        this.visitorSessionFactory = visitorSessionFactory;
        this.traceLevelOverride = traceLevelOverride;
        setVisitorParameters(searchCluster, route, documentType);
    }

    private int inferSessionTraceLevel(Query query) {
        int implicitLevel = traceLevelOverride;
        if (log.isLoggable(Level.FINEST)) {
            implicitLevel = 9;
        } else if (log.isLoggable(Level.FINE)) {
            implicitLevel = 7;
        }
        return Math.max(query.getTrace().getLevel(), implicitLevel);
    }

    private static String createSelectionString(String documentType, String selection) {
        if ((selection == null) || selection.isEmpty()) return documentType;

        StringBuilder sb = new StringBuilder(documentType);
        sb.append(" and ( ").append(selection).append(" )");
        return sb.toString();
    }

    private String createQuerySelectionString() {
        String s = query.properties().getString(streamingUserid);
        if (s != null) {
            return "id.user==" + s;
        }
        s = query.properties().getString(streamingGroupname);
        if (s != null) {
            return "id.group==\"" + s + "\"";
        }
        return query.properties().getString(streamingSelection);
    }

    private void setVisitorParameters(String searchCluster, Route route, String documentType) {
        params.setDocumentSelection(createSelectionString(documentType, createQuerySelectionString()));
        params.setTimeoutMs(query.getTimeout()); // Per bucket visitor timeout
        params.setSessionTimeoutMs(query.getTimeout());
        params.setVisitorLibrary("searchvisitor");
        params.setLocalDataHandler(this);
        if (query.properties().getDouble(streamingFromtimestamp) != null) {
            params.setFromTimestamp(query.properties().getDouble(streamingFromtimestamp).longValue());
        }
        if (query.properties().getDouble(streamingTotimestamp) != null) {
            params.setToTimestamp(query.properties().getDouble(streamingTotimestamp).longValue());
        }
        params.setFieldSet(AllFields.NAME); // Streaming searches need to look at _all_ fields by default.
        params.visitInconsistentBuckets(true);
        params.setPriority(DocumentProtocol.Priority.VERY_HIGH);

        if (query.properties().getString(streamingPriority) != null) {
            params.setPriority(DocumentProtocol.getPriorityByName(
                    query.properties().getString(streamingPriority)));
        }

        params.setMaxPending(Integer.MAX_VALUE);
        params.setMaxBucketsPerVisitor(MAX_BUCKETS_PER_VISITOR);
        params.setTraceLevel(inferSessionTraceLevel(query));


        String maxbuckets = query.properties().getString(streamingMaxbucketspervisitor);
        if (maxbuckets != null) {
            params.setMaxBucketsPerVisitor(Integer.parseInt(maxbuckets));
        }

        EncodedData ed = new EncodedData();
        encodeQueryData(query, 0, ed);
        params.setLibraryParameter("query", ed.getEncodedData());
        params.setLibraryParameter("querystackcount", String.valueOf(ed.getReturned()));
        params.setLibraryParameter("searchcluster", searchCluster.getBytes());
        if (query.getPresentation().getSummary() != null) {
            params.setLibraryParameter("summaryclass", query.getPresentation().getSummary());
        } else {
            params.setLibraryParameter("summaryclass", "default");
        }
        Set<String> summaryFields = query.getPresentation().getSummaryFields();
        if (summaryFields != null && !summaryFields.isEmpty()) {
            params.setLibraryParameter("summary-fields", String.join(" ", summaryFields));
        }
        params.setLibraryParameter("summarycount", String.valueOf(query.getOffset() + query.getHits()));
        params.setLibraryParameter("rankprofile", query.getRanking().getProfile());
        params.setLibraryParameter("allowslimedocsums", "true");
        params.setLibraryParameter("queryflags", String.valueOf(getQueryFlags(query)));

        ByteBuffer buf = ByteBuffer.allocate(1024);

        if (query.getRanking().getLocation() != null) {
            buf.clear();
            query.getRanking().getLocation().encode(buf);
            buf.flip();
            byte[] af = new byte [buf.remaining()];
            buf.get(af);
            params.setLibraryParameter("location", af);
        }

        if (QueryEncoder.hasEncodableProperties(query)) {
            encodeQueryData(query, 1, ed);
            params.setLibraryParameter("rankproperties", ed.getEncodedData());
        }

        List<Grouping> groupingList = GroupingExecutor.getGroupingList(query);
        if (groupingList.size() > 0){
            BufferSerializer gbuf = new BufferSerializer(new GrowableByteBuffer());
            gbuf.putInt(null, groupingList.size());
            for(Grouping g: groupingList){
                g.serialize(gbuf);
            }
            gbuf.flip();
            byte [] blob = gbuf.getBytes(null, gbuf.getBuf().limit());
            params.setLibraryParameter("aggregation", blob);
        }

        if (query.getRanking().getSorting() != null) {
            encodeQueryData(query, 3, ed);
            params.setLibraryParameter("sort", ed.getEncodedData());
        }

        params.setRoute(route);
    }

    static int getQueryFlags(Query query) {
        int flags = 0;

        flags |= query.properties().getBoolean(Model.ESTIMATE) ? 0x00000080 : 0;
        flags |= (query.getRanking().getFreshness() != null) ? 0x00002000 : 0;
        flags |= 0x00008000;
        flags |= query.getNoCache() ? 0x00010000 : 0;
        flags |= 0x00020000;                         // was PARALLEL
        flags |= query.properties().getBoolean(Ranking.RANKFEATURES,false) ? 0x00040000 : 0;

        return flags;
    }

    private static class EncodedData {
        private Object returned;
        private byte[] encoded;

        public void setReturned(Object o){
            this.returned = o;
        }
        public Object getReturned(){
            return returned;
        }
        public void setEncodedData(byte[] data){
            encoded = data;
        }
        public byte[] getEncodedData(){
            return encoded;
        }
    }

    private static void encodeQueryData(Query query, int code, EncodedData ed){
        ByteBuffer buf = ByteBuffer.allocate(1024);
        while (true) {
            try {
                switch(code){
                    case 0:
                        ed.setReturned(query.getModel().getQueryTree().getRoot().encode(buf));
                        break;
                    case 1:
                        ed.setReturned(QueryEncoder.encodeAsProperties(query, buf));
                        break;
                    case 2:
                        throw new IllegalArgumentException("old aggregation no longer exists!");
                    case 3:
                        if (query.getRanking().getSorting() != null)
                            ed.setReturned(query.getRanking().getSorting().encode(buf));
                        else
                            ed.setReturned(0);
                        break;
                }
                buf.flip();
                break;
            } catch (java.nio.BufferOverflowException e) {
                int size = buf.limit();
                buf = ByteBuffer.allocate(size*2);
            }
        }
        byte [] bb = new byte [buf.remaining()];
        buf.get(bb);
        ed.setEncodedData(bb);
    }

    @Override
    public void doSearch() throws InterruptedException, ParseException, TimeoutException {
        VisitorSession session = visitorSessionFactory.createVisitorSession(params);
        try {
            if ( ! session.waitUntilDone(query.getTimeout())) {
                log.log(Level.FINE, () -> "Visitor returned from waitUntilDone without being completed for " + query + " with selection " + params.getDocumentSelection());
                session.abort();
                throw new TimeoutException("Query timed out in " + VdsStreamingSearcher.class.getName());
            }
        } finally {
            session.destroy();
            sessionTrace = session.getTrace();
            log.log(Level.FINE, () -> sessionTrace.toString());
            query.trace(sessionTrace.toString(), false, 9);
        }

        if (params.getControlHandler().getResult().code == VisitorControlHandler.CompletionCode.SUCCESS) {
            log.log(Level.FINE, () -> "VdsVisitor completed successfully for " + query + " with selection " + params.getDocumentSelection());
        } else {
            throw new IllegalArgumentException("Query failed: " +
                                               params.getControlHandler().getResult().code + ": " +
                                               params.getControlHandler().getResult().message);
        }
    }

    @Override
    public VisitorStatistics getStatistics() {
        return params.getControlHandler().getVisitorStatistics();
    }

    @Override
    public void onMessage(Message m, AckToken token) {
        if (m instanceof QueryResultMessage qm) {
            onQueryResult(qm.getResult(), qm.getSummary());
        } else {
            throw new UnsupportedOperationException("Received unsupported message " + m + ". VdsVisitor can only accept query result messages.");
        }
        ack(token);
    }

    @Override
    public Trace getTrace() {
        return sessionTrace;
    }

    public void onQueryResult(SearchResult sr, DocumentSummary summary) {
        handleSearchResult(sr);
        handleSummary(summary);
    }

    private void handleSearchResult(SearchResult sr) {
        final int hitCountTotal = sr.getTotalHitCount();
        final int hitCount = sr.getHitCount();
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "Got SearchResult with " + hitCountTotal + " in total and " + hitCount + " hits in real for query with selection " + params.getDocumentSelection());
        }

        List<SearchResult.Hit> newHits = new ArrayList<>(hitCount);
        for (int i = 0; i < hitCount; i++) {
            SearchResult.Hit hit = sr.getHit(i);
            newHits.add(hit);
        }
        synchronized (this) {
            totalHitCount += hitCountTotal;
            hits = ListMerger.mergeIntoArrayList(hits, newHits, query.getOffset() + query.getHits());
        }

        Map<Integer, byte []> newGroupingMap = sr.getGroupingList();
        mergeGroupingMaps(newGroupingMap);
    }

    private void mergeGroupingMaps(Map<Integer, byte []> newGroupingMap) {
        if (log.isLoggable(Level.FINEST)) {
            log.log(Level.FINEST, "mergeGroupingMaps: newGroupingMap = " + newGroupingMap);
        }
        for(Integer key : newGroupingMap.keySet()) {
            byte [] value = newGroupingMap.get(key);

            Grouping newGrouping = new Grouping();
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "Received group with key " + key + " and size " + value.length);
            }
            BufferSerializer buf = new BufferSerializer( new GrowableByteBuffer(ByteBuffer.wrap(value)) );
            newGrouping.deserialize(buf);
            if (buf.getBuf().hasRemaining()) {
                throw new IllegalArgumentException("Failed deserializing grouping. There is still data left. " +
                                                   "Position = " + buf.position() + ", limit = " + buf.getBuf().limit());
            }

            synchronized (groupingMap) {
                if (groupingMap.containsKey(key)) {
                    Grouping grouping = groupingMap.get(key);
                    grouping.merge(newGrouping);
                } else {
                    groupingMap.put(key, newGrouping);
                }
            }
        }
    }

    private void handleSummary(DocumentSummary ds) {
        int summaryCount = ds.getSummaryCount();
        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "Got DocumentSummary with " + summaryCount + " summaries for query with selection " + params.getDocumentSelection());
        }
        synchronized (summaryMap) {
            for (int i = 0; i < summaryCount; i++) {
                DocumentSummary.Summary summary = ds.getSummary(i);
                summaryMap.put(summary.getDocId(), summary);
            }
        }
    }

    @Override
    final public List<SearchResult.Hit> getHits() {
        int fromIndex = Math.min(hits.size(), query.getOffset());
        int toIndex = Math.min(hits.size(), query.getOffset() + query.getHits());
        return hits.subList(fromIndex, toIndex);
    }

    @Override
    final public Map<String, DocumentSummary.Summary> getSummaryMap() { return summaryMap; }

    @Override
    final public int getTotalHitCount() { return totalHitCount; }

    @Override
    final public List<Grouping> getGroupings() {
        Collection<Grouping> groupings = groupingMap.values();
        for (Grouping g : groupings) {
            g.postMerge();
        }
        return new ArrayList<>(groupings);
    }

}
