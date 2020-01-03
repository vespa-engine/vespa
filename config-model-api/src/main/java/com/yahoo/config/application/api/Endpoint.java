// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.config.provision.RegionName;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Represents a (global) endpoint in 'deployments.xml'.  It contains the name of the
 * endpoint (endpointId) and the name of the container cluster that the endpoint
 * should point to.
 *
 * If the endpoint is not set it will default to the string "default".
 *
 * @author ogronnesby
 */
public class Endpoint {

    /*
     * Endpoint IDs must be:
     * - lowercase
     * - alphanumeric
     * - begin with a character
     * - contain zero consecutive dashes
     * - have a length between 1 and 12
     */
    private static final Pattern endpointPattern = Pattern.compile("^[a-z](?:-?[a-z0-9]+)*$");
    private static final int endpointMaxLength = 12;
    private static final String defaultEndpointId = "default";

    private final Optional<String> endpointId;
    private final String containerId;
    private final Set<RegionName> regions;

    public Endpoint(Optional<String> endpointId, String containerId, Set<String> regions) {
        this.endpointId = Objects.requireNonNull(endpointId, "endpointId must be non-null");
        this.containerId = Objects.requireNonNull(containerId, "containerId must be non-null");
        this.regions = Set.copyOf(
                Objects.requireNonNull(
                        regions.stream().map(RegionName::from).collect(Collectors.toList()),
                        "Missing 'regions' parameter"));

        if (endpointId().length() > endpointMaxLength || !endpointPattern.matcher(endpointId()).matches()) {
            throw new IllegalArgumentException("Invalid endpoint ID: '" + endpointId() + "'");
        }
    }

    public String endpointId() {
        return endpointId.orElse(defaultEndpointId);
    }

    public String containerId() {
        return containerId;
    }

    public Set<RegionName> regions() {
        return regions;
    }

    public Endpoint withRegions(Set<String> regions) {
        return new Endpoint(endpointId, containerId, regions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Endpoint endpoint = (Endpoint) o;
        return Objects.equals(endpointId, endpoint.endpointId) &&
                Objects.equals(containerId, endpoint.containerId) &&
                Objects.equals(regions, endpoint.regions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpointId, containerId, regions);
    }

    @Override
    public String toString() {
        return "endpoint '" + endpointId() + "' (cluster " + containerId + ") -> " +
               regions.stream().map(RegionName::value).sorted().collect(Collectors.joining(", "));
    }

}
