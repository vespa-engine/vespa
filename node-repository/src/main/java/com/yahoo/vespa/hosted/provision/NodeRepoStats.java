// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.autoscale.Load;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricSnapshot;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Stats about the current state known to this node repo
 *
 * @author bratseth
 */
public class NodeRepoStats {

    private final Load load;
    private final Load activeLoad;

    private NodeRepoStats(Load load, Load activeLoad) {
        this.load = load;
        this.activeLoad = activeLoad;
    }

    /**
     * Returns the current average work-extracting utilization in this node repo over all nodes.
     * Capacity not allocated to active nodes are taken to have 0 utilization as it provides no useful work.
     */
    public Load load() { return load; }

    /** Returns the current average utilization in this node repo over all active nodes. */
    public Load activeLoad() { return activeLoad; }

    public static NodeRepoStats computeOver(NodeRepository nodeRepository) {
        NodeList allNodes = nodeRepository.nodes().list();

        NodeResources totalActiveResources = new NodeResources(0, 0, 0, 0);
        double cpu = 0, memory = 0, disk = 0;
        for (var nodeTimeseries : nodeRepository.metricsDb().getNodeTimeseries(Duration.ofHours(1), Set.of())) {
            Optional<Node> node = allNodes.node(nodeTimeseries.hostname());
            if (node.isEmpty() || node.get().state() != Node.State.active) continue;

            Optional<NodeMetricSnapshot> snapshot = nodeTimeseries.last();
            if (snapshot.isEmpty()) continue;

            cpu += snapshot.get().cpu() * node.get().resources().vcpu();
            memory += snapshot.get().memory() * node.get().resources().memoryGb();
            disk += snapshot.get().disk() * node.get().resources().diskGb();
            totalActiveResources = totalActiveResources.add(node.get().resources().justNumbers());
        }

        NodeResources totalHostResources = new NodeResources(0, 0, 0, 0);
        for (var host : allNodes.hosts()) {
            totalHostResources = totalHostResources.add(host.resources().justNumbers());
        }

        Load load = new Load(divide(cpu, totalHostResources.vcpu()),
                             divide(memory, totalHostResources.memoryGb()),
                             divide(disk, totalHostResources.diskGb()));
        Load activeLoad = new Load(divide(cpu, totalActiveResources.vcpu()),
                                   divide(memory, totalActiveResources.memoryGb()),
                                   divide(disk, totalActiveResources.diskGb()));
        return new NodeRepoStats(load, activeLoad);
    }

    private static double divide(double a, double b) {
        if (a == 0 && b == 0) return 0;
        return a / b;
    }

}
