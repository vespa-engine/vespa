// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;

import java.time.Instant;
import java.util.Collection;

/**
 * Interface to retrieve metrics on (tenant) nodes.
 *
 * @author bratseth
 */
public interface NodeMetrics {

    /**
     * Fetches metrics for an application. This call may be expensive.
     *
     * @param application the application to fetch metrics from
     */
    Collection<MetricValue> fetchMetrics(ApplicationId application);

    final class MetricValue {

        private final String hostname;
        private final long timestampSecond;
        private final double cpuUtil;
        private final double totalMemUtil;
        private final double diskUtil;
        private final double applicationGeneration;

        public MetricValue(String hostname, long timestampSecond,
                           double cpuUtil, double totalMemUtil, double diskUtil, double applicationGeneration) {
            this.hostname = hostname;
            this.timestampSecond = timestampSecond;
            this.cpuUtil = cpuUtil;
            this.totalMemUtil = totalMemUtil;
            this.diskUtil = diskUtil;
            this.applicationGeneration = applicationGeneration;
        }

        public String hostname() { return hostname; }
        public long timestampSecond() { return timestampSecond; }
        public double cpuUtil() { return cpuUtil; }
        public double totalMemUtil() { return totalMemUtil; }
        public double diskUtil() { return diskUtil; }
        public double applicationGeneration() { return applicationGeneration; }

        @Override
        public String toString() {
            return "node metrics for " + hostname + " at " + Instant.ofEpochSecond(timestampSecond) + ": " +
                   "cpuUtil: " + cpuUtil + ", " +
                   "totalMemUtil: " + totalMemUtil + ", " +
                   "diskUtil: " + diskUtil + ", " +
                   "applicationGeneration: " + applicationGeneration;
        }

    }

}
