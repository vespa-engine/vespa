// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;

import java.io.IOException;
import java.util.Optional;

/**
 * SearchInvoker encapsulates an allocated connection for running a single search query.
 * The invocation object can be stateful and should not be reused.
 *
 * @author ollivir
 */
public abstract class SearchInvoker extends CloseableInvoker {

    private final Optional<Node> node;
    private ResponseMonitor<SearchInvoker> monitor;

    protected SearchInvoker(Optional<Node> node) {
        this.node = node;
    }

    /**
     * Retrieve the hits for the given {@link Query}. If the search is run on multiple content
     * nodes, the provided {@link Execution} may be used to retrieve document summaries required
     * for correct result windowing.
     */
    public Result search(Query query, Execution execution) throws IOException {
        sendSearchRequest(query, null);
        InvokerResult result = getSearchResult(execution);
        setFinalStatus(result.getResult().hits().getError() == null);
        result.complete();
        return result.getResult();
    }

    /**
     *
     * @param query the query to send
     * @param context a context object that can be used to pass context among different
     *                invokers, e.g for reuse of preserialized data.
     * @return an object that can be passed to the next invocation of sendSearchRequest
     */
    protected abstract Object sendSearchRequest(Query query, Object context) throws IOException;

    protected abstract InvokerResult getSearchResult(Execution execution) throws IOException;

    protected void setMonitor(ResponseMonitor<SearchInvoker> monitor) {
        this.monitor = monitor;
    }

    protected void responseAvailable() {
        if (monitor != null) {
            monitor.responseAvailable(this);
        }
    }

    protected Optional<Integer> distributionKey() {
        return node.map(Node::key);
    }

    protected InvokerResult errorResult(Query query, ErrorMessage errorMessage) {
        Result error = new Result(query, errorMessage);
        Coverage errorCoverage = new Coverage(0, 0, 0);
        errorCoverage.setNodesTried(1);
        error.setCoverage(errorCoverage);
        return new InvokerResult(error);
    }

}
