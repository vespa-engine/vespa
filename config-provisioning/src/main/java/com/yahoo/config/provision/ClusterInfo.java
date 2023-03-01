package com.yahoo.config.provision;

import java.time.Duration;
import java.util.Objects;

/**
 * Auxiliary information about a cluster, provided by the config model to the node repo during a
 * capacity request.
 *
 * @author bratseth
 */
public class ClusterInfo {

    private static final ClusterInfo empty = new ClusterInfo.Builder().build();

    private final Duration bcpDeadline;

    private ClusterInfo(Builder builder) {
        this.bcpDeadline = builder.bcpDeadline;
    }

    public Duration bcpDeadline() { return bcpDeadline; }

    public static ClusterInfo empty() { return empty; }

    public boolean isEmpty() { return this.equals(empty); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ClusterInfo other)) return false;
        if ( ! other.bcpDeadline.equals(this.bcpDeadline)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bcpDeadline);
    }

    @Override
    public String toString() {
        return "cluster info: [bcp deadline: " + bcpDeadline + "]";
    }

    public static class Builder {

        private Duration bcpDeadline = Duration.ofMinutes(0);

        public Builder bcpDeadline(Duration duration) {
            this.bcpDeadline = Objects.requireNonNull(duration);
            return this;
        }

        public ClusterInfo build() {
            return new ClusterInfo(this);
        }

    }

}
