package com.yahoo.search.dispatch.searchcluster;

import com.google.common.math.Quantiles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SearchGroupsImpl implements SearchGroups {
    private final Map<Integer, Group> groups;
    private final double minActivedocsPercentage;
    public SearchGroupsImpl(Map<Integer, Group> groups, double minActivedocsPercentage) {
        this.groups = Map.copyOf(groups);
        this.minActivedocsPercentage = minActivedocsPercentage;
    }
    @Override public Group get(int id) { return groups.get(id); }
    @Override public Set<Integer> keys() { return groups.keySet();}
    @Override public Collection<Group> groups() { return groups.values(); }
    @Override public int size() { return groups.size(); }
    @Override
    public boolean isPartialGroupCoverageSufficient(Collection<Node> nodes) {
        if (size() == 1)
            return true;
        long activeDocuments = nodes.stream().mapToLong(Node::getActiveDocuments).sum();
        return isGroupCoverageSufficient(activeDocuments, medianDocumentsPerGroup());
    }

    public boolean isGroupCoverageSufficient(long activeDocuments, long medianDocuments) {
        double documentCoverage = 100.0 * (double) activeDocuments / medianDocuments;
        return ! (medianDocuments > 0 && documentCoverage < minActivedocsPercentage);
    }

    public long medianDocumentsPerGroup() {
        if (isEmpty()) return 0;
        var activeDocuments = groups().stream().map(Group::activeDocuments).collect(Collectors.toList());
        return (long) Quantiles.median().compute(activeDocuments);
    }


    public static SearchGroupsImpl buildGroupListForTest(int numGroups, int nodesPerGroup, double minActivedocsPercentage) {
        return new SearchGroupsImpl(buildGroupMapForTest(numGroups, nodesPerGroup), minActivedocsPercentage);
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
