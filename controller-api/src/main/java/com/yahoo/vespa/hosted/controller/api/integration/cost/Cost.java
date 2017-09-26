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
 * Calculate cost, hardware utilization and waste for Vespa.
 * <p>
 * Cost refers to the total cost ownership aka TCO.
 * <p>
 * We define a target utilization for each cluster in an application and compares this
 * to the actual utilization. Cost is then the TCO for the hardware used multiplied with
 * the relative utilization. Waste is the difference between target and ideal cost.
 *
 * @author smorgrav
 */
public interface Cost {

    /**
     * Get application costs for all applications across all zones.
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
     * Get application costs for a specific application.
     *
     * @param zone The zone - the combination of a environment and region e.g 'test.us-east-1'
     * @param app  ApplicationId e.g tenant:application:instance
     * @return The cost of one application
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
     * Provides target utilization - default targets are cased on the following assumptions:
     *
     * 1. CPU contention starts to be noticeable at 80% and spikes are 2x average
     * 2. Query load is perfectly load-balanced between two zones, cpu needs to handle fail-over - thus 2x load
     * 3. Memory contention (and especially sys cpu usage from memory management) increases after 90%
     * 4. Memory consumptions spikes over average with ~20%
     * 5. Memory and disk usage is independent of query load
     *
     * The default targets are:
     * CPU: 0.2
     * MEM: 0.7
     * DISK: 0.7
     * DISKBUSY: 0.3
     *
     * @param zone The zone - the combination of a environment and region e.g 'test.us-east-1'
     * @param app  ApplicationId e.g tenant:application:instance
     * @return Target utilization
     */
    default CostResources getTargetUtilization(Zone zone, ApplicationId app) {
        return new CostResources(0.7, 0.2, 0.7, 0.3);
    }

    /**
     * @return zone->app for all known zones and applications
     */
    Map<Zone, List<ApplicationId>> getApplications();

    /**
     * Provides information about the clusters in the application like
     * what hardware that it is using, the TCO for the hardware and the hostnames in the cluster.
     *
     * @param zone The zone - the combination of a environment and region e.g 'test.us-east-1'
     * @param app  ApplicationId e.g tenant:application:instance
     * @return Map between clusterid -> info
     */
    Map<String, CostClusterInfo> getClusterInfo(Zone zone, ApplicationId app);

    /**
     * Provides the ratio of available hardware used (e.g cpu, mem, disk) each in the range: [0,1].
     *
     * @param zone The zone - the combination of a environment and region e.g 'test.us-east-1'
     * @param app  ApplicationId e.g tenant:application:instance
     * @return Map between clusterid -> resource utilization
     */
    Map<String, CostResources> getClusterUtilization(Zone zone, ApplicationId app);
}
