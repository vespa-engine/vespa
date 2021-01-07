// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * @author ollivir
 */
public abstract class InvokerFactory {

    protected final SearchCluster searchCluster;

    public InvokerFactory(SearchCluster searchCluster) {
        this.searchCluster = searchCluster;
    }

    protected abstract Optional<SearchInvoker> createNodeSearchInvoker(VespaBackEndSearcher searcher,
                                                                       Query query,
                                                                       int maxHits,
                                                                       Node node);

    public abstract FillInvoker createFillInvoker(VespaBackEndSearcher searcher, Result result);

    /**
     * Create a {@link SearchInvoker} for a list of content nodes.
     *
     * @param searcher the searcher processing the query
     * @param query the search query being processed
     * @param groupId the id of the node group to which the nodes belong
     * @param nodes pre-selected list of content nodes
     * @param acceptIncompleteCoverage if some of the nodes are unavailable and this parameter is
     *                                 false, verify that the remaining set of nodes has sufficient coverage
     * @return the invoker or empty if some node in the
     *         list is invalid and the remaining coverage is not sufficient
     */
    Optional<SearchInvoker> createSearchInvoker(VespaBackEndSearcher searcher,
                                                Query query,
                                                OptionalInt groupId,
                                                List<Node> nodes,
                                                boolean acceptIncompleteCoverage,
                                                int maxHits) {
        List<SearchInvoker> invokers = new ArrayList<>(nodes.size());
        Set<Integer> failed = null;
        for (Node node : nodes) {
            boolean nodeAdded = false;
            if (node.isWorking() != Boolean.FALSE) {
                Optional<SearchInvoker> invoker = createNodeSearchInvoker(searcher, query, maxHits, node);
                if (invoker.isPresent()) {
                    invokers.add(invoker.get());
                    nodeAdded = true;
                }
            }

            if ( ! nodeAdded) {
                if (failed == null) {
                    failed = new HashSet<>();
                }
                failed.add(node.key());
            }
        }

        if (failed != null) {
            List<Node> success = new ArrayList<>(nodes.size() - failed.size());
            for (Node node : nodes) {
                if ( ! failed.contains(node.key())) {
                    success.add(node);
                }
            }
            if ( ! searchCluster.isPartialGroupCoverageSufficient(groupId, success) && !acceptIncompleteCoverage) {
                return Optional.empty();
            }
            if (invokers.size() == 0) {
                return Optional.of(createCoverageErrorInvoker(nodes, failed));
            }
        }

        if (invokers.size() == 1 && failed == null) {
            return Optional.of(invokers.get(0));
        } else {
            return Optional.of(new InterleavedSearchInvoker(invokers, searchCluster.isGroupWellBalanced(groupId), searchCluster, failed));
        }
    }

    protected static SearchInvoker createCoverageErrorInvoker(List<Node> nodes, Set<Integer> failed) {
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

    public void release() {}

}
