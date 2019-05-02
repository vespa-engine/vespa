// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.internal.ConfigNodeFlavors;
import com.yahoo.config.provisioning.FlavorsConfig;

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

    public FlavorsConfig.Flavor.Builder addFlavor(String flavorName, double cpu, double mem, double disk, Flavor.Environment environment) {
        FlavorsConfig.Flavor.Builder flavor = new FlavorsConfig.Flavor.Builder()
                .name(flavorName)
                .disk(new FlavorsConfig.Flavor.Disk.Builder().sizeInGb(disk))
                .cpu(new FlavorsConfig.Flavor.Cpu.Builder().cores(cpu))
                .memory(new FlavorsConfig.Flavor.Memory.Builder().sizeInGb(mem))
                .environment(environment.name());
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
                flavorConfigBuilder.addFlavor(flavorName, 1. /* cpu*/, 3. /* mem GB*/, 2. /*disk GB*/, Flavor.Environment.DOCKER_CONTAINER);
            else if (flavorName.equals("docker2"))
                flavorConfigBuilder.addFlavor(flavorName, 2. /* cpu*/, 4. /* mem GB*/, 4. /*disk GB*/, Flavor.Environment.DOCKER_CONTAINER);
            else if (flavorName.equals("host"))
                flavorConfigBuilder.addFlavor(flavorName, 7. /* cpu*/, 10. /* mem GB*/, 12. /*disk GB*/, Flavor.Environment.BARE_METAL);
            else
                flavorConfigBuilder.addFlavor(flavorName, 1. /* cpu*/, 3. /* mem GB*/, 2. /*disk GB*/, Flavor.Environment.BARE_METAL);
        }
        return new ConfigNodeFlavors(flavorConfigBuilder.build());
    }
}
