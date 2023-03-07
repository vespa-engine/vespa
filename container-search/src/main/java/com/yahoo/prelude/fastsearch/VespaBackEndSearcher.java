// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.collections.TinyIdentitySet;
import com.yahoo.fs4.DocsumPacket;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.GeoLocationItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.textualrepresentation.TextualQueryRepresentation;
import com.yahoo.prelude.querytransform.QueryRewrite;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.protect.Validator;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.cluster.PingableSearcher;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.grouping.vespa.GroupingExecutor;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.searchlib.aggregation.Grouping;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Superclass for backend searchers.
 *
 * @author baldersheim
 */
public abstract class VespaBackEndSearcher extends PingableSearcher {

    /** for vespa-internal use only; consider renaming the summary class */
    public static final String SORTABLE_ATTRIBUTES_SUMMARY_CLASS = "attributeprefetch";

    private String serverId;

    /** The set of all document databases available in the backend handled by this searcher */
    private final Map<String, DocumentDatabase> documentDbs = new LinkedHashMap<>();
    private DocumentDatabase defaultDocumentDb = null;

    /** Default docsum class. null means "unset" and is the default value */
    private String defaultDocsumClass = null;

    /** Returns an iterator which returns all hits below this result **/
    private static Iterator<Hit> hitIterator(Result result) {
        return result.hits().unorderedDeepIterator();
    }

    /** The name of this source */
    private String name;

    public final String getName() { return name; }
    protected final String getDefaultDocsumClass() { return defaultDocsumClass; }

    /** Sets default document summary class. Default is null */
    private void setDefaultDocsumClass(String docsumClass) { defaultDocsumClass = docsumClass; }

    public final Logger getLogger() { return super.getLogger(); }

    /**
     * Searches a search cluster
     * This is an endpoint - searchers will never propagate the search to any nested searcher.
     *
     * @param query the query to search
     * @param execution the query execution context
     */
    protected abstract Result doSearch2(Query query, Execution execution);

    protected abstract void doPartialFill(Result result, String summaryClass);

    private boolean hasLocation(Item tree) {
        if (tree instanceof GeoLocationItem) {
            return true;
        }
        if (tree instanceof CompositeItem composite) {
            for (Item child : composite.items()) {
                if (hasLocation(child)) return true;
            }
        }
        return false;
    }

    /**
     * Returns whether we need to send the query when fetching summaries.
     * This is necessary if the query requests summary features or dynamic snippeting.
     */
    public boolean summaryNeedsQuery(Query query) {
        if (query.getRanking().getQueryCache()) return false;  // Query is cached in backend

        DocumentDatabase documentDb = getDocumentDatabase(query);

        // Needed to generate a dynamic summary?
        DocsumDefinition docsumDefinition = documentDb.getDocsumDefinitionSet().getDocsum(query.getPresentation().getSummary());
        if (docsumDefinition.isDynamic()) return true;

        if (hasLocation(query.getModel().getQueryTree())) return true;

        // Needed to generate ranking features?
        RankProfile rankProfile = documentDb.schema().rankProfiles().get(query.getRanking().getProfile());
        if (rankProfile == null) return true; // stay safe
        if (rankProfile.hasSummaryFeatures()) return true;
        if (query.getRanking().getListFeatures()) return true;

        // (Don't just add other checks here as there is a return false above)

        return false;
    }

    public String getServerId() { return serverId; }

    public DocumentDatabase getDocumentDatabase(Query query) {
        if (query.getModel().getRestrict().size() == 1) {
            String docTypeName = (String)query.getModel().getRestrict().toArray()[0];
            DocumentDatabase db = documentDbs.get(docTypeName);
            if (db != null) {
                return db;
            }
        }
        return defaultDocumentDb;
    }

    private void resolveDocumentDatabase(Query query) {
        DocumentDatabase docDb = getDocumentDatabase(query);
        if (docDb != null) {
            query.getModel().setDocumentDb(docDb.schema().name());
        }
    }

    public final void init(String serverId, SummaryParameters docSumParams, ClusterParams clusterParams,
                           DocumentdbInfoConfig documentdbInfoConfig, SchemaInfo schemaInfo) {
        this.serverId = serverId;
        this.name = clusterParams.searcherName;

        Validator.ensureNotNull("Name of Vespa backend integration", getName());

        setDefaultDocsumClass(docSumParams.defaultClass);

        if (documentdbInfoConfig != null) {
            for (DocumentdbInfoConfig.Documentdb docDb : documentdbInfoConfig.documentdb()) {
                DocumentDatabase db = new DocumentDatabase(schemaInfo.schemas().get(docDb.name()));
                if (documentDbs.isEmpty())
                    defaultDocumentDb = db;
                documentDbs.put(docDb.name(), db);
            }
        }
    }

    protected void transformQuery(Query query) { }

    @Override
    public Result search(Query query, Execution execution) {
        // query root should not be null here
        Item root = query.getModel().getQueryTree().getRoot();
        if (root == null || root instanceof NullItem) {
            return new Result(query, ErrorMessage.createNullQuery(query.getUri().toString()));
        }

        if ( ! getDocumentDatabase(query).schema().rankProfiles().containsKey(query.getRanking().getProfile()))
            return new Result(query, ErrorMessage.createInvalidQueryParameter(getDocumentDatabase(query).schema() +
                                                                              " does not contain requested rank profile '" +
                                                                              query.getRanking().getProfile() + "'"));

        QueryRewrite.optimizeByRestrict(query);
        QueryRewrite.optimizeAndNot(query);
        QueryRewrite.collapseSingleComposites(query);

        root = query.getModel().getQueryTree().getRoot();
        if (root == null || root instanceof NullItem) // root can become null after optimization
            return new Result(query);

        resolveDocumentDatabase(query);
        transformQuery(query);
        traceQuery(name, "search", query, query.getOffset(), query.getHits(), 1, Optional.empty());

        root = query.getModel().getQueryTree().getRoot();
        if (root == null || root instanceof NullItem) // root can become null after resolving and transformation?
            return new Result(query);

        Result result = doSearch2(query, execution);

        if (query.getTrace().getLevel() >= 1)
            query.trace(getName() + " dispatch response: " + result, false, 1);
        result.trace(getName());
        return result;
    }

    private List<Result> partitionHits(Result result, String summaryClass) {
        List<Result> parts = new ArrayList<>();
        TinyIdentitySet<Query> queryMap = new TinyIdentitySet<>(4);

        for (Iterator<Hit> i = hitIterator(result); i.hasNext(); ) {
            Hit hit = i.next();
            if (hit instanceof FastHit fastHit) {
                if ( ! fastHit.isFilled(summaryClass)) {
                    Query q = fastHit.getQuery();
                    if (q == null) {
                        q = result.hits().getQuery(); // fallback for untagged hits
                    }
                    int idx = queryMap.indexOf(q);
                    if (idx < 0) {
                        idx = queryMap.size();
                        Result r = new Result(q);
                        parts.add(r);
                        queryMap.add(q);
                    }
                    parts.get(idx).hits().add(fastHit);
                }
            }
        }
        return parts;
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        if (result.isFilled(summaryClass)) return; // TODO: Checked in the superclass - remove

        List<Result> parts = partitionHits(result, summaryClass);
        if (parts.size() > 0) { // anything to fill at all?
            for (Result r : parts) {
                doPartialFill(r, summaryClass);
                mergeErrorsInto(result, r);
            }
            result.hits().setSorted(false);
            result.analyzeHits();
        }
    }

    private void mergeErrorsInto(Result destination, Result source) {
        destination.hits().addErrorsFrom(source.hits());
    }

    void traceQuery(String sourceName, String type, Query query, int offset, int hits, int level, Optional<String> quotedSummaryClass) {
        if ((query.getTrace().getLevel()<level) || !query.getTrace().getQuery()) return;

        StringBuilder s = new StringBuilder();
        s.append(sourceName).append(" ").append(type).append(" to dispatch: ")
                .append("query=[")
                .append(query.getModel().getQueryTree().getRoot().toString())
                .append("]");

        s.append(" timeout=").append(query.getTimeout()).append("ms");

        s.append(" offset=")
                .append(offset)
                .append(" hits=")
                .append(hits);

        if (query.getRanking().hasRankProfile()) {
            s.append(" rankprofile[")
                .append(query.getRanking().getProfile())
                .append("]");
        }

        if (query.getRanking().getFreshness() != null) {
            s.append(" freshness=")
                    .append(query.getRanking().getFreshness().getRefTime());
        }

        if (query.getRanking().getSorting() != null) {
            s.append(" sortspec=")
                    .append(query.getRanking().getSorting().fieldOrders().toString());
        }

        if (query.getRanking().getLocation() != null) {
            s.append(" location=")
                    .append(query.getRanking().getLocation().backendString());
        }

        if (query.getGroupingSessionCache()) {
            s.append(" groupingSessionCache=true");
        }
        if (query.getRanking().getQueryCache()) {
            s.append(" ranking.queryCache=true");
        }
        if (query.getGroupingSessionCache() || query.getRanking().getQueryCache()) {
            s.append(" sessionId=").append(query.getSessionId(getServerId()));
        }

        List<Grouping> grouping = GroupingExecutor.getGroupingList(query);
        s.append(" grouping=").append(grouping.size()).append(" : ");
        for(Grouping g : grouping) {
            s.append(g.toString());
        }

        if ( ! query.getRanking().getProperties().isEmpty()) {
            s.append(" rankproperties=")
                    .append(query.getRanking().getProperties().toString());
        }

        if ( ! query.getRanking().getFeatures().isEmpty()) {
            s.append(" rankfeatures=")
                    .append(query.getRanking().getFeatures().toString());
        }

        if (query.getModel().getRestrict() != null) {
            s.append(" restrict=").append(query.getModel().getRestrict().toString());
        }

        quotedSummaryClass.ifPresent((String summaryClass) -> s.append(" summary=").append(summaryClass));

        query.trace(s.toString(), false, level);
        if (query.getTrace().isTraceable(level + 1) && query.getTrace().getQuery()) {
            query.trace("Current state of query tree: "
                            + new TextualQueryRepresentation(query.getModel().getQueryTree().getRoot()),
                    false, level+1);
        }
        if (query.getTrace().isTraceable(level + 2) && query.getTrace().getQuery()) {
            query.trace("YQL+ representation: " + query.yqlRepresentation(), level+2);
        }
    }

    static class FillHitResult {
        final boolean ok;
        final String error;
        FillHitResult(boolean ok) {
            this(ok, null);
        }
        FillHitResult(boolean ok, String error) {
            this.ok = ok;
            this.error = error;
        }
    }

    private FillHitResult fillHit(FastHit hit, DocsumPacket packet, String summaryClass) {
        if (packet != null) {
            byte[] docsumdata = packet.getData();
            if (docsumdata.length > 0) {
                return new FillHitResult(true, decodeSummary(summaryClass, hit, docsumdata));
            }
        }
        return new FillHitResult(false);
    }

    static protected class FillHitsResult {
        public final int skippedHits; // Number of hits not producing a summary.
        public final String error; // Optional error message
        FillHitsResult(int skippedHits, String error) {
            this.skippedHits = skippedHits;
            this.error = error;
        }
    }
    /**
     * Fills the hits.
     *
     * @return the number of hits that we did not return data for, and an optional error message.
     *         when things are working normally we return 0.
     */
     protected FillHitsResult fillHits(Result result, DocsumPacket[] packets, String summaryClass) {
        int skippedHits = 0;
        String lastError = null;
        int packetIndex = 0;
        for (Iterator<Hit> i = hitIterator(result); i.hasNext();) {
            Hit hit = i.next();

            if (hit instanceof FastHit fastHit && ! hit.isFilled(summaryClass)) {
                DocsumPacket docsum = packets[packetIndex];

                packetIndex++;
                FillHitResult fr = fillHit(fastHit, docsum, summaryClass);
                if ( ! fr.ok ) {
                    skippedHits++;
                }
                if (fr.error != null) {
                    result.hits().addError(ErrorMessage.createTimeout(fr.error));
                    skippedHits++;
                    lastError = fr.error;
                }
            }
        }
        result.hits().setSorted(false);
        return new FillHitsResult(skippedHits, lastError);
    }

    protected DocsumDefinitionSet getDocsumDefinitionSet(Query query) {
        DocumentDatabase db = getDocumentDatabase(query);
        return db.getDocsumDefinitionSet();
    }

    private String decodeSummary(String summaryClass, FastHit hit, byte[] docsumdata) {
        DocumentDatabase db = getDocumentDatabase(hit.getQuery());
        hit.setField(Hit.SDDOCNAME_FIELD, db.schema().name());
        return decodeSummary(summaryClass, hit, docsumdata, db.getDocsumDefinitionSet());
    }

    private static String decodeSummary(String summaryClass, FastHit hit, byte[] docsumdata, DocsumDefinitionSet docsumSet) {
        String error = docsumSet.lazyDecode(summaryClass, docsumdata, hit);
        if (error == null) {
            hit.setFilled(summaryClass);
        }
        return error;
    }

    public void shutDown() { }

}
