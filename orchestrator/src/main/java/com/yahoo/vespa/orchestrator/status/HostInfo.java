// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * ZooKeeper-backed information Host status information about a host.
 *
 * @author hakonhall
 */
// @Immutable
public class HostInfo {
    private final HostStatus status;
    private final Optional<Instant> suspendedSince;

    public static HostInfo createSuspended(HostStatus status, Instant suspendedSince) {
        if (!status.isSuspended()) {
            throw new IllegalArgumentException(status + " is not a suspended-status");
        }

        return new HostInfo(status, Optional.of(suspendedSince));
    }

    public static HostInfo createNoRemarks() {
        return new HostInfo(HostStatus.NO_REMARKS, Optional.empty());
    }

    private HostInfo(HostStatus status, Optional<Instant> suspendedSince) {
        this.status = Objects.requireNonNull(status);
        this.suspendedSince = Objects.requireNonNull(suspendedSince);
    }

    public HostStatus status() { return status; }

    /**
     * The instant the host status was set to a suspended status. Is preserved when transitioning
     * between suspended statuses. Returns empty if and only if NO_REMARKS.
     */
    public Optional<Instant> suspendedSince() { return suspendedSince; }

    @Override
    public String toString() {
        return "HostInfo{" +
                ", status=" + status +
                ", suspendedSince=" + suspendedSince +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HostInfo hostInfo = (HostInfo) o;
        return status == hostInfo.status &&
                suspendedSince.equals(hostInfo.suspendedSince);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, suspendedSince);
    }
}
