// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.google.common.collect.ImmutableMap;
import com.yahoo.fs4.mplex.Backend;
import com.yahoo.prelude.Pong;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.cluster.ClusterMonitor;
import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.dispatch.InterleavedFillInvoker;
import com.yahoo.search.dispatch.InvokerFactory;
import com.yahoo.search.dispatch.SearchInvoker;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.Pinger;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.search.result.Hit;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * FS4InvokerFactory constructs {@link FillInvoker} and {@link SearchInvoker} objects that communicate with
 * content nodes or dispatchers over the fnet/FS4 protocol
 *
 * @author ollivir
 */
public class FS4InvokerFactory extends InvokerFactory {
    private final FS4ResourcePool fs4ResourcePool;
    private final ImmutableMap<Integer, Node> nodesByKey;

    public FS4InvokerFactory(FS4ResourcePool fs4ResourcePool, SearchCluster searchCluster) {
        super(searchCluster);
        this.fs4ResourcePool = fs4ResourcePool;

        ImmutableMap.Builder<Integer, Node> builder = ImmutableMap.builder();
        searchCluster.groups().values().forEach(group -> group.nodes().forEach(node -> builder.put(node.key(), node)));
        this.nodesByKey = builder.build();
    }

    public SearchInvoker createDirectSearchInvoker(VespaBackEndSearcher searcher, Query query, Node node) {
        Backend backend = fs4ResourcePool.getBackend(node.hostname(), node.fs4port());
        return new FS4SearchInvoker(searcher, query, backend.openChannel(), Optional.of(node));
    }

    @Override
    protected Optional<SearchInvoker> createNodeSearchInvoker(VespaBackEndSearcher searcher, Query query, Node node) {
        Backend backend = fs4ResourcePool.getBackend(node.hostname(), node.fs4port());
        if (backend.probeConnection()) {
            return Optional.of(new FS4SearchInvoker(searcher, query, backend.openChannel(), Optional.of(node)));
        } else {
            return Optional.empty();
        }
    }

    public FillInvoker createFillInvoker(VespaBackEndSearcher searcher, Result result, Node node) {
        return new FS4FillInvoker(searcher, result.getQuery(), fs4ResourcePool, node.hostname(), node.fs4port());
    }

    /**
     * Create a {@link FillInvoker} for a the hits in a {@link Result}.
     *
     * @param searcher the searcher processing the query
     * @param result the Result containing hits that need to be filled
     * @return Optional containing the FillInvoker or <i>empty</i> if some hit is from an unknown content node
     */
    public Optional<FillInvoker> createFillInvoker(VespaBackEndSearcher searcher, Result result) {
        Collection<Integer> requiredNodes = requiredFillNodes(result);

        Map<Integer, FillInvoker> invokers = new HashMap<>();
        for (Integer distKey : requiredNodes) {
            Node node = nodesByKey.get(distKey);
            if (node == null) {
                return Optional.empty();
            }
            invokers.put(distKey, createFillInvoker(searcher, result, node));
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

    @Override
    public Callable<Pong> createPinger(Node node, ClusterMonitor<Node> monitor) {
        return new Pinger(node, monitor, fs4ResourcePool);
    }
}
