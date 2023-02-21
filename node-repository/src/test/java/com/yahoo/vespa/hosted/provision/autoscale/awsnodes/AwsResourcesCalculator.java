// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale.awsnodes;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;

/**
 * Calculations and logic on node resources common to provision-service and host-admin (at least).
 *
 * @author hakon
 */
public class AwsResourcesCalculator {

    private final ReservedSpacePolicyImpl reservedSpacePolicy;
    private final double hostMemory = 0.6;

    public AwsResourcesCalculator() {
        this.reservedSpacePolicy = new ReservedSpacePolicyImpl();
    }

    /** The real resources of a parent host node in the node repository, given the real resources of the flavor. */
    public NodeResources realResourcesOfParentHost(NodeResources realResourcesOfFlavor) {
        return realResourcesOfFlavor;
    }

    /** The real resources of a child. */
    public NodeResources realResourcesOfChildContainer(NodeResources resources, VespaFlavor hostFlavor) {
        // This must match realResourcesOfChildSaturatingHost() if exclusive is true, and vice versa
        boolean exclusive = saturates(hostFlavor, resources);
        return resources.withMemoryGb(resources.memoryGb() - memoryOverhead(hostFlavor, resources, false))
                        .withDiskGb(resources.diskGb() - diskOverhead(hostFlavor, resources, false, exclusive));
    }

    /**
     * Returns the memory overhead resulting if the given resources are placed on the given node
     *
     * @param real true if the given resources are in real values, false if they are in advertised
     */
    public double memoryOverhead(VespaFlavor hostFlavor, NodeResources resources, boolean real) {
        double hostMemoryOverhead =
                hostFlavor.advertisedResources().memoryGb() - hostFlavor.realResources().memoryGb()
                + hostMemory; // Approximate cost of host administration processes

        if (hostMemoryOverhead > hostFlavor.advertisedResources().memoryGb()) // An unusably small flavor,
            return resources.memoryGb(); // all will be overhead
        double memoryShare = resources.memoryGb() /
                             ( hostFlavor.advertisedResources().memoryGb() - ( real ? hostMemoryOverhead : 0));
        if (memoryShare > 1) // The real resources of the host cannot fit the requested real resources after overhead
            memoryShare = 1;

        return hostMemoryOverhead * memoryShare;
    }

    /**
     * Returns the disk overhead resulting if the given advertised resources are placed on the given node
     *
     * @param real true if the resources are in real values, false if they are in advertised
     */
    public double diskOverhead(VespaFlavor flavor, NodeResources resources, boolean real, boolean exclusive) {
        if ( flavor.realResources().storageType() != NodeResources.StorageType.local) return 0;
        double hostDiskOverhead = reservedSpacePolicy.getPartitionSizeInBase2Gb(NodeType.host, ! exclusive);
        double diskShare = resources.diskGb() /
                           ( flavor.advertisedResources().diskGb() - ( real ? hostDiskOverhead : 0) );
        return hostDiskOverhead * diskShare;
    }

    /** Returns whether nodeResources saturates at least one resource dimension of hostFlavor */
    private boolean saturates(VespaFlavor hostFlavor, NodeResources nodeResources) {
        NodeResources hostResources = hostFlavor.advertisedResources();
        return equal(hostResources.vcpu(), nodeResources.vcpu()) ||
               equal(hostResources.memoryGb(), nodeResources.memoryGb()) ||
               equal(hostResources.diskGb(), nodeResources.diskGb()) ||
               equal(hostResources.bandwidthGbps(), nodeResources.bandwidthGbps());
    }

    private boolean equal(double a, double b) {
        return Math.abs(a - b) < 0.00000001;
    }

}
