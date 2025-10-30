// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toCollection;

/**
 * Simple interface for groups and their nodes in the content cluster.
 *
 * @author baldersheim
 */
public interface SearchGroups {

    Group get(int id);

    Set<Integer> keys();

    Collection<Group> groups();

    default boolean isEmpty() {
        return size() == 0;
    }

    default Set<Node> nodes() {
        return groups().stream().flatMap(group -> group.nodes().stream())
                       .sorted(comparingInt(Node::key))
                       .collect(toCollection(LinkedHashSet::new));
    }

    int size();

    boolean isPartialGroupCoverageSufficient(boolean currentCoverageSufficient, Collection<Node> nodes);

    boolean hasSufficientCoverage(long groupDocumentCount, long documentCount);

    long maxDocumentCount();

}
