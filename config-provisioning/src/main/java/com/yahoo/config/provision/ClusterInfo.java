package com.yahoo.config.provision;

import java.time.Duration;

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

    public static class Builder {

        private Duration bcpDeadline = Duration.ofMinutes(0);

        public Builder bcpDeadline(Duration duration) {
            this.bcpDeadline = duration;
            return this;
        }

        public ClusterInfo build() {
            return new ClusterInfo(this);
        }

    }

}
