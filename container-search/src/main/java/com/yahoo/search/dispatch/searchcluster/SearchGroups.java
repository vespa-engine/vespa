package com.yahoo.search.dispatch.searchcluster;

import java.util.Collection;
import java.util.Set;

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
    int size();
    boolean isPartialGroupCoverageSufficient(Collection<Node> nodes);
}
