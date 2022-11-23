package com.yahoo.search.dispatch.searchcluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GroupListImpl implements GroupList {
    private final Map<Integer, Group> groups;
    public GroupListImpl(Map<Integer, Group> groups) {
        this.groups = Map.copyOf(groups);
    }
    @Override public Group group(int id) { return groups.get(id); }
    @Override public Set<Integer> groupKeys() { return groups.keySet();}
    @Override public Collection<Group> groups() { return groups.values(); }
    @Override public int numGroups() { return groups.size(); }
    public static GroupList buildGroupListForTest(int numGroups, int nodesPerGroup) {
        return new GroupListImpl(buildGroupMapForTest(numGroups, nodesPerGroup));
    }
    public static Map<Integer, Group> buildGroupMapForTest(int numGroups, int nodesPerGroup) {
        Map<Integer, Group> groups = new HashMap<>();
        int distributionKey = 0;
        for (int group = 0; group < numGroups; group++) {
            List<Node> groupNodes = new ArrayList<>();
            for (int i = 0; i < nodesPerGroup; i++) {
                Node node = new Node(distributionKey, "host" + distributionKey, group);
                node.setWorking(true);
                groupNodes.add(node);
                distributionKey++;
            }
            Group g = new Group(group, groupNodes);
            groups.put(group, g);
        }
        return Map.copyOf(groups);
    }
}
