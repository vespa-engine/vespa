// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

/**
 * Checks that resources per document per node is reduced too much in one go.
 *
 * @author bratseth
 */
public class ResourcesReductionValidator implements ChangeValidator {

    private static final double maxAllowedUtilizationIncrease = 2.0; // Allow max doubling of utilization

    @Override
    public void validate(ChangeContext context) {
        for (var clusterId : context.previousModel().allClusters()) {
            if (context.model().allClusters().contains(clusterId))
                validate(clusterId, context);
        }
    }

    private void validate(ClusterSpec cluster, ChangeContext context) {
        ClusterResources current = clusterResources(cluster.id(), context.previousModel());
        ClusterResources next = clusterResources(cluster.id(), context.model());
        if (current == null || next == null) return;

        double loadIncreasePerNode;
        StringBuilder changes = new StringBuilder();
        if ( ! cluster.type().isContent()) {
            loadIncreasePerNode = (double)current.nodes() / next.nodes();
            if (current.nodes() != next.nodes())
                changes.append("from ").append(current.nodes()).append(" nodes to ")
                       .append(next.nodes()).append(" nodes").append(", ");
        }
        else { // take data size per node given from redundancy and groups into account
            ContentCluster currentCluster = context.previousModel().getContentClusters().get(cluster.id().value());
            ContentCluster nextCluster = context.model().getContentClusters().get(cluster.id().value());
            loadIncreasePerNode =
                (double) nextCluster.getRedundancy().finalRedundancy() / currentCluster.getRedundancy().finalRedundancy()
                *
                (double) currentCluster.groupSize() / nextCluster.groupSize();
            if (nextCluster.getRedundancy().finalRedundancy() != currentCluster.getRedundancy().finalRedundancy())
                changes.append("redundancy from ").append(currentCluster.getRedundancy().finalRedundancy())
                       .append(" to ").append(nextCluster.getRedundancy().finalRedundancy()).append(", ");
            if (nextCluster.groupSize() != currentCluster.groupSize())
                changes.append("group size from ").append(currentCluster.groupSize())
                       .append(" to ").append(nextCluster.groupSize()).append(" nodes, ");
        }

        if (current.nodeResources().isUnspecified() || next.nodeResources().isUnspecified()) {
            // Self-hosted: We don't know node resources so assume they are constant
            if (loadIncreasePerNode > maxAllowedUtilizationIncrease)
                invalid(changes, cluster, context);
        }
        else {
            NodeResources currentResources = current.nodeResources();
            NodeResources nextResources = next.nodeResources();
            if (invalid(currentResources.vcpu(), nextResources.vcpu(), "vcpu", loadIncreasePerNode, changes)
                || invalid(currentResources.memoryGiB(), nextResources.memoryGiB(), "memory GiB", loadIncreasePerNode, changes)
                || invalid(currentResources.diskGb(), nextResources.diskGb(), "disk Gb", loadIncreasePerNode, changes))
                invalid(changes, cluster, context);
        }
    }

    private boolean invalid(double current, double next, String resource, double loadIncreasePerNode,
                            StringBuilder changes) {
        if (loadIncreasePerNode * current / next > maxAllowedUtilizationIncrease) {
            if (current != next)
                changes.append("from ").append(current).append(" to ").append(next)
                       .append(" ").append(resource).append(" per node, ");
            return true;
        }
        return false;
    }

    private void invalid(StringBuilder changes, ClusterSpec cluster, ChangeContext context) {
        changes.setLength(changes.length() - 2); // Remove last ", "
        context.invalid(ValidationId.resourcesReduction,
                        "Effective resource reduction in " + cluster.id() + " is too large: " +
                        changes +
                        ". To protect against mistakes, changes causing load increases of more than 100% are blocked");
    }

    /**
     * If the given requested cluster resources does not specify node resources, return them with
     * the current node resources of the cluster, as that is what unspecified resources actually resolved to.
     * This will always yield specified node resources on hosted instances and never on self-hosted instances.
     */
    private ClusterResources clusterResources(ClusterSpec.Id id, VespaModel model) {
        if ( ! model.provisioned().capacities().containsKey(id)) return null;

        ClusterResources resources = model.provisioned().capacities().get(id).maxResources();
        if ( ! resources.nodeResources().isUnspecified()) return resources;

        var containerCluster = model.getContainerClusters().get(id.value());
        if (containerCluster != null) {
            if ( ! containerCluster.getContainers().isEmpty())
                return resources.with(containerCluster.getContainers().get(0).getHostResource().advertisedResources());
        }

        var contentCluster = model.getContentClusters().get(id.value());
        if (contentCluster != null) {
            var searchCluster = contentCluster.getSearch();
            if ( ! searchCluster.getSearchNodes().isEmpty())
                return resources.with(searchCluster.getSearchNodes().get(0).getHostResource().advertisedResources());
        }

        return resources; // only expected for admin clusters
    }

}
