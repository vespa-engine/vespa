package com.yahoo.config.application.api;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a (global) endpoint in 'deployments.xml'.  It contains the name of the
 * endpoint (endpointId) and the name of the container cluster that the endpoint
 * should point to.
 *
 * If the endpointId is not set, it will default to the same as the containerId.
 */
public class Endpoint {
    private final Optional<String> endpointId;
    private final String containerId;
    private final Set<String> regions;

    public Endpoint(Optional<String> endpointId, String containerId, Set<String> regions) {
        this.endpointId = endpointId;
        this.containerId = containerId;
        this.regions = Set.copyOf(regions);
    }

    public String endpointId() {
        return endpointId.orElse(containerId);
    }

    public String containerId() {
        return containerId;
    }

    public Set<String> regions() {
        return regions;
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
}
