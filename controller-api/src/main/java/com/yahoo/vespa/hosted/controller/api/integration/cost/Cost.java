// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.cost;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.controller.common.NotFoundCheckedException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculate cost, hardware utilization and waste for applications.
 * <p>
 * Cost refers to the total cost ownership aka TCO.
 * <p>
 * We define a target utilization for each cluster in an application and compares this
 * to the actual utilization to get a number for ideal cost (if ideally scaled) and waste.
 * <p>
 * The target utilization is defined with the following in mind:
 * 1 Application stats to see contention on CPU above 80%
 * 2 It is scaled for a 50/50 load balancing between two zones (thus must be able to serve the other zone)
 * 3 Peaks are 2x average wrt CPU
 * 4 Memory contention is rising when over 80%
 *
 * @author smorgrav
 */
public interface Cost {

    /**
     * Get application costs for all applications across all zones
     *
     * @return A list of all application costs in all zones
     */
    default List<CostApplication> getApplicationCost() {
        List<CostApplication> costApplications = new ArrayList<>();
        getApplications().forEach((zone, list) -> {
            list.forEach(app -> {
                try {
                    costApplications.add(getApplicationCost(zone, app));
                } catch (NotFoundCheckedException e) {
                    // Application removed after fetched in getApplications ?
                    // TODO Log
                }
            });

        });

        return costApplications;
    }

    /**
     * Get application costs for a specific application deployement
     *
     * @param zone The zone - the combination of a environment and region e.g 'test.us-east-1'
     * @param app  ApplicationId e.g tenant:application:instance
     * @return A list of applications cost in given zone
     */
    default CostApplication getApplicationCost(Zone zone, ApplicationId app)
            throws NotFoundCheckedException {

        Map<String, CostClusterInfo> info = getClusterInfo(zone, app);
        Map<String, CostResources> util = getClusterUtilization(zone, app);
        CostResources target = getTargetUtilization(zone, app);

        Map<String, CostCluster> costClusters = new HashMap<>();
        for (String clusterId : util.keySet()) {
            costClusters.put(clusterId, new CostCluster(info.get(clusterId), util.get(clusterId), target));
        }

        return new CostApplication(zone, app, costClusters);
    }

    /**
     * Provides target utilization - default targets ARE XXX
     *
     * @param zone The zone - the combination of a environment and region e.g 'test.us-east-1'
     * @param app  ApplicationId e.g tenant:application:instance
     * @return Target utilization
     */
    default CostResources getTargetUtilization(Zone zone, ApplicationId app) {
        return new CostResources(0.8, 0.3, 0.4, 0.3);
    }

    /**
     * @return zone->app for all known zones and applications
     */
    Map<Zone, List<ApplicationId>> getApplications();

    /**
     * Provides information about the clusters in the application like
     * what hardware that it is using, the TCO for the hardware and number of hosts.
     *
     * @param zone The zone - the combination of a environment and region e.g 'test.us-east-1'
     * @param app  ApplicationId e.g tenant:application:instance
     * @return Map between clusterid -> costclusterinfo
     */
    Map<String, CostClusterInfo> getClusterInfo(Zone zone, ApplicationId app);

    /**
     * Provides ratio of available hardware used.
     * <p>
     * Used to calculate the utilization of the hardware.
     *
     * @param zone The zone - the combination of a environment and region e.g 'test.us-east-1'
     * @param app  ApplicationId e.g tenant:application:instance
     * @return Map between clusterid -> resource utilization
     */
    Map<String, CostResources> getClusterUtilization(Zone zone, ApplicationId app);
}
