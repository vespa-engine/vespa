// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.fs4.QueryPacket;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.Node;
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
    public Result search(Query query, QueryPacket queryPacket, Execution execution) throws IOException {
        sendSearchRequest(query, queryPacket);
        Result result = getSearchResult(execution);
        setFinalStatus(result.hits().getError() == null);
        return result;
    }

    protected abstract void sendSearchRequest(Query query, QueryPacket queryPacket) throws IOException;

    protected abstract Result getSearchResult(Execution execution) throws IOException;

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
}
