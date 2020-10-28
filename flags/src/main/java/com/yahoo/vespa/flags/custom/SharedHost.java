// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.flags.Flags;

import java.util.List;
import java.util.Objects;

/**
 * Defines properties related to shared hosts, see {@link Flags#SHARED_HOST}.
 *
 * @author hakon
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class SharedHost {
    private final List<HostResources> resources;

    public static SharedHost createDisabled() {
        return new SharedHost(null);
    }

    @JsonCreator
    public SharedHost(@JsonProperty("resources") List<HostResources> resources) {
        this.resources = resources == null ? List.of() : List.copyOf(resources);
    }

    @JsonProperty("resources")
    public List<HostResources> getResourcesOrNull() {
        return resources.isEmpty() ? null : resources;
    }

    @JsonIgnore
    public List<HostResources> getHostResources() {
        return resources;
    }

    public boolean isEnabled() {
        return resources.size() > 0;
    }

    @Override
    public String toString() {
        return resources.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SharedHost that = (SharedHost) o;
        return resources.equals(that.resources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resources);
    }
}
