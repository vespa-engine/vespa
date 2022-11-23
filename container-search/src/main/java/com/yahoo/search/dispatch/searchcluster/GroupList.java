package com.yahoo.search.dispatch.searchcluster;

import java.util.Collection;
import java.util.Set;

/**
 * Simple interface for groups and their nodes in the content cluster
 * @author baldersheim
 */
public interface GroupList {
    Group group(int id);
    Set<Integer> groupKeys();
    Collection<Group> groups();
    default boolean isEmpty() {
        return numGroups() == 0;
    }
    int numGroups();
}
