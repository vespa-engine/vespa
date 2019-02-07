// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.google.common.collect.ImmutableMap;
import com.yahoo.fs4.mplex.Backend;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.dispatch.InterleavedFillInvoker;
import com.yahoo.search.dispatch.InterleavedSearchInvoker;
import com.yahoo.search.dispatch.SearchErrorInvoker;
import com.yahoo.search.dispatch.SearchInvoker;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
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
    private final SearchCluster searchCluster;
    private final ImmutableMap<Integer, Node> nodesByKey;

    public FS4InvokerFactory(FS4ResourcePool fs4ResourcePool, SearchCluster searchCluster, VespaBackEndSearcher searcher) {
        this.fs4ResourcePool = fs4ResourcePool;
        this.searcher = searcher;
        this.searchCluster = searchCluster;

        ImmutableMap.Builder<Integer, Node> builder = ImmutableMap.builder();
        searchCluster.groups().values().forEach(group -> group.nodes().forEach(node -> builder.put(node.key(), node)));
        this.nodesByKey = builder.build();
    }

    public SearchInvoker getSearchInvoker(Query query, Node node) {
        Backend backend = fs4ResourcePool.getBackend(node.hostname(), node.fs4port());
        return new FS4SearchInvoker(searcher, query, backend.openChannel(), Optional.of(node));
    }

    /**
     * Create a {@link SearchInvoker} for a list of content nodes.
     *
     * @param query
     *            the search query being processed
     * @param groupId
     *            the id of the node group to which the nodes belong
     * @param nodes
     *            pre-selected list of content nodes
     * @param acceptIncompleteCoverage
     *            if some of the nodes are unavailable and this parameter is
     *            <b>false</b>, verify that the remaining set of nodes has enough
     *            coverage
     * @return Optional containing the SearchInvoker or <i>empty</i> if some node in the
     *         list is invalid and the remaining coverage is not sufficient
     */
    public Optional<SearchInvoker> getSearchInvoker(Query query, OptionalInt groupId, List<Node> nodes, boolean acceptIncompleteCoverage) {
        List<SearchInvoker> invokers = new ArrayList<>(nodes.size());
        Set<Integer> failed = null;
        for (Node node : nodes) {
            boolean nodeAdded = false;
            if (node.isWorking()) {
                Backend backend = fs4ResourcePool.getBackend(node.hostname(), node.fs4port());
                if (backend.probeConnection()) {
                    invokers.add(new FS4SearchInvoker(searcher, query, backend.openChannel(), Optional.of(node)));
                    nodeAdded = true;
                }
            }

            if (!nodeAdded) {
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

    private SearchInvoker createCoverageErrorInvoker(List<Node> nodes, Set<Integer> failed) {
        StringBuilder down = new StringBuilder("Connection failure on nodes with distribution-keys: ");
        int count = 0;
        for (Node node : nodes) {
            if (failed.contains(node.key())) {
                if (count > 0) {
                    down.append(", ");
                }
                count++;
                down.append(node.key());
            }
        }
        Coverage coverage = new Coverage(0, 0, 0);
        coverage.setNodesTried(count);
        return new SearchErrorInvoker(ErrorMessage.createBackendCommunicationError(down.toString()), coverage);
    }

    public FillInvoker getFillInvoker(Query query, Node node) {
        return new FS4FillInvoker(searcher, query, fs4ResourcePool, node.hostname(), node.fs4port());
    }

    /**
     * Create a {@link FillInvoker} for a the hits in a {@link Result}.
     *
     * @param result the Result containing hits that need to be filled
     * @return Optional containing the FillInvoker or <i>empty</i> if some hit is from an unknown content node
     */
    public Optional<FillInvoker> getFillInvoker(Result result) {
        Collection<Integer> requiredNodes = requiredFillNodes(result);

        Query query = result.getQuery();
        Map<Integer, FillInvoker> invokers = new HashMap<>();
        for (Integer distKey : requiredNodes) {
            Node node = nodesByKey.get(distKey);
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
