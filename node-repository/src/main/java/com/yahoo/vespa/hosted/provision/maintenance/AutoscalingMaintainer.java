// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.autoscale.AllocatableClusterResources;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaler;
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
            nodesByCluster(applicationNodes).forEach((clusterId, clusterNodes) -> autoscale(application, clusterId, clusterNodes, deployment));
        }
    }

    private void autoscale(ApplicationId applicationId,
                           ClusterSpec.Id clusterId,
                           List<Node> clusterNodes,
                           MaintenanceDeployment deployment) {
        Application application = nodeRepository().applications().get(applicationId, true);
        Cluster cluster = application.cluster(clusterId);
        if (cluster == null) return; // no information on limits for this cluster
        Optional<AllocatableClusterResources> target = autoscaler.autoscale(cluster, clusterNodes);
        if (target.isEmpty()) return; // current resources are fine

        if (cluster.minResources().equals(cluster.maxResources())) { // autoscaling is deactivated
            logAutoscaling("Scaling suggestion for ", target.get(), applicationId, clusterId, clusterNodes);
        }
        else {
            logAutoscaling("Autoscaling ", target.get(), applicationId, clusterId, clusterNodes);
            autoscaleTo(target.get(), applicationId, clusterId, application, deployment);
        }
    }

    private void autoscaleTo(AllocatableClusterResources target,
                             ApplicationId applicationId,
                             ClusterSpec.Id clusterId,
                             Application application,
                             MaintenanceDeployment deployment) {
        nodeRepository().applications().set(applicationId,
                                            application.withClusterTarget(clusterId, target.toAdvertisedClusterResources()),
                                            deployment.applicationLock().get());
        deployment.activate();
    }

    private void logAutoscaling(String prefix,
                                AllocatableClusterResources target,
                                ApplicationId application,
                                ClusterSpec.Id clusterId,
                                List<Node> clusterNodes) {
        Instant lastLogTime = lastLogged.get(new Pair<>(application, clusterId));
        if (lastLogTime != null && lastLogTime.isAfter(nodeRepository().clock().instant().minus(Duration.ofHours(1)))) return;

        int currentGroups = (int)clusterNodes.stream().map(node -> node.allocation().get().membership().cluster().group()).distinct().count();
        ClusterSpec.Type clusterType = clusterNodes.get(0).allocation().get().membership().cluster().type();
        log.info(prefix + application + " " + clusterType + " " + clusterId + ":" +
                 "\nfrom " + toString(clusterNodes.size(), currentGroups, clusterNodes.get(0).flavor().resources()) +
                 "\nto   " + toString(target.nodes(), target.groups(), target.advertisedResources()));
        lastLogged.put(new Pair<>(application, clusterId), nodeRepository().clock().instant());
    }

    private String toString(int nodes, int groups, NodeResources resources) {
        return String.format(nodes + (groups > 1 ? " (in " + groups + " groups)" : "") +
                             " * [vcpu: %0$.1f, memory: %1$.1f Gb, disk %2$.1f Gb]" +
                             " (total: [vcpu: %3$.1f, memory: %4$.1f Gb, disk: %5$.1f Gb])",
                             resources.vcpu(), resources.memoryGb(), resources.diskGb(),
                             nodes * resources.vcpu(), nodes * resources.memoryGb(), nodes * resources.diskGb());
    }

    private Map<ClusterSpec.Id, List<Node>> nodesByCluster(List<Node> applicationNodes) {
        return applicationNodes.stream().collect(Collectors.groupingBy(n -> n.allocation().get().membership().cluster().id()));
    }

}
