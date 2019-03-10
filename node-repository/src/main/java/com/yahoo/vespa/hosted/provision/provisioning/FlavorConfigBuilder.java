// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;

/**
 * Simplifies creation of a node-repository config containing flavors.
 * This is needed because the config builder API is inconvenient.
 *
 * @author bratseth
 */
public class FlavorConfigBuilder {

    private FlavorsConfig.Builder builder = new FlavorsConfig.Builder();

    public FlavorsConfig build() {
        return new FlavorsConfig(builder);
    }

    public FlavorsConfig.Flavor.Builder addFlavor(String flavorName, double cpu, double mem, double disk, Flavor.Type type) {
        FlavorsConfig.Flavor.Builder flavor = new FlavorsConfig.Flavor.Builder();
        flavor.name(flavorName);
        flavor.description("Flavor-name-is-" + flavorName);
        flavor.minDiskAvailableGb(disk);
        flavor.minCpuCores(cpu);
        flavor.minMainMemoryAvailableGb(mem);
        flavor.environment(type.name());
        builder.flavor(flavor);
        return flavor;
    }

    public FlavorsConfig.Flavor.Builder addNonStockFlavor(String flavorName, double cpu, double mem, double disk, Flavor.Type type) {
        FlavorsConfig.Flavor.Builder flavor = new FlavorsConfig.Flavor.Builder();
        flavor.name(flavorName);
        flavor.description("Flavor-name-is-" + flavorName);
        flavor.minDiskAvailableGb(disk);
        flavor.minCpuCores(cpu);
        flavor.minMainMemoryAvailableGb(mem);
        flavor.stock(false);
        flavor.environment(type.name());
        builder.flavor(flavor);
        return flavor;
    }

    public void addReplaces(String replaces, FlavorsConfig.Flavor.Builder flavor) {
        FlavorsConfig.Flavor.Replaces.Builder flavorReplaces = new FlavorsConfig.Flavor.Replaces.Builder();
        flavorReplaces.name(replaces);
        flavor.replaces(flavorReplaces);
    }

    public void addCost(int cost, FlavorsConfig.Flavor.Builder flavor) {
        flavor.cost(cost);
    }

    /** Convenience method which creates a node flavors instance from a list of flavor names */
    public static NodeFlavors createDummies(String... flavors) {

        FlavorConfigBuilder flavorConfigBuilder = new FlavorConfigBuilder();
        for (String flavorName : flavors) {
            if (flavorName.equals("docker"))
                flavorConfigBuilder.addFlavor(flavorName, 1. /* cpu*/, 3. /* mem GB*/, 2. /*disk GB*/, Flavor.Type.DOCKER_CONTAINER);
            else if (flavorName.equals("docker2"))
                flavorConfigBuilder.addFlavor(flavorName, 2. /* cpu*/, 4. /* mem GB*/, 4. /*disk GB*/, Flavor.Type.DOCKER_CONTAINER);
            else if (flavorName.equals("host"))
                flavorConfigBuilder.addFlavor(flavorName, 7. /* cpu*/, 10. /* mem GB*/, 12. /*disk GB*/, Flavor.Type.BARE_METAL);
            else
                flavorConfigBuilder.addFlavor(flavorName, 1. /* cpu*/, 3. /* mem GB*/, 2. /*disk GB*/, Flavor.Type.BARE_METAL);
        }
        return new NodeFlavors(flavorConfigBuilder.build());
    }
}
