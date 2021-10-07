// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the dispatch setup for a content cluster.
 * This EITHER has a number of dispatch groups OR a an explicit list of groups.
 *
 * @author geirst
 */
public class DispatchSpec {

    private final Integer numDispatchGroups;
    private final List<Group> groups;

    private DispatchSpec(Builder builder) {
        numDispatchGroups = builder.numDispatchGroups;
        groups = builder.groups;
    }

    public Integer getNumDispatchGroups() { return numDispatchGroups; }

    public List<Group> getGroups() {
        return groups;
    }

    public boolean valid() {
        return numDispatchGroups != null || groups != null;
    }

    /**
     * Reference to a node which is contained in a dispatch group.
     */
    public static class Node {
        private final int distributionKey;
        public Node(int distributionKey) {
            this.distributionKey = distributionKey;
        }
        public int getDistributionKey() {
            return distributionKey;
        }
    }

    /**
     * A dispatch group with a list of nodes contained in that group.
     */
    public static class Group {
        private final List<Node> nodes = new ArrayList<>();
        public Group() {

        }
        public Group addNode(Node node) {
            nodes.add(node);
            return this;
        }
        public List<Node> getNodes() {
            return nodes;
        }
    }

    public static class Builder {

        private Integer numDispatchGroups;
        private List<Group> groups;

        public DispatchSpec build() {
            return new DispatchSpec(this);
        }

        public Builder setNumDispatchGroups(Integer numDispatchGroups) {
            this.numDispatchGroups = numDispatchGroups;
            return this;
        }

        public Builder setGroups(List<Group> groups) {
            this.groups = groups;
            return this;
        }
    }
}
