// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.component.Version;

import java.time.Instant;
import java.util.Objects;

/**
 * A stable OS version.
 *
 * @author mpolden
 */
public class StableOsVersion {

    private final Version version;
    private final Instant promotedAt;

    public StableOsVersion(Version version, Instant promotedAt) {
        this.version = Objects.requireNonNull(version);
        this.promotedAt = Objects.requireNonNull(promotedAt);
    }

    /** The version number */
    public Version version() {
        return version;
    }

    /** Returns the time this was promoted to stable */
    public Instant promotedAt() {
        return promotedAt;
    }

    @Override
    public String toString() {
        return "os version " + version + ", promoted at " + promotedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StableOsVersion that = (StableOsVersion) o;
        return version.equals(that.version) && promotedAt.equals(that.promotedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, promotedAt);
    }

}
