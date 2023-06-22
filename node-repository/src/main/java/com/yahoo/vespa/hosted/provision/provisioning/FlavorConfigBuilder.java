// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provisioning.FlavorsConfig;

import static com.yahoo.config.provision.Flavor.Type.BARE_METAL;
import static com.yahoo.config.provision.Flavor.Type.DOCKER_CONTAINER;
import static com.yahoo.config.provision.NodeResources.Architecture;
import static com.yahoo.config.provision.NodeResources.Architecture.arm64;
import static com.yahoo.config.provision.NodeResources.Architecture.x86_64;

/**
 * Simplifies creation of a node-repository config containing flavors.
 * This is needed because the config builder API is inconvenient.
 *
 * Note: Flavors added will have fast disk and remote storage unless explicitly specified.
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
        return addFlavor(flavorName, cpu, mem, disk, bandwidth, true, true, type, x86_64, 0, 0);
    }

    public FlavorsConfig.Flavor.Builder addFlavor(String flavorName,
                                                  double cpu,
                                                  double mem,
                                                  double disk,
                                                  double bandwidth,
                                                  Flavor.Type type,
                                                  Architecture architecture) {
        return addFlavor(flavorName, cpu, mem, disk, bandwidth, true, true, type, architecture, 0, 0);
    }

    public FlavorsConfig.Flavor.Builder addFlavor(String flavorName,
                                                  double cpu,
                                                  double mem,
                                                  double disk,
                                                  double bandwidth,
                                                  boolean fastDisk,
                                                  boolean remoteStorage,
                                                  Flavor.Type type,
                                                  Architecture architecture,
                                                  int gpuCount,
                                                  double gpuMemory) {
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
        flavor.gpuCount(gpuCount);
        flavor.gpuMemoryGb(gpuMemory);
        builder.flavor(flavor);
        return flavor;
    }

    /** Convenience method which creates a node flavors instance from a list of flavor names */
    public static NodeFlavors createDummies(String... flavors) {
        FlavorConfigBuilder builder = new FlavorConfigBuilder();
        for (String flavorName : flavors) {
            switch (flavorName) {
                case "docker" -> builder.addFlavor(flavorName, 1., 30., 20., 1.5, DOCKER_CONTAINER);
                case "host" -> builder.addFlavor(flavorName, 7., 100., 120., 5, BARE_METAL);
                case "host2" -> builder.addFlavor(flavorName, 16, 24, 100, 1, BARE_METAL);
                case "host3" -> builder.addFlavor(flavorName, 24, 64, 100, 10, BARE_METAL);
                case "host4" -> builder.addFlavor(flavorName, 48, 128, 1000, 10, BARE_METAL);
                case "arm64" -> builder.addFlavor(flavorName, 2., 30., 20., 3, BARE_METAL, arm64);
                case "gpu" -> builder.addFlavor(flavorName, 4, 16, 125, 10, true, false, BARE_METAL, x86_64, 1, 16);
                default -> builder.addFlavor(flavorName, 1., 30., 20., 3, BARE_METAL);
            }
        }
        return new NodeFlavors(builder.build());
    }

}
