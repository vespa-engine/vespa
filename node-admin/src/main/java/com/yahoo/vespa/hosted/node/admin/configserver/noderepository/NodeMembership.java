// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

/**
 * @author freva
 */
public class NodeMembership {
    private final String clusterType;
    private final String clusterId;
    private final String group;
    private final int index;
    private final boolean retired;

    public NodeMembership(String clusterType, String clusterId, String group, int index, boolean retired) {
        this.clusterType = clusterType;
        this.clusterId = clusterId;
        this.group = group;
        this.index = index;
        this.retired = retired;
    }

    public String getClusterType() {
        return clusterType;
    }

    public String getClusterId() {
        return clusterId;
    }

    public String getGroup() {
        return group;
    }

    public int getIndex() {
        return index;
    }

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
}
