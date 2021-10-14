// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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
}
