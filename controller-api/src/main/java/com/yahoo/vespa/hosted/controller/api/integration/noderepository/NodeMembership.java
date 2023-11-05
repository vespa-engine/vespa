// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * @author mpolden
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeMembership {

    @JsonProperty
    public String clustertype;
    @JsonProperty
    public String clusterid;
    @JsonProperty
    public String group;
    @JsonProperty
    public Integer index;
    @JsonProperty
    public Boolean retired;

    public String getClustertype() {
        return clustertype;
    }

    public String getClusterid() {
        return clusterid;
    }

    public String getGroup() {
        return group;
    }

    public Integer getIndex() {
        return index;
    }

    public Boolean getRetired() {
        return retired;
    }

    @Override
    public String toString() {
        return "NodeMembership{" +
               "clustertype='" + clustertype + '\'' +
               ", clusterid='" + clusterid + '\'' +
               ", group='" + group + '\'' +
               ", index=" + index +
               ", retired=" + retired +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeMembership that = (NodeMembership) o;
        return Objects.equals(clustertype, that.clustertype) &&
               Objects.equals(clusterid, that.clusterid) &&
               Objects.equals(group, that.group) &&
               Objects.equals(index, that.index) &&
               Objects.equals(retired, that.retired);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clustertype, clusterid, group, index, retired);
    }
}
