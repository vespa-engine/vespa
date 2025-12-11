// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.DataplaneProxy;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ensures that application container clusters are restarted when data plane proxy is added or removed.
 *
 * @author bjorncs
 */
public class DataplaneProxyChangeValidator implements ChangeValidator {

    @Override
    public void validate(ChangeContext context) {
        var previousClustersWithProxy = findClustersWithDataplaneProxy(context.previousModel());
        var nextClustersWithProxy = findClustersWithDataplaneProxy(context.model());
        var nextClusters = findAllClusters(context.model());

        // Detect additions
        for (var entry : nextClustersWithProxy.entrySet()) {
            if (!previousClustersWithProxy.containsKey(entry.getKey())) {
                var cluster = entry.getValue();
                cluster.setDeferChangesUntilRestart(true);
                var message = "Token endpoint was enabled for cluster '%s', services require restart"
                        .formatted(entry.getKey());
                context.require(createRestartAction(cluster, message));
            }
        }

        // Detect removals
        for (var clusterId : previousClustersWithProxy.keySet()) {
            if (!nextClustersWithProxy.containsKey(clusterId)) {
                var cluster = nextClusters.get(clusterId);
                cluster.setDeferChangesUntilRestart(true);
                var message = "Token endpoint was disabled for cluster '%s', services require restart"
                        .formatted(clusterId);
                context.require(createRestartAction(cluster, message));
            }
        }
    }

    private static Map<ClusterSpec.Id, ApplicationContainerCluster> findAllClusters(VespaModel model) {
        return model.getContainerClusters().values().stream()
                .collect(Collectors.toMap(ApplicationContainerCluster::id, cluster -> cluster));
    }

    private static Map<ClusterSpec.Id, ApplicationContainerCluster> findClustersWithDataplaneProxy(VespaModel model) {
        return model.getContainerClusters().values().stream()
                .filter(cluster -> cluster.getAllComponents().stream()
                        .anyMatch(component -> component.getClassId().getName().equals(DataplaneProxy.COMPONENT_CLASS)))
                .collect(Collectors.toMap(ApplicationContainerCluster::id, cluster -> cluster));
    }

    private static VespaRestartAction createRestartAction(ApplicationContainerCluster cluster, String message) {
        var services = cluster.getContainers().stream()
                .map(AbstractService::getServiceInfo)
                .toList();
        return new VespaRestartAction(cluster.id(), message, services);
    }
}
