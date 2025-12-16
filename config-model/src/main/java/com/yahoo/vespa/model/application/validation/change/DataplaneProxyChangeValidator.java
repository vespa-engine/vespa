// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.application.validation.change.VespaRestartAction.ConfigChange;
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
        var previousClusters = findAllClusters(context.previousModel());
        var currentClusters = findAllClusters(context.model());

        for (var entry : currentClusters.entrySet()) {
            var clusterId = entry.getKey();
            var currentCluster = entry.getValue();

            var previousCluster = previousClusters.get(clusterId);
            if (previousCluster == null) continue;

            boolean hadProxy = hasDataplaneProxy(previousCluster);
            boolean hasProxy = hasDataplaneProxy(currentCluster);

            if (hadProxy != hasProxy) {
                var message = hasProxy
                        ? "Token endpoint was enabled for cluster '%s', services require restart"
                        : "Token endpoint was disabled for cluster '%s', services require restart";
                context.require(createRestartAction(currentCluster, message.formatted(clusterId), ConfigChange.DEFER_UNTIL_RESTART));
            }
        }
    }

    private static Map<ClusterSpec.Id, ApplicationContainerCluster> findAllClusters(VespaModel model) {
        return model.getContainerClusters().values().stream()
                .collect(Collectors.toMap(ApplicationContainerCluster::id, cluster -> cluster));
    }

    private static boolean hasDataplaneProxy(ApplicationContainerCluster cluster) {
        return cluster.getAllComponents().stream()
                .anyMatch(component -> component.getClassId().getName().equals(DataplaneProxy.COMPONENT_CLASS));
    }

    private static VespaRestartAction createRestartAction(ApplicationContainerCluster cluster, String message,
                                                         ConfigChange configChange) {
        var services = cluster.getContainers().stream()
                .map(AbstractService::getServiceInfo)
                .toList();
        return new VespaRestartAction(cluster.id(), message, services, configChange);
    }
}
