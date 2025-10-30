// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import com.google.common.math.Quantiles;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author bratseth
 */
public class SearchGroupsImpl implements SearchGroups {

    private final AvailabilityPolicy availabilityPolicy;
    private final Map<Integer, Group> groups;

    public SearchGroupsImpl(AvailabilityPolicy availabilityPolicy, Map<Integer, Group> groups) {
        this.availabilityPolicy = availabilityPolicy;
        this.groups = Map.copyOf(groups);
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
            if (availabilityPolicy.prioritizeAvailability()) {
                // To take a group *out of* rotation, require that it has less active documents than the median.
                // This avoids scenarios where incorrect accounting in a single group takes all other groups offline.
                return hasSufficientCoverage(groupDocumentCount, medianDocumentCount);
            }
            else {
                // Only serve from groups that have the maximal coverage, prioritizing 100% coverage over availability
                // when there is a conflict.
                return hasSufficientCoverage(groupDocumentCount, maxDocumentCount);
            }
        }
        else {
            // to put a group *in* rotation, require that it has as many documents as the largest group,
            // to avoid taking groups in too early when the majority of the groups have just been added.
            return hasSufficientCoverage(groupDocumentCount, maxDocumentCount);
        }
    }

    public boolean hasSufficientCoverage(long groupDocumentCount, long documentCount) {
        double documentCoverage = 100.0 * (double) groupDocumentCount / documentCount;
        return documentCoverage >= availabilityPolicy.minActiveDocsPercentage();
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
