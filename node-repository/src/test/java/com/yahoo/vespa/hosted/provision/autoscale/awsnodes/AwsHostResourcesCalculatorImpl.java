// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale.awsnodes;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.Nodelike;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author valerijf
 * @author bratseth
 */
public class AwsHostResourcesCalculatorImpl implements HostResourcesCalculator {

    private final Map<String, VespaFlavor> flavors;
    private final AwsResourcesCalculator resourcesCalculator;

    public AwsHostResourcesCalculatorImpl() {
        this.flavors = AwsNodeTypes.asVespaFlavors().stream().collect(Collectors.toMap(f -> f.name(), f -> f));
        this.resourcesCalculator = new AwsResourcesCalculator();
    }

    @Override
    public NodeResources realResourcesOf(Nodelike node, NodeRepository nodeRepository) {
        if (node.parentHostname().isEmpty()) return resourcesCalculator.realResourcesOfParentHost(node.resources()); // hosts use configured flavors

        Node parentHost = nodeRepository.nodes().node(node.parentHostname().get()).orElseThrow();
        VespaFlavor hostFlavor = flavors.get(parentHost.flavor().name());
        return resourcesCalculator.realResourcesOfChildContainer(node.resources(), hostFlavor);
    }

    @Override
    public NodeResources advertisedResourcesOf(Flavor flavor) {
        if ( ! flavor.isConfigured()) return flavor.resources(); // Node 'flavors' just wrap the advertised resources
        return flavors.get(flavor.name()).advertisedResources();
    }

    @Override
    public NodeResources requestToReal(NodeResources advertisedResources, boolean exclusive) {
        // Only consider exactly matched flavors if any to avoid concluding we have slightly too little resources
        // on an exactly matched flavor if we move from exclusive to shared hosts
        List<VespaFlavor> consideredFlavors = flavorsCompatibleWithAdvertised(advertisedResources, true);
        if (consideredFlavors.isEmpty())
            consideredFlavors = flavorsCompatibleWithAdvertised(advertisedResources, false);

        double memoryOverhead = consideredFlavors.stream()
                                                 .mapToDouble(flavor -> resourcesCalculator.memoryOverhead(flavor, advertisedResources, false))
                                                 .max().orElse(0);
        double diskOverhead   = consideredFlavors.stream()
                                                 .mapToDouble(flavor -> resourcesCalculator.diskOverhead(flavor, advertisedResources, false, exclusive))
                                                 .max().orElse(0);
        return advertisedResources.withMemoryGb(advertisedResources.memoryGb() - memoryOverhead)
                                  .withDiskGb(advertisedResources.diskGb() - diskOverhead);
    }

    @Override
    public NodeResources realToRequest(NodeResources realResources, boolean exclusive) {
        // Only consider exactly matched flavors if any to avoid concluding we have slightly too little resources
        // on an exactly matched flavor if we move from exclusive to shared hosts
        List<VespaFlavor> consideredFlavors = flavorsCompatibleWithReal(realResources, true);
        if (consideredFlavors.isEmpty())
            consideredFlavors = flavorsCompatibleWithReal(realResources, false);
        double worstMemoryOverhead = 0;
        double worstDiskOverhead = 0;
        for (VespaFlavor flavor : consideredFlavors) {
            double memoryOverhead = resourcesCalculator.memoryOverhead(flavor, realResources, true);
            double diskOverhead = resourcesCalculator.diskOverhead(flavor, realResources, true, exclusive);
            NodeResources advertised = realResources.withMemoryGb(realResources.memoryGb() + memoryOverhead)
                                                    .withDiskGb(realResources.diskGb() + diskOverhead);
            if ( ! flavor.advertisedResources().satisfies(advertised)) continue;
            if (memoryOverhead > worstMemoryOverhead)
                worstMemoryOverhead = memoryOverhead;
            if (diskOverhead > worstDiskOverhead)
                worstDiskOverhead = diskOverhead;
        }
        return realResources.withMemoryGb(realResources.memoryGb() + worstMemoryOverhead)
                            .withDiskGb(realResources.diskGb() + worstDiskOverhead);
    }

    @Override
    public long reservedDiskSpaceInBase2Gb(NodeType nodeType, boolean sharedHost) {
        return 1;
    }

    /** Returns the flavors of hosts which are eligible and matches the given advertised resources */
    private List<VespaFlavor> flavorsCompatibleWithAdvertised(NodeResources advertisedResources, boolean exactOnly) {
        return flavors.values().stream()
                      .filter(flavor -> exactOnly
                                        ? flavor.advertisedResources().equalsWhereSpecified(advertisedResources)
                                        : flavor.advertisedResources().satisfies(advertisedResources))
                      .toList();
    }

    /** Returns the flavors of hosts which are eligible and matches the given real resources */
    private List<VespaFlavor> flavorsCompatibleWithReal(NodeResources realResources, boolean exactOnly) {
        return flavors.values().stream()
                      .filter(flavor -> exactOnly
                                        ? resourcesCalculator.realResourcesOfChildContainer(flavor.advertisedResources(), flavor).compatibleWith(realResources)
                                        : flavor.realResources().satisfies(realResources))
                      .toList();
    }

}
