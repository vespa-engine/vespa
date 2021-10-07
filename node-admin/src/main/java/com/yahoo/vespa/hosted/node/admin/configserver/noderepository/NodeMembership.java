// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import java.util.Objects;

/**
 * @author freva
 */
public class NodeMembership {
    private final ClusterType clusterType;
    private final String clusterId;
    private final String group;
    private final int index;
    private final boolean retired;

    public NodeMembership(String clusterType, String clusterId, String group, int index, boolean retired) {
        this.clusterType = new ClusterType(clusterType);
        this.clusterId = clusterId;
        this.group = group;
        this.index = index;
        this.retired = retired;
    }

    public ClusterType type() {
        return clusterType;
    }

    public String clusterId() {
        return clusterId;
    }

    public String group() {
        return group;
    }

    public int index() { return index; }

    public boolean isRetired() {
        return retired;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NodeMembership that = (NodeMembership) o;

        if (index != that.index) return false;
        if (retired != that.retired) return false;
        if (!clusterType.equals(that.clusterType)) return false;
        if (!clusterId.equals(that.clusterId)) return false;
        return group.equals(that.group);

    }

    @Override
    public int hashCode() {
        int result = clusterType.hashCode();
        result = 31 * result + clusterId.hashCode();
        result = 31 * result + group.hashCode();
        result = 31 * result + index;
        result = 31 * result + (retired ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Membership {" +
                " clusterType = " + clusterType +
                " clusterId = " + clusterId +
                " group = " + group +
                " index = " + index +
                " retired = " + retired +
                " }";
    }

    public static class ClusterType {
        private final String type;

        private ClusterType(String type) {
            this.type = Objects.requireNonNull(type);
        }

        public boolean isAdmin() { return "admin".equals(type); }
        public boolean isContent() { return "content".equals(type); }
        public boolean isCombined() { return "combined".equals(type); }
        public boolean isContainer() { return "container".equals(type); }
        public boolean hasContainer() { return isContainer() || isCombined(); }
        public boolean hasContent() { return isContent() || isCombined(); }

        public String value() {
            return type;
        }

        @Override
        public String toString() {
            return type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ClusterType that = (ClusterType) o;
            return type.equals(that.type);
        }

        @Override
        public int hashCode() {
            return type.hashCode();
        }
    }
}
