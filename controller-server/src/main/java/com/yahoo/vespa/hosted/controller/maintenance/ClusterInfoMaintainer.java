// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.application.ClusterInfo;
import com.yahoo.vespa.hosted.controller.application.Deployment;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Maintains information about hardware, hostnames and cluster specifications.
 *
 * This is used to calculate cost metrics for the application api.
 *
 * @author smorgrav
 */
public class ClusterInfoMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(ClusterInfoMaintainer.class.getName());
    
    private final Controller controller;
    private final NodeRepository nodeRepository;

    ClusterInfoMaintainer(Controller controller, Duration duration, JobControl jobControl) {
        super(controller, duration, jobControl);
        this.controller = controller;
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
    }

    private static String clusterId(NodeRepositoryNode node) {
        return node.getMembership().clusterid;
    }

    private Map<ClusterSpec.Id, ClusterInfo> getClusterInfo(List<Node> nodes) {
        Map<ClusterSpec.Id, ClusterInfo> infoMap = new HashMap<>();

        // Group nodes by clusterid
        Map<String, List<Node>> clusters = nodes.stream().collect(Collectors.groupingBy(Node::clusterId));

        // For each cluster - get info
        for (String id : clusters.keySet()) {
            List<Node> clusterNodes = clusters.get(id);

            // Assume they are all equal and use first node as a representative for the cluster
            Node node = clusterNodes.get(0);

            // Add to map
            List<String> hostnames = clusterNodes.stream()
                                                 .map(Node::hostname)
                                                 .map(HostName::value)
                                                 .collect(Collectors.toList());
            ClusterInfo info = new ClusterInfo(node.canonicalFlavor(), node.cost(),
                                               node.resources().vcpu(), node.resources().memoryGb(), node.resources().diskGb(),
                                               ClusterSpec.Type.from(node.clusterType().name()), hostnames);
            infoMap.put(new ClusterSpec.Id(id), info);
        }

        return infoMap;
    }

    @Override
    protected void maintain() {
        for (Application application : controller().applications().asList()) {
            for (Instance instance : application.instances().values()) {
                for (Deployment deployment : instance.deployments().values()) {
                    DeploymentId deploymentId = new DeploymentId(instance.id(), deployment.zone());
                    try {
                        var nodes = nodeRepository.list(deploymentId.zoneId(), deploymentId.applicationId());
                        Map<ClusterSpec.Id, ClusterInfo> clusterInfo = getClusterInfo(nodes);
                        controller().applications().lockApplicationIfPresent(application.id(), lockedApplication ->
                                controller.applications().store(lockedApplication.with(instance.name(),
                                                                                       locked -> locked.withClusterInfo(deployment.zone(), clusterInfo))));
                    }
                    catch (Exception e) {
                        log.log(Level.WARNING, "Failing getting cluster information for " + deploymentId, e);
                    }
                }
            }
        }
    }

}
