// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.Node;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * @author ollivir
 */
public abstract class InvokerFactory {
    public abstract Optional<SearchInvoker> createSearchInvoker(VespaBackEndSearcher searcher, Query query, OptionalInt groupId,
            List<Node> nodes, boolean acceptIncompleteCoverage);

    public abstract Optional<FillInvoker> createFillInvoker(VespaBackEndSearcher searcher, Result result);

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
}
