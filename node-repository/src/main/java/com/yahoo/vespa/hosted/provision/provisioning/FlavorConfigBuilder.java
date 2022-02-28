// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provisioning.FlavorsConfig;

import static com.yahoo.config.provision.NodeResources.Architecture;

/**
 * Simplifies creation of a node-repository config containing flavors.
 * This is needed because the config builder API is inconvenient.
 *
 * @author bratseth
 */
public class FlavorConfigBuilder {

    private final FlavorsConfig.Builder builder = new FlavorsConfig.Builder();

    public FlavorsConfig build() {
        return new FlavorsConfig(builder);
    }

    public FlavorsConfig.Flavor.Builder addFlavor(String flavorName,
                                                  double cpu,
                                                  double mem,
                                                  double disk,
                                                  double bandwidth,
                                                  Flavor.Type type) {
        return addFlavor(flavorName, cpu, mem, disk, bandwidth, true, true, type, Architecture.x86_64);
    }

    public FlavorsConfig.Flavor.Builder addFlavor(String flavorName,
                                                  double cpu,
                                                  double mem,
                                                  double disk,
                                                  double bandwidth,
                                                  Flavor.Type type,
                                                  Architecture architecture) {
        return addFlavor(flavorName, cpu, mem, disk, bandwidth, true, true, type, architecture);
    }

    public FlavorsConfig.Flavor.Builder addFlavor(String flavorName,
                                                  double cpu,
                                                  double mem,
                                                  double disk,
                                                  double bandwidth,
                                                  boolean fastDisk,
                                                  boolean remoteStorage,
                                                  Flavor.Type type,
                                                  Architecture architecture) {
        FlavorsConfig.Flavor.Builder flavor = new FlavorsConfig.Flavor.Builder();
        flavor.name(flavorName);
        flavor.minDiskAvailableGb(disk);
        flavor.minCpuCores(cpu);
        flavor.minMainMemoryAvailableGb(mem);
        flavor.bandwidth(1000 * bandwidth);
        flavor.environment(type.name());
        flavor.fastDisk(fastDisk);
        flavor.remoteStorage(remoteStorage);
        flavor.architecture(architecture.name());
        builder.flavor(flavor);
        return flavor;
    }

    /** Convenience method which creates a node flavors instance from a list of flavor names */
    public static NodeFlavors createDummies(String... flavors) {

        FlavorConfigBuilder flavorConfigBuilder = new FlavorConfigBuilder();
        for (String flavorName : flavors) {
            if (flavorName.equals("docker"))
                flavorConfigBuilder.addFlavor(flavorName, 1., 30., 20., 1.5, Flavor.Type.DOCKER_CONTAINER);
            else if (flavorName.equals("docker2"))
                flavorConfigBuilder.addFlavor(flavorName, 2.,  40., 40., 0.5, Flavor.Type.DOCKER_CONTAINER);
            else if (flavorName.equals("host"))
                flavorConfigBuilder.addFlavor(flavorName, 7., 100., 120., 5, Flavor.Type.BARE_METAL);
            else if (flavorName.equals("host2"))
                flavorConfigBuilder.addFlavor(flavorName, 16, 24, 100, 1, Flavor.Type.BARE_METAL);
            else if (flavorName.equals("host3"))
                flavorConfigBuilder.addFlavor(flavorName, 24, 64, 100, 10, Flavor.Type.BARE_METAL);
            else if (flavorName.equals("host4"))
                flavorConfigBuilder.addFlavor(flavorName, 48, 128, 1000, 10, Flavor.Type.BARE_METAL);
            else if (flavorName.equals("devhost"))
                flavorConfigBuilder.addFlavor(flavorName, 4.,  80., 100, 10, Flavor.Type.BARE_METAL);
            else if (flavorName.equals("arm64"))
                flavorConfigBuilder.addFlavor(flavorName,2.,  30., 20., 3, Flavor.Type.BARE_METAL, Architecture.arm64);
            else
                flavorConfigBuilder.addFlavor(flavorName, 1.,  30., 20., 3, Flavor.Type.BARE_METAL);
        }
        return new NodeFlavors(flavorConfigBuilder.build());
    }

}
