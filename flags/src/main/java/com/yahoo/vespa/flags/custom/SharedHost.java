// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.flags.PermanentFlags;

import java.util.List;
import java.util.Objects;

/**
 * Defines properties related to shared hosts, see {@link PermanentFlags#SHARED_HOST}.
 *
 * @author hakon
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public class SharedHost {
    private final int DEFAULT_MIN_COUNT = 0;

    private final List<HostResources> resources;
    private final int minCount;

    public static SharedHost createDisabled() {
        return new SharedHost(null, null);
    }

    /**
     * @param resourcesOrNull the resources of the shared host (or several to support e.g. tiers or
     *                        fast/slow disks separately)
     * @param minCountOrNull  the minimum number of shared hosts
     */
    @JsonCreator
    public SharedHost(@JsonProperty("resources") List<HostResources> resourcesOrNull,
                      @JsonProperty("min-count") Integer minCountOrNull) {
        this.resources = resourcesOrNull == null ? List.of() : List.copyOf(resourcesOrNull);
        this.minCount = requireNonNegative(minCountOrNull, DEFAULT_MIN_COUNT, "min-count");
    }

    @JsonGetter("resources")
    public List<HostResources> getResourcesOrNull() {
        return resources.isEmpty() ? null : resources;
    }

    @JsonGetter("min-count")
    public Integer getMinCountOrNull() {
        return minCount == DEFAULT_MIN_COUNT ? null : minCount;
    }

    @JsonIgnore
    public boolean isEnabled() {
        return resources.size() > 0;
    }

    @JsonIgnore
    public List<HostResources> getHostResources() {
        return resources;
    }

    @JsonIgnore
    public int getMinCount() {
        return minCount;
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

    private int requireNonNegative(Integer integerOrNull, int defaultValue, String fieldName) {
        if (integerOrNull == null) {
            return defaultValue;
        }

        if (integerOrNull < 0) {
            throw new IllegalArgumentException(fieldName + " cannot be negative");
        }

        return integerOrNull;
    }
}
