package com.yahoo.search.dispatch.searchcluster;

import java.util.Collection;
import java.util.Set;

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
    default Set<Node> nodes() { return groups().stream().flatMap(group -> group.nodes().stream()).collect(toSet());}
    int size();
    boolean isPartialGroupCoverageSufficient(Collection<Node> nodes);
}
