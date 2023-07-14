package com.yahoo.search.dispatch.searchcluster;

import com.google.common.math.Quantiles;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

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
        if (medianDocuments <= 0) return true;
        double documentCoverage = 100.0 * (double) activeDocuments / medianDocuments;
        return documentCoverage >= minActivedocsPercentage;
    }

    public long medianDocumentsPerGroup() {
        if (isEmpty()) return 0;
        double[] activeDocuments = groups().stream().mapToDouble(Group::activeDocuments).toArray();
        return (long) Quantiles.median().computeInPlace(activeDocuments);
    }

}
