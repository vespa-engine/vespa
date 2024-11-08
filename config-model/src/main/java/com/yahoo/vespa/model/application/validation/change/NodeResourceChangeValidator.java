// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Emits restart change actions for clusters where the node resources are changed in a way
 * which requires a "restart" (container recreation) to take effect.
 * Nodes will restart on their own on this condition, but we want to emit restart actions to
 * defer applying new config until restart.
 *
 * @author bratseth
 */
public class NodeResourceChangeValidator implements ChangeValidator {

    @Override
    public void validate(ChangeContext context) {
        for (ClusterSpec cluster : context.previousModel().allClusters()) {
            Optional<NodeResources> currentResources = resourcesOf(cluster, context.previousModel());
            Optional<NodeResources> nextResources = resourcesOf(cluster, context.model());
            if (currentResources.isEmpty() || nextResources.isEmpty()) continue; // new or removed cluster
            if ( changeRequiresRestart(currentResources.get(), nextResources.get()))
                createRestartActionsFor(cluster, context.previousModel()).forEach(context::require);
        }
    }

    private boolean changeRequiresRestart(NodeResources currentResources, NodeResources nextResources) {
        return currentResources.memoryGiB() != nextResources.memoryGiB();
    }

    private Optional<NodeResources> resourcesOf(ClusterSpec cluster, VespaModel model) {
        return model.allocatedHosts().getHosts().stream().filter(host -> host.membership().isPresent())
                                                         .filter(host -> host.membership().get().cluster().id().equals(cluster.id()))
                                                         .findFirst()
                                                         .map(HostSpec::advertisedResources);
    }

    private List<ConfigChangeAction> createRestartActionsFor(ClusterSpec cluster, VespaModel model) {
        ApplicationContainerCluster containerCluster = model.getContainerClusters().get(cluster.id().value());
        if (containerCluster != null)
            return createRestartActionsFor(containerCluster);

        ContentCluster contentCluster = model.getContentClusters().get(cluster.id().value());
        if (contentCluster != null)
            return createRestartActionsFor(contentCluster);

        return List.of();
    }

    private List<ConfigChangeAction> createRestartActionsFor(ApplicationContainerCluster cluster) {
        return cluster.getContainers().stream()
                                      .map(container -> new VespaRestartAction(cluster.id(),
                                                                               "Node resource change",
                                                                               container.getServiceInfo(),
                                                                               false))
                                     .collect(Collectors.toList());
    }

    private List<ConfigChangeAction> createRestartActionsFor(ContentCluster cluster) {
        return cluster.getSearch().getSearchNodes().stream()
                                                   .map(node -> new VespaRestartAction(cluster.id(),
                                                                                       "Node resource change",
                                                                                       node.getServiceInfo(),
                                                                                      false))
                                                   .collect(Collectors.toList());
    }

}
