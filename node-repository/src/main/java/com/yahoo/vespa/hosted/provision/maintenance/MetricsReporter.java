// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.DockerHostCapacity;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author oyving
 */
public class MetricsReporter extends Maintainer {

    private final Metric metric;

    public MetricsReporter(NodeRepository nodeRepository, Metric metric, Duration interval, JobControl jobControl) {
        super(nodeRepository, interval, jobControl);
        this.metric = metric;
    }

    @Override
    public void maintain() {
        // Metrics pr state
        for (Node.State state : Node.State.values())
            metric.set("hostedVespa." + state.name() + "Hosts", 
                       nodeRepository().getNodes(NodeType.tenant, state).size(), null);

        // Capacity flavors for docker
        DockerHostCapacity capacity = new DockerHostCapacity(nodeRepository().getNodes(Node.State.values()));
        List<Flavor> dockerFlavors = nodeRepository().getAvailableFlavors().getFlavors().stream()
                .filter(f -> f.getType().equals(Flavor.Type.DOCKER_CONTAINER))
                .collect(Collectors.toList());
        metric.set("hostedVespa.docker.totalCapacityCpu", capacity.getCapacityTotal().getCpu(), null);
        metric.set("hostedVespa.docker.totalCapacityMem", capacity.getCapacityTotal().getMemory(), null);
        metric.set("hostedVespa.docker.totalCapacityDisk", capacity.getCapacityTotal().getDisk(), null);
        metric.set("hostedVespa.docker.freeCapacityCpu", capacity.getFreeCapacityTotal().getCpu(), null);
        metric.set("hostedVespa.docker.freeCapacityMem", capacity.getFreeCapacityTotal().getMemory(), null);
        metric.set("hostedVespa.docker.freeCapacityDisk", capacity.getFreeCapacityTotal().getDisk(), null);

        for (Flavor flavor : dockerFlavors) {
            Map<String, String> dimensions = new HashMap<>();
            dimensions.put("flavor", flavor.name());
            Metric.Context context = metric.createContext(dimensions);
            metric.set("hostedVespa.docker.freeCapacityFlavor", capacity.freeCapacityInFlavorEquivalence(flavor), context);
            metric.set("hostedVespa.docker.idealHeadroomFlavor", flavor.getIdealHeadroom(), context);
            metric.set("hostedVespa.docker.hostsAvailableFlavor", capacity.getNofHostsAvailableFor(flavor), context);
        }
    }
}
