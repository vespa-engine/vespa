// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * @author mpolden
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeOwner {

    @JsonProperty
    public String tenant;
    @JsonProperty
    public String application;
    @JsonProperty
    public String instance;

    public NodeOwner() {}

    public String getTenant() {
        return tenant;
    }

    public String getApplication() {
        return application;
    }

    public String getInstance() {
        return instance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeOwner nodeOwner = (NodeOwner) o;
        return Objects.equals(tenant, nodeOwner.tenant) &&
               Objects.equals(application, nodeOwner.application) &&
               Objects.equals(instance, nodeOwner.instance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, application, instance);
    }
}
