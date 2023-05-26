package com.yahoo.config.provision;

import java.time.Duration;
import java.util.Objects;

import static ai.vespa.validation.Validation.requireAtLeast;

/**
 * Auxiliary information about a cluster, provided by the config model to the node repo during a
 * capacity request.
 *
 * @author bratseth
 */
public class ClusterInfo {

    private static final ClusterInfo empty = new ClusterInfo.Builder().build();

    private final Duration bcpDeadline;
    private final Duration hostTTL;

    private ClusterInfo(Builder builder) {
        this.bcpDeadline = builder.bcpDeadline;
        this.hostTTL = builder.hostTTL;
        requireAtLeast(hostTTL, "host TTL", Duration.ZERO);
    }

    public Duration bcpDeadline() { return bcpDeadline; }

    public Duration hostTTL() { return hostTTL; }

    public static ClusterInfo empty() { return empty; }

    public boolean isEmpty() { return this.equals(empty); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ClusterInfo other)) return false;
        if ( ! other.bcpDeadline.equals(this.bcpDeadline)) return false;
        if ( ! other.hostTTL.equals(this.hostTTL)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bcpDeadline, hostTTL);
    }

    @Override
    public String toString() {
        return "cluster info: [bcp deadline: " + bcpDeadline + ", host TTL: " + hostTTL + "]";
    }

    public static class Builder {

        private Duration bcpDeadline = Duration.ZERO;
        private Duration hostTTL = Duration.ZERO;

        public Builder bcpDeadline(Duration duration) {
            this.bcpDeadline = Objects.requireNonNull(duration);
            return this;
        }

        public Builder hostTTL(Duration duration) {
            this.hostTTL = Objects.requireNonNull(duration);
            return this;
        }

        public ClusterInfo build() {
            return new ClusterInfo(this);
        }

    }

}
