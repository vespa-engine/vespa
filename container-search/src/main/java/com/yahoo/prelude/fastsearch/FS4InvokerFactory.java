// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.google.common.collect.ImmutableMap;
import com.yahoo.fs4.mplex.Backend;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.dispatch.InterleavedFillInvoker;
import com.yahoo.search.dispatch.InterleavedSearchInvoker;
import com.yahoo.search.dispatch.SearchCluster;
import com.yahoo.search.dispatch.SearchInvoker;
import com.yahoo.search.result.Hit;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * FS4InvokerFactory constructs {@link FillInvoker} and {@link SearchInvoker} objects that communicate with
 * content nodes or dispatchers over the fnet/FS4 protocol
 *
 * @author ollivir
 */
public class FS4InvokerFactory {
    private final FS4ResourcePool fs4ResourcePool;
    private final VespaBackEndSearcher searcher;
    private final ImmutableMap<Integer, SearchCluster.Node> nodesByKey;

    public FS4InvokerFactory(FS4ResourcePool fs4ResourcePool, SearchCluster searchCluster, VespaBackEndSearcher searcher) {
        this.fs4ResourcePool = fs4ResourcePool;
        this.searcher = searcher;

        ImmutableMap.Builder<Integer, SearchCluster.Node> builder = ImmutableMap.builder();
        searchCluster.groups().values().forEach(group -> group.nodes().forEach(node -> builder.put(node.key(), node)));
        this.nodesByKey = builder.build();
    }

    public SearchInvoker getSearchInvoker(Query query, SearchCluster.Node node) {
        Backend backend = fs4ResourcePool.getBackend(node.hostname(), node.fs4port());
        return new FS4SearchInvoker(searcher, query, backend.openChannel(), node);
    }

    public Optional<SearchInvoker> getSearchInvoker(Query query, List<SearchCluster.Node> nodes) {
        Map<Integer, SearchInvoker> invokers = new HashMap<>();
        for (SearchCluster.Node node : nodes) {
            if (node.isWorking()) {
                Backend backend = fs4ResourcePool.getBackend(node.hostname(), node.fs4port());
                if (backend.probeConnection()) {
                    invokers.put(node.key(), new FS4SearchInvoker(searcher, query, backend.openChannel(), node));
                } else {
                    return Optional.empty();
                }
            }
        }
        if (invokers.size() == 1) {
            return Optional.of(invokers.values().iterator().next());
        } else {
            return Optional.of(new InterleavedSearchInvoker(invokers));
        }
    }

    public FillInvoker getFillInvoker(Query query, SearchCluster.Node node) {
        return new FS4FillInvoker(searcher, query, fs4ResourcePool, node.hostname(), node.fs4port(), node.key());
    }

    public Optional<FillInvoker> getFillInvoker(Result result) {
        Collection<Integer> requiredNodes = requiredFillNodes(result);

        Query query = result.getQuery();
        Map<Integer, FillInvoker> invokers = new HashMap<>();
        for (Integer distKey : requiredNodes) {
            SearchCluster.Node node = nodesByKey.get(distKey);
            if (node == null) {
                return Optional.empty();
            }
            invokers.put(distKey, getFillInvoker(query, node));
        }

        if (invokers.size() == 1) {
            return Optional.of(invokers.values().iterator().next());
        } else {
            return Optional.of(new InterleavedFillInvoker(invokers));
        }
    }

    private static Collection<Integer> requiredFillNodes(Result result) {
        Set<Integer> requiredNodes = new HashSet<>();
        for (Iterator<Hit> i = result.hits().unorderedDeepIterator(); i.hasNext();) {
            Hit h = i.next();
            if (h instanceof FastHit) {
                FastHit hit = (FastHit) h;
                requiredNodes.add(hit.getDistributionKey());
            }
        }
        return requiredNodes;
    }
}
