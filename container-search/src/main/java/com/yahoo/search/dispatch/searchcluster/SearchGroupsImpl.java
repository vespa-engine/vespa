// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import com.google.common.math.Quantiles;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author baldersheim
 */
public class SearchGroupsImpl implements SearchGroups {

    private final Map<Integer, Group> groups;
    private final double minActiveDocsPercentage;

    public SearchGroupsImpl(Map<Integer, Group> groups, double minActiveDocsPercentage) {
        this.groups = Map.copyOf(groups);
        this.minActiveDocsPercentage = minActiveDocsPercentage;
    }

    @Override public Group get(int id) { return groups.get(id); }
    @Override public Set<Integer> keys() { return groups.keySet();}
    @Override public Collection<Group> groups() { return groups.values(); }
    @Override public int size() { return groups.size(); }

    @Override
    public boolean isPartialGroupCoverageSufficient(boolean currentIsGroupCoverageSufficient, Collection<Node> nodes) {
        if (size() == 1) return true;
        long groupDocumentCount = nodes.stream().mapToLong(Node::getActiveDocuments).sum();
        return isGroupCoverageSufficient(currentIsGroupCoverageSufficient,
                                         groupDocumentCount, medianDocumentCount(), maxDocumentCount());
    }

    public boolean isGroupCoverageSufficient(boolean currentIsGroupCoverageSufficient,
                                             long groupDocumentCount, long medianDocumentCount, long maxDocumentCount) {
        if (medianDocumentCount <= 0) return true;
        if (currentIsGroupCoverageSufficient) {
            // To take a group *out of* rotation, require that it has less active documents than the median.
            // This avoids scenarios where incorrect accounting in a single group takes all other groups offline.
            double documentCoverage = 100.0 * (double) groupDocumentCount / medianDocumentCount;
            return documentCoverage >= minActiveDocsPercentage;
        }
        else {
            // to put a group *in* rotation, require that it has as many documents as the largest group,
            // to avoid taking groups in too early when the majority of the groups have just been added.
            double documentCoverage = 100.0 * (double) groupDocumentCount / maxDocumentCount;
            return documentCoverage >= minActiveDocsPercentage;
        }
    }

    public long medianDocumentCount() {
        if (isEmpty()) return 0;
        double[] activeDocuments = groups().stream().mapToDouble(Group::activeDocuments).toArray();
        return (long) Quantiles.median().computeInPlace(activeDocuments);
    }

    public long maxDocumentCount() {
        return (long)groups().stream().mapToDouble(Group::activeDocuments).max().orElse(0);
    }

}
