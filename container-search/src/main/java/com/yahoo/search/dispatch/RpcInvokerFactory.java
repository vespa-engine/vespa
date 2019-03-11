// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * @author ollivir
 */
public class RpcInvokerFactory extends InvokerFactory {
    /** Unless turned off this will fill summaries by dispatching directly to search nodes over RPC when possible */
    private final static CompoundName dispatchSummaries = new CompoundName("dispatch.summaries");

    private final RpcResourcePool rpcResourcePool;
    private final SearchCluster searchCluster;

    public RpcInvokerFactory(RpcResourcePool rpcResourcePool, SearchCluster searchCluster) {
        this.rpcResourcePool = rpcResourcePool;
        this.searchCluster = searchCluster;
    }

    @Override
    public Optional<SearchInvoker> createSearchInvoker(VespaBackEndSearcher searcher, Query query, OptionalInt groupId, List<Node> nodes,
            boolean acceptIncompleteCoverage) {
        List<SearchInvoker> invokers = new ArrayList<>(nodes.size());
        Set<Integer> failed = null;
        for (Node node : nodes) {
            if (node.isWorking()) {
                invokers.add(new RpcSearchInvoker(searcher, node, rpcResourcePool));
            } else {
                if (failed == null) {
                    failed = new HashSet<>();
                }
                failed.add(node.key());
            }
        }

        if (failed != null) {
            List<Node> success = new ArrayList<>(nodes.size() - failed.size());
            for (Node node : nodes) {
                if (!failed.contains(node.key())) {
                    success.add(node);
                }
            }
            if (!searchCluster.isPartialGroupCoverageSufficient(groupId, success)) {
                if (acceptIncompleteCoverage) {
                    invokers.add(createCoverageErrorInvoker(nodes, failed));
                } else {
                    return Optional.empty();
                }
            }
        }

        if (invokers.size() == 1) {
            return Optional.of(invokers.get(0));
        } else {
            return Optional.of(new InterleavedSearchInvoker(invokers, searcher, searchCluster));
        }
    }

    @Override
    public Optional<FillInvoker> createFillInvoker(VespaBackEndSearcher searcher, Result result) {
        Query query = result.getQuery();
        if (query.properties().getBoolean(dispatchSummaries, true)
                && ! searcher.summaryNeedsQuery(query)
                && query.getRanking().getLocation() == null)
        {
            return Optional.of(new RpcFillInvoker(rpcResourcePool, searcher.getDocumentDatabase(query)));
        } else {
            return Optional.empty();
        }
    }

    // for testing
    public FillInvoker createFillInvoker(DocumentDatabase documentDb) {
        return new RpcFillInvoker(rpcResourcePool, documentDb);
    }

    public void release() {
        rpcResourcePool.release();
    }
}
