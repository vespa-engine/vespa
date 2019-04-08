// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RotationStatus;
import com.yahoo.config.provision.zone.ZoneId;

import java.util.Map;

/**
 * A service which returns metric values on request
 *
 * @author bratseth
 */
public interface MetricsService {

    ApplicationMetrics getApplicationMetrics(ApplicationId application);

    DeploymentMetrics getDeploymentMetrics(ApplicationId application, ZoneId zone);

    /**
     * Get status for a global rotation
     * @param rotationName The fully qualified domain name of the rotation
     */
    Map<HostName, RotationStatus> getRotationStatus(String rotationName);

    Map<String, SystemMetrics> getSystemMetrics(ApplicationId application, ZoneId zone);

    class DeploymentMetrics {

        private final double queriesPerSecond;
        private final double writesPerSecond;
        private final long documentCount;
        private final double queryLatencyMillis;
        private final double writeLatencyMillis;

        public DeploymentMetrics(double queriesPerSecond, double writesPerSecond,
                                 long documentCount,
                                 double queryLatencyMillis, double writeLatencyMillis) {
            this.queriesPerSecond = queriesPerSecond;
            this.writesPerSecond = writesPerSecond;
            this.documentCount = documentCount;
            this.queryLatencyMillis = queryLatencyMillis;
            this.writeLatencyMillis = writeLatencyMillis;
        }

        public double queriesPerSecond() { return queriesPerSecond; }

        public double writesPerSecond() { return writesPerSecond; }

        public long documentCount() { return documentCount; }

        public double queryLatencyMillis() { return queryLatencyMillis; }

        public double writeLatencyMillis() { return writeLatencyMillis; }

    }

    class ApplicationMetrics {

        private final double queryServiceQuality;
        private final double writeServiceQuality;

        public ApplicationMetrics(double queryServiceQuality, double writeServiceQuality) {
            this.queryServiceQuality = queryServiceQuality;
            this.writeServiceQuality = writeServiceQuality;
        }

        /** Returns the quality of service for queries as a number between 1 (perfect) and 0 (none) */
        public double queryServiceQuality() { return queryServiceQuality; }

        /** Returns the quality of service for writes as a number between 1 (perfect) and 0 (none) */
        public double writeServiceQuality() { return writeServiceQuality; }

    }

    class SystemMetrics {

        private final double cpuUtil;
        private final double memUtil;
        private final double diskUtil;

        /**
         * @param cpuUtil  percentage of system cpu utilization
         * @param memUtil  percentage of system memory utilization
         * @param diskUtil percentage of system disk utilization
         */
        public SystemMetrics(double cpuUtil, double memUtil, double diskUtil) {
            this.cpuUtil = cpuUtil;
            this.memUtil = memUtil;
            this.diskUtil = diskUtil;
        }

        /** @return the percentage of cpu utilization **/
        public double cpuUtil() { return cpuUtil; }

        /** @return the percentage of memory utilization **/
        public double memUtil() { return memUtil; }

        /** @return the percentage of disk utilization **/
        public double diskUtil() { return diskUtil; }

        public static class Builder {
            private double cpuUtil;
            private double memUtil;
            private double diskUtil;

            public void setCpuUtil(double cpuUtil) {
                this.cpuUtil = cpuUtil;
            }

            public void setMemUtil(double memUtil) {
                this.memUtil = memUtil;
            }

            public void setDiskUtil(double diskUtil) {
                this.diskUtil = diskUtil;
            }

            public SystemMetrics build() { return new SystemMetrics(cpuUtil, memUtil, diskUtil); }
        }

    }

}
