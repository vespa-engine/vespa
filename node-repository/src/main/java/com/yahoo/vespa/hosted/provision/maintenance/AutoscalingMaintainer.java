// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.autoscale.AllocatableClusterResources;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaler;
import com.yahoo.vespa.hosted.provision.autoscale.ClusterResources;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricsDb;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Maintainer making automatic scaling decisions
 *
 * @author bratseth
 */
public class AutoscalingMaintainer extends Maintainer {

    private final Autoscaler autoscaler;
    private final Deployer deployer;
    private final Map<Pair<ApplicationId, ClusterSpec.Id>, Instant> lastLogged = new HashMap<>();

    public AutoscalingMaintainer(NodeRepository nodeRepository,
                                 HostResourcesCalculator hostResourcesCalculator,
                                 NodeMetricsDb metricsDb,
                                 Deployer deployer,
                                 Duration interval) {
        super(nodeRepository, interval);
        this.autoscaler = new Autoscaler(hostResourcesCalculator, metricsDb, nodeRepository);
        this.deployer = deployer;
    }

    @Override
    protected void maintain() {
        if ( ! nodeRepository().zone().environment().isProduction()) return;

        activeNodesByApplication().forEach((applicationId, nodes) -> autoscale(applicationId, nodes));
    }

    private void autoscale(ApplicationId application, List<Node> applicationNodes) {
        try (MaintenanceDeployment deployment = new MaintenanceDeployment(application, deployer, nodeRepository())) {
            if ( ! deployment.isValid()) return; // Another config server will consider this application
            nodesByCluster(applicationNodes).forEach((clusterId, clusterNodes) -> autoscale(application, clusterId, clusterNodes));
        }
    }

    private void autoscale(ApplicationId application, ClusterSpec.Id clusterId, List<Node> clusterNodes) {
        Optional<AllocatableClusterResources> target = autoscaler.autoscale(clusterNodes);
        if (target.isEmpty()) return;

        Instant lastLogTime = lastLogged.get(new Pair<>(application, clusterId));
        if (lastLogTime != null && lastLogTime.isAfter(nodeRepository().clock().instant().minus(Duration.ofHours(1)))) return;

        int currentGroups = (int) clusterNodes.stream().map(node -> node.allocation().get().membership().cluster().group()).distinct().count();
        ClusterSpec.Type clusterType = clusterNodes.get(0).allocation().get().membership().cluster().type();
        log.info("Autoscale: " + application + " " + clusterType + " " + clusterId +
                 " from " + toString(clusterNodes.size(), currentGroups, clusterNodes.get(0).flavor().resources()) +
                 " to " + toString(target.get().nodes(), target.get().groups(), target.get().advertisedResources()));
        lastLogged.put(new Pair<>(application, clusterId), nodeRepository().clock().instant());
    }

    private String toString(int nodes, int groups, NodeResources resources) {
        return nodes +
               (groups > 1 ? " in " + groups + " groups " : " ") +
               " * " + resources +
               " (total: " + "[vcpu: " + nodes * resources.vcpu() + ", memory: " + nodes * resources.memoryGb() + " Gb, disk " + nodes * resources.diskGb() + " Gb])";
    }

    private Map<ClusterSpec.Id, List<Node>> nodesByCluster(List<Node> applicationNodes) {
        return applicationNodes.stream().collect(Collectors.groupingBy(n -> n.allocation().get().membership().cluster().id()));
    }

}
