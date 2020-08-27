package com.yahoo.vespa.hosted.controller.api.integration.billing;

import java.util.Objects;

/**
 * Quota information transmitted to the configserver on deploy.
 */
public class Quota {

    private final int maxClusterSize;

    public Quota(int maxClusterSize) {
        this.maxClusterSize = maxClusterSize;
    }

    public int maxClusterSize() {
        return maxClusterSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Quota quota = (Quota) o;
        return maxClusterSize == quota.maxClusterSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxClusterSize);
    }
}
