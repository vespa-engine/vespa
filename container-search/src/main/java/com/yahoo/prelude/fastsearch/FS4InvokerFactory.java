// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.google.common.collect.ImmutableMap;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.CloseableInvoker;
import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.dispatch.InterleavedFillInvoker;
import com.yahoo.search.dispatch.InterleavedSearchInvoker;
import com.yahoo.search.dispatch.SearchCluster;
import com.yahoo.search.dispatch.SearchInvoker;
import com.yahoo.search.result.Hit;

import java.util.ArrayList;
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
        return new FS4SearchInvoker(searcher, query, fs4ResourcePool, node);
    }

    public Optional<SearchInvoker> getSearchInvoker(Query query, List<SearchCluster.Node> nodes) {
        return getInvoker(nodes, node -> getSearchInvoker(query, node), InterleavedSearchInvoker::new);
    }

    public FillInvoker getFillInvoker(Query query, SearchCluster.Node node) {
        return new FS4FillInvoker(searcher, query, fs4ResourcePool, node.hostname(), node.fs4port(), node.key());
    }

    public Optional<FillInvoker> getFillInvoker(Result result) {
        Collection<Integer> requiredNodes = requiredFillNodes(result);
        List<SearchCluster.Node> nodes = new ArrayList<>(requiredNodes.size());

        for (Integer distKey : requiredNodes) {
            SearchCluster.Node node = nodesByKey.get(distKey);
            if (node == null) {
                return Optional.empty();
            }
            nodes.add(node);
        }

        Query query = result.getQuery();
        return getInvoker(nodes, node -> getFillInvoker(query, node), InterleavedFillInvoker::new);
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

    @FunctionalInterface
    private interface InvokerConstructor<INVOKER> {
        INVOKER construct(SearchCluster.Node node);
    }

    @FunctionalInterface
    private interface ClusterInvokerConstructor<CLUSTERINVOKER extends INVOKER, INVOKER> {
        CLUSTERINVOKER construct(Map<Integer, INVOKER> subinvokers);
    }

    /* Get an invocation object for the provided collection of nodes. If only one
       node is used, only the single-node invoker is used. For multiple nodes, each
       gets a single-node invoker and they are all wrapped into a cluster invoker.
       The functional interfaces are used to allow code reuse with SearchInvokers
       and FillInvokers even though they don't share much class hierarchy. */
    private <INVOKER extends CloseableInvoker, CLUSTERINVOKER extends INVOKER> Optional<INVOKER> getInvoker(
            Collection<SearchCluster.Node> nodes, InvokerConstructor<INVOKER> singleNodeCtor,
            ClusterInvokerConstructor<CLUSTERINVOKER, INVOKER> clusterCtor) {
        if (nodes.size() == 1) {
            SearchCluster.Node node = nodes.iterator().next();
            return Optional.of(singleNodeCtor.construct(node));
        } else {
            Map<Integer, INVOKER> nodeInvokers = new HashMap<>();
            for (SearchCluster.Node node : nodes) {
                if (node.isWorking()) {
                    nodeInvokers.put(node.key(), singleNodeCtor.construct(node));
                }
            }
            if (nodeInvokers.size() == 1) {
                return Optional.of(nodeInvokers.values().iterator().next());
            } else {
                return Optional.of(clusterCtor.construct(nodeInvokers));
            }
        }
    }
}
