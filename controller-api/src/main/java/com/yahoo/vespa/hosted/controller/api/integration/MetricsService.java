// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;

/**
 * A service which returns metric values on request
 * 
 * @author bratseth
 */
public interface MetricsService {
    
    ApplicationMetrics getApplicationMetrics(ApplicationId application);

    DeploymentMetrics getDeploymentMetrics(ApplicationId application, Zone zone);

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
    
}
