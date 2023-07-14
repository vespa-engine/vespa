package com.yahoo.search.dispatch.searchcluster;

import com.yahoo.stream.CustomCollectors;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;

/**
 * Simple interface for groups and their nodes in the content cluster
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
    boolean isPartialGroupCoverageSufficient(Collection<Node> nodes);
}
