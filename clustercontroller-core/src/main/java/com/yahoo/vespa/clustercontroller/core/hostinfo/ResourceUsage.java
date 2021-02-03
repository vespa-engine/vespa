// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Encapsulation of the usage levels for a particular resource type. The resource type
 * itself is not tracked in this class; this must be done on a higher level.
 *
 * Note: equality checks and hash code computations do NOT include the actual floating
 * point usage! This is so sets of ResourceUsages are de-duplicated at the resource level
 * regardless of the relative usage (all cases where these are compared is assumed to
 * be when feed is blocked anyway, so just varying levels over the feed block limit).
 */
public class ResourceUsage {
    private final Double usage;
    private final String name;

    public ResourceUsage(@JsonProperty("usage") Double usage,
                         @JsonProperty("name") String name) {
        this.usage = usage;
        this.name = name;
    }

    /** Resource usage in [0, 1] */
    public Double getUsage() {
        return usage;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceUsage that = (ResourceUsage) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
