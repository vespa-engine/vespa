// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.prelude.querytransform.QueryRewrite;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.dispatch.SearchInvoker;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.searchchain.Execution;

import java.io.IOException;
import java.util.Optional;

/**
 * The searcher which forwards queries to fdispatch nodes, using the fnet/fs4
 * network layer.
 *
 * @author  bratseth
 */
// TODO: Clean up all the duplication in the various search methods by
// switching to doing all the error handling using exceptions below doSearch2.
// Right now half is done by exceptions handled in doSearch2 and half by setting
// errors on results and returning them. It could be handy to create a QueryHandlingErrorException
// or similar which could wrap an error message, and then just always throw that and
// catch and unwrap into a results with an error in high level methods.  -Jon
public class FastSearcher extends VespaBackEndSearcher {

    /** Used to dispatch directly to search nodes over RPC, replacing the old fnet communication path */
    private final Dispatcher dispatcher;

    /**
     * Creates a Fastsearcher.
     *
     * @param serverId the resource pool used to create direct connections to the local search nodes when
     *                        bypassing the dispatch node
     * @param dispatcher the dispatcher used (when enabled) to send summary requests over the rpc protocol.
     *                   Eventually we will move everything to this protocol and never use dispatch nodes.
     *                   At that point we won't need a cluster searcher above this to select and pass the right
     *                   backend.
     * @param docSumParams  document summary parameters
     * @param clusterParams the cluster number, and other cluster backend parameters
     * @param documentdbInfoConfig document database parameters
     */
    public FastSearcher(String serverId,
                        Dispatcher dispatcher,
                        SummaryParameters docSumParams,
                        ClusterParams clusterParams,
                        DocumentdbInfoConfig documentdbInfoConfig,
                        SchemaInfo schemaInfo) {
        init(serverId, docSumParams, clusterParams, documentdbInfoConfig, schemaInfo);
        this.dispatcher = dispatcher;
    }

    /**
     * Pings the backend. Does not propagate to other searchers.
     */
    @Override
    public Pong ping(Ping ping, Execution execution) {
        throw new IllegalStateException("This ping should not have been called.");
    }

    @Override
    protected void transformQuery(Query query) {
        QueryRewrite.rewriteSddocname(query);
    }

    private void injectSource(HitGroup hits) {
        for (Hit hit : hits.asUnorderedHits()) {
            if (hit instanceof FastHit) {
                hit.setSource(getName());
            }
        }
    }

    @Override
    public Result doSearch2(Query query, Execution execution) {
        if (dispatcher.searchCluster().allGroupsHaveSize1())
            forceSinglePassGrouping(query);
        try (SearchInvoker invoker = getSearchInvoker(query)) {
            Result result = invoker.search(query, execution);
            injectSource(result.hits());

            if (query.properties().getBoolean(Ranking.RANKFEATURES, false)) {
                // There is currently no correct choice for which
                // summary class we want to fetch at this point. If we
                // fetch the one selected by the user it may not
                // contain the data we need. If we fetch the default
                // one we end up fetching docsums twice unless the
                // user also requested the default one.
                fill(result, query.getPresentation().getSummary(), execution); // ARGH
            }
            return result;
        } catch (TimeoutException e) {
            return new Result(query,ErrorMessage.createTimeout(e.getMessage()));
        } catch (IOException e) {
            Result result = new Result(query);
            if (query.getTrace().getLevel() >= 1)
                query.trace(getName() + " error response: " + result, false, 1);
            result.hits().addError(ErrorMessage.createBackendCommunicationError(getName() + " failed: "+ e.getMessage()));
            return result;
        }
    }

    /**
     * Perform a partial docsum fill for a temporary result
     * representing a partition of the complete fill request.
     *
     * @param result result containing a partition of the unfilled hits
     * @param summaryClass the summary class we want to fill with
     **/
    @Override
    protected void doPartialFill(Result result, String summaryClass) {
        if (result.isFilled(summaryClass)) return;

        Query query = result.getQuery();
        traceQuery(getName(), "fill", query, query.getOffset(), query.getHits(), 1, quotedSummaryClass(summaryClass));

        try (FillInvoker invoker = getFillInvoker(result)) {
            invoker.fill(result, summaryClass);
        }
    }

    /** When we only search a single node, doing all grouping in one pass is more efficient */
    private void forceSinglePassGrouping(Query query) {
        for (GroupingRequest groupingRequest : query.getSelect().getGrouping())
            forceSinglePassGrouping(groupingRequest.getRootOperation());
    }

    private void forceSinglePassGrouping(GroupingOperation operation) {
        operation.setForceSinglePass(true);
        for (GroupingOperation childOperation : operation.getChildren())
            forceSinglePassGrouping(childOperation);
    }

    /**
     * Returns an invocation object for use in a single search request. The specific implementation returned
     * depends on query properties with the default being an invoker that interfaces with a dispatcher
     * on the same host.
     */
    private SearchInvoker getSearchInvoker(Query query) {
        return dispatcher.getSearchInvoker(query, this);
    }

    /**
     * Returns an invocation object for use in a single fill request. The specific implementation returned
     * depends on query properties with the default being an invoker that uses RPC to interface with
     * content nodes.
     */
    private FillInvoker getFillInvoker(Result result) {
        return dispatcher.getFillInvoker(result, this);
    }

    private static Optional<String> quotedSummaryClass(String summaryClass) {
        return Optional.of(summaryClass == null ? "[null]" : "'" + summaryClass + "'");
    }

    public String toString() {
        return "fast searcher (" + getName() + ") ";
    }

}
