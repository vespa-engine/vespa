// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.provisioning.FlavorsConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A host flavor (type). This is a value object where the identity is the name.
 * Use {@link NodeFlavors} to create a flavor.
 *
 * @author bratseth
 */
public class Flavor {

    private boolean configured;

    /** The hardware resources of this flavor */
    private NodeResources resources;

    private final String name;
    private final int cost;
    private final boolean isStock;
    private final Type type;
    private final double minCpuCores;
    private final double minMainMemoryAvailableGb;
    private final double minDiskAvailableGb;
    private final boolean fastDisk;
    private final double bandwidth;
    private final String description;
    private final boolean retired;
    private List<Flavor> replacesFlavors;
    private int idealHeadroom; // Note: Not used after Vespa 6.282

    /**
     * Creates a Flavor, but does not set the replacesFlavors.
     *
     * @param flavorConfig config to be used for Flavor.
     */
    public Flavor(FlavorsConfig.Flavor flavorConfig) {
        this.configured = true;
        this.name = flavorConfig.name();
        this.replacesFlavors = new ArrayList<>();
        this.cost = flavorConfig.cost();
        this.isStock = flavorConfig.stock();
        this.type = Type.valueOf(flavorConfig.environment());
        this.minCpuCores = flavorConfig.minCpuCores();
        this.minMainMemoryAvailableGb = flavorConfig.minMainMemoryAvailableGb();
        this.minDiskAvailableGb = flavorConfig.minDiskAvailableGb();
        this.fastDisk = flavorConfig.fastDisk();
        this.bandwidth = flavorConfig.bandwidth();
        this.description = flavorConfig.description();
        this.retired = flavorConfig.retired();
        this.idealHeadroom = flavorConfig.idealHeadroom();
        this.resources = new NodeResources(minCpuCores, minMainMemoryAvailableGb, minDiskAvailableGb);
    }

    /** Create a Flavor from a Flavor spec and all other fields set to Docker defaults */
    public Flavor(NodeResources resources) {
        if (resources.allocateByLegacyName())
            throw new IllegalArgumentException("Can not create flavor '" + resources.legacyName() + "' from a flavor: " +
                                               "Non-docker flavors must be of a configured flavor");
        this.configured = false;
        this.name = resources.legacyName().orElse(resources.toString());
        this.cost = 0;
        this.isStock = true;
        this.type = Type.DOCKER_CONTAINER;
        this.minCpuCores = resources.vcpu();
        this.minMainMemoryAvailableGb = resources.memoryGb();
        this.minDiskAvailableGb = resources.diskGb();
        this.fastDisk = true;
        this.bandwidth = 1;
        this.description = "";
        this.retired = false;
        this.replacesFlavors = Collections.emptyList();
        this.resources = resources;
    }

    /** Returns the unique identity of this flavor */
    public String name() { return name; }

    /**
     * Get the monthly cost (total cost of ownership) in USD for this flavor, typically total cost
     * divided by 36 months.
     * 
     * @return monthly cost in USD
     */
    public int cost() { return cost; }
    
    public boolean isStock() { return isStock; }

    public double getMinMainMemoryAvailableGb() { return minMainMemoryAvailableGb; }

    public double getMinDiskAvailableGb() { return minDiskAvailableGb; }

    public boolean hasFastDisk() { return fastDisk; }

    public double getBandwidth() { return bandwidth; }

    public double getMinCpuCores() { return minCpuCores; }

    public String getDescription() { return description; }

    /** Returns whether the flavor is retired */
    public boolean isRetired() {
        return retired;
    }

    public Type getType() { return type; }
    
    /** Convenience, returns getType() == Type.DOCKER_CONTAINER */
    public boolean isDocker() { return type == Type.DOCKER_CONTAINER; }

    /** The free capacity we would like to preserve for this flavor */
    public int getIdealHeadroom() {
        return idealHeadroom;
    }

    /**
     * Returns the canonical name of this flavor - which is the name which should be used as an interface to users.
     * The canonical name of this flavor is:
     * <ul>
     *   <li>If it replaces one flavor, the canonical name of the flavor it replaces
     *   <li>If it replaces multiple or no flavors - itself
     * </ul>
     *
     * The logic is that we can use this to capture the gritty details of configurations in exact flavor names
     * but also encourage users to refer to them by a common name by letting such flavor variants declare that they
     * replace the canonical name we want. However, if a node replaces multiple names, we have no basis for choosing one
     * of them as the canonical, so we return the current as canonical.
     */
    public String canonicalName() {
        return isCanonical() ? name : replacesFlavors.get(0).canonicalName();
    }
    
    /** Returns whether this is a canonical flavor */
    public boolean isCanonical() {
        return replacesFlavors.size() != 1;
    }

    /**
     * The flavors this (directly) replaces.
     * This is immutable if this is frozen, and a mutable list otherwise.
     */
    public List<Flavor> replaces() { return replacesFlavors; }

    /**
     * Returns whether this flavor satisfies the requested flavor, either directly
     * (by being the same), or by directly or indirectly replacing it
     */
    public boolean satisfies(Flavor flavor) {
        if (this.equals(flavor)) {
            return true;
        }
        if (this.retired) {
            return false;
        }
        for (Flavor replaces : replacesFlavors)
            if (replaces.satisfies(flavor))
                return true;
        return false;
    }

    /** Irreversibly freezes the content of this */
    public void freeze() {
        replacesFlavors = ImmutableList.copyOf(replacesFlavors);
    }
    
    /** Returns whether this flavor has at least as much of each hardware resource as the given flavor */
    public boolean isLargerThan(Flavor other) {
        return this.minCpuCores >= other.minCpuCores &&
               this.minDiskAvailableGb >= other.minDiskAvailableGb &&
               this.minMainMemoryAvailableGb >= other.minMainMemoryAvailableGb &&
               this.fastDisk || ! other.fastDisk;
    }

    /**
     * True if this is a configured flavor used for hosts,
     * false if it is a virtual flavor created on the fly from node resources
     */
    public boolean isConfigured() { return configured; }

    public NodeResources resources() { return resources; }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof Flavor)) return false;
        return ((Flavor)other).name.equals(this.name);
    }

    @Override
    public String toString() { return "flavor '" + name + "'"; }

    public enum Type {
        undefined, // Default value in config (flavors.def)
        BARE_METAL,
        VIRTUAL_MACHINE,
        DOCKER_CONTAINER
    }

}
