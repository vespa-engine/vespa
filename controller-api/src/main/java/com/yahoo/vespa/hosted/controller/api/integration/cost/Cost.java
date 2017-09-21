// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.cost;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.controller.common.NotFoundCheckedException;

import java.util.List;
import java.util.Map;

/**
 * Cost domain model.
 *
 * Cost refers to the total cost ownership aka TCO.
 *
 * We define a target utilization for each cluster in an application and compares this
 * to the actual utilization to get a number for ideal cost (if ideally scaled) and waste.
 *
 * The target utilization is defined with the following in mind:
 *   1 Application stats to see contention on CPU above 80%
 *   2 It is scaled for a 50/50 load balancing between two zones (thus must be able to serve the other zone)
 *   3 Peaks are 2x average wrt CPU
 *   4 Memory contention is rising when over 80%
 *
 * @author smorgrav
 */
public interface Cost {

    /**
     * Collect all information and format it as a CSV blob for download.
     *
     * @return A String with comma separated values. Can be big!
     */
    default String getCsvForLocalAnalysis() {
        return null;
    }

    /**
     * Get application costs for all applications across all zones
     *
     * @return A list of all application costs
     */
    default List<CostApplication> getApplicationCost() {
        return null;
    }

    /**
     * Get application costs for a given application instance in a given zone.
     *
     * @param env Environment like test, dev, perf, staging or prod
     * @param region Region name like us-east-1
     * @param app ApplicationId like tenant:application:instance
     *
     * @return A list of applications in given zone
     */
    default CostApplication getApplicationCost(Environment env, RegionName region, ApplicationId app)
        throws NotFoundCheckedException {
        return null;
    }

    /**
     * Provides target utilization - default targets ARE XXX
     *
     * @return
     */
    default CostHardwareInfo getUsageTarget(Environment env, RegionName region, ApplicationId app) {
        return new CostHardwareInfo(0.8, 0.3, 0.4, 0.3);
    }


    /**
     * Provides information about the clusters in the application like
     * what hardware that it is using, the TCO for the hardware and number of hosts.
     *
     * @return Map between clusterid -> costclusterinfo
     */
    Map<String, CostClusterInfo> getClusterInfo(Zone zone, ApplicationId app);

    /**
     * Provides measurements as absolute numbers of hardware resources.
     *
     * Used to calculate the utilization of the hardware.
     *
     * @return Map between clusterid -> costhardwareinfo
     */
    Map<String, CostHardwareInfo> getUsageMetrics(Zone zone, ApplicationId app);
}
