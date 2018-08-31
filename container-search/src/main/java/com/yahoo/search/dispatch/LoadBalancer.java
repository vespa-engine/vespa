package com.yahoo.search.dispatch;

import com.yahoo.search.Query;
import com.yahoo.search.dispatch.SearchCluster.Group;

import java.util.Optional;

public class LoadBalancer {

    private final SearchCluster searchCluster;

    public LoadBalancer(SearchCluster searchCluster) {
        this.searchCluster = searchCluster;
    }

    public Optional<Group> getGroupForQuery(Query query) {
        if (searchCluster.groups().size() == 1) {
            for(Group group: searchCluster.groups().values()) {
                // since the number of groups is 1, this will run only once
                if(group.nodes().size() == 1) {
                    return Optional.of(group);
                }
            }
        }
        return Optional.empty();
    }
}
