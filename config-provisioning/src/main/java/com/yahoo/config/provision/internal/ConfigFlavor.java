// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.internal;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provisioning.FlavorsConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Flavor} generated from config.
 *
 * @author freva
 */
public class ConfigFlavor implements Flavor {

    private final String name;
    private final int cost;
    private final boolean isStock;
    private final Environment environment;
    private final Cpu cpu;
    private final Memory memory;
    private final Disk disk;
    private final Bandwidth bandwidth;
    private final boolean retired;
    private List<Flavor> replacesFlavors;

    /**
     * Creates a Flavor, but does not set the replacesFlavors.
     * @param flavorConfig config to be used for Flavor.
     */
    public ConfigFlavor(FlavorsConfig.Flavor flavorConfig) {
        this.name = flavorConfig.name();
        this.replacesFlavors = new ArrayList<>();
        this.cost = flavorConfig.cost();
        this.isStock = flavorConfig.stock();
        this.environment = Environment.valueOf(flavorConfig.environment());
        this.cpu = new ConfigCpu(flavorConfig.cpu());
        this.memory = new ConfigMemory(flavorConfig.memory());
        this.disk = new ConfigDisk(flavorConfig.disk());
        this.bandwidth = new ConfigBandwidth(flavorConfig.bandwidth());
        this.retired = flavorConfig.retired();
    }

    @Override
    public String flavorName() {
        return name;
    }

    @Override
    public int cost() {
        return cost;
    }

    @Override
    public boolean isStock() {
        return isStock;
    }

    @Override
    public boolean isRetired() {
        return retired;
    }

    @Override
    public Cpu cpu() {
        return cpu;
    }

    @Override
    public Memory memory() {
        return memory;
    }

    @Override
    public Disk disk() {
        return disk;
    }

    @Override
    public Bandwidth bandwidth() {
        return bandwidth;
    }

    @Override
    public Environment environment() {
        return environment;
    }

    @Override
    public List<Flavor> replaces() {
        return replacesFlavors;
    }

    /** Irreversibly freezes the content of this */
    public void freeze() {
        replacesFlavors = ImmutableList.copyOf(replacesFlavors);
    }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof ConfigFlavor)) return false;
        return ((ConfigFlavor)other).name.equals(this.name);
    }

    @Override
    public String toString() { return "flavor '" + name + "'"; }

    private class ConfigCpu implements Flavor.Cpu {
        private final double cores;

        private ConfigCpu(FlavorsConfig.Flavor.Cpu cpu) {
            this.cores = cpu.cores();
        }

        @Override
        public double cores() {
            return cores;
        }
    }

    private class ConfigMemory implements Flavor.Memory {
        private final double sizeInGb;

        private ConfigMemory(FlavorsConfig.Flavor.Memory memory) {
            this.sizeInGb = memory.sizeInGb();
        }

        @Override
        public double sizeInGb() {
            return sizeInGb;
        }
    }

    private class ConfigDisk implements Flavor.Disk {
        private final double sizeInBase10;
        private final boolean isFast;

        private ConfigDisk(FlavorsConfig.Flavor.Disk disk) {
            this.sizeInBase10 = disk.sizeInGb();
            this.isFast = disk.fast();
        }

        @Override
        public double sizeInBase10Gb() {
            return sizeInBase10;
        }

        @Override
        public boolean isFast() {
            return isFast;
        }
    }

    private class ConfigBandwidth implements Flavor.Bandwidth {
        private final double mbits;

        private ConfigBandwidth(FlavorsConfig.Flavor.Bandwidth bandwidth) {
            this.mbits = bandwidth.mbits();
        }

        @Override
        public double mbits() {
            return mbits;
        }
    }
}
