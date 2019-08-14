// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.config.provisioning.FlavorsConfig;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A host or node flavor.
 * *Host* flavors come from a configured set which corresponds to the actual flavors available in a zone.
 * *Node* flavors are simply a wrapper of a NodeResources object.
 *
 * @author bratseth
 */
public class Flavor {

    private boolean configured;
    private final String name;
    private final int cost;
    private final Type type;
    private final double bandwidth;

    /** The hardware resources of this flavor */
    private NodeResources resources;

    /** Creates a *host* flavor from configuration */
    public Flavor(FlavorsConfig.Flavor flavorConfig) {
        this.configured = true;
        this.name = flavorConfig.name();
        this.cost = flavorConfig.cost();
        this.type = Type.valueOf(flavorConfig.environment());
        this.resources = new NodeResources(flavorConfig.minCpuCores(),
                                           flavorConfig.minMainMemoryAvailableGb(),
                                           flavorConfig.minDiskAvailableGb(),
                                           flavorConfig.fastDisk() ? NodeResources.DiskSpeed.fast : NodeResources.DiskSpeed.slow);
        this.bandwidth = flavorConfig.bandwidth();
    }

    /** Creates a *node* flavor from a node resources spec */
    public Flavor(NodeResources resources) {
        Objects.requireNonNull(resources, "Resources cannot be null");
        this.configured = false;
        this.name = resources.toString();
        this.cost = 0;
        this.type = Type.DOCKER_CONTAINER;
        this.bandwidth = 1;
        this.resources = resources;
    }

    /** Returns the unique identity of this flavor if it is configured, or the resource spec string otherwise */
    public String name() { return name; }

    /**
     * Get the monthly cost (total cost of ownership) in USD for this flavor, typically total cost
     * divided by 36 months.
     * 
     * @return monthly cost in USD
     */
    public int cost() { return cost; }
    
    /**
     * True if this is a configured flavor used for hosts,
     * false if it is a virtual flavor created on the fly from node resources
     */
    public boolean isConfigured() { return configured; }

    public NodeResources resources() { return resources; }

    public double getMinMainMemoryAvailableGb() { return resources.memoryGb(); }

    public double getMinDiskAvailableGb() { return resources.diskGb(); }

    public boolean hasFastDisk() { return resources.diskSpeed() == NodeResources.DiskSpeed.fast; }

    public double getBandwidth() { return bandwidth; }

    public double getMinCpuCores() { return resources.vcpu(); }

    public Type getType() { return type; }
    
    /** Convenience, returns getType() == Type.DOCKER_CONTAINER */
    public boolean isDocker() { return type == Type.DOCKER_CONTAINER; }

    // TODO: Remove after August 2019
    public String canonicalName() { return name; }

    // TODO: Remove after August 2019
    public boolean satisfies(Flavor flavor) { return this.equals(flavor); }

    // TODO: Remove after August 2019
    public boolean isStock() { return false; }

    // TODO: Remove after August 2019
    public boolean isRetired() { return false; }

    // TODO: Remove after August 2019
    public boolean isCanonical() { return false; }

    // TODO: Remove after August 2019
    public List<Flavor> replaces() { return Collections.emptyList(); }

    // TODO: Remove after August 2019
    public void freeze() {}

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Flavor)) return false;
        Flavor other = (Flavor)o;
        if (configured)
            return other.name.equals(this.name);
        else
            return this.resources.equals(other.resources);
    }

    @Override
    public String toString() {
        if (isConfigured())
            return "flavor '" + name + "'";
        else
            return name;
    }

    public enum Type {
        undefined, // Default value in config (flavors.def)
        BARE_METAL,
        VIRTUAL_MACHINE,
        DOCKER_CONTAINER
    }

}
