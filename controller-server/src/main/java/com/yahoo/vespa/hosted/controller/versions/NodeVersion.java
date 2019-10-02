// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.versions;

import com.yahoo.component.Version;
import com.yahoo.config.provision.HostName;

import java.time.Instant;
import java.util.Objects;

/**
 * Version information for a node allocated to a {@link com.yahoo.vespa.hosted.controller.application.SystemApplication}.
 *
 * This is immutable.
 *
 * @author mpolden
 */
public class NodeVersion {

    private final HostName hostname;
    private final Version version;
    private final Instant changedAt;

    public NodeVersion(HostName hostname, Version version, Instant changedAt) {
        this.hostname = Objects.requireNonNull(hostname, "hostname must be non-null");
        this.version = Objects.requireNonNull(version, "version must be non-null");
        this.changedAt = Objects.requireNonNull(changedAt, "changedAt must be non-null");
    }

    /** Hostname of this */
    public HostName hostname() {
        return hostname;
    }

    /** Current version of this */
    public Version version() {
        return version;
    }

    /** The most recent time the version of this changed */
    public Instant changedAt() {
        return changedAt;
    }

    @Override
    public String toString() {
        return hostname + " on " + version + " [changed at " + changedAt + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeVersion that = (NodeVersion) o;
        return hostname.equals(that.hostname) &&
               version.equals(that.version) &&
               changedAt.equals(that.changedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, version, changedAt);
    }

    public static NodeVersion empty(HostName hostname) {
        return new NodeVersion(hostname, Version.emptyVersion, Instant.EPOCH);
    }

}
