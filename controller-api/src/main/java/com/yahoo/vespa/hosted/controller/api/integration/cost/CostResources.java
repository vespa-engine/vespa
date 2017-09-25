package com.yahoo.vespa.hosted.controller.api.integration.cost;// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * Value object for system resources as ratios of available resources.
 *
 * Can be for actual readings or target numbers.
 *
 * @author smorgrav
 */
public class CostResources {

    private final double memory;
    private final double cpu;
    private final double disk;
    private final double diskBusy;
    private final double maxUtilization;

    public CostResources(double memory, double cpu, double disk, double diskBusy) {
        this.memory = memory;
        this.cpu = cpu;
        this.disk = disk;
        this.diskBusy = diskBusy;

        double maxUtil = Math.max(cpu, disk);
        maxUtil = Math.max(maxUtil, memory);
        this.maxUtilization = Math.max(maxUtil, diskBusy);
    }

    public double getMaxUtilization() {
        return maxUtilization;
    }

    public double getMemory() {
        return memory;
    }

    public double getCpu() {
        return cpu;
    }

    public double getDisk() {
        return disk;
    }

    public double getDiskBusy() {
        return diskBusy;
    }

    private void validateUsageRatio(float ratio) {
        if (ratio < 0) throw new IllegalArgumentException("Usage cannot be negative");
        if (ratio > 1) throw new IllegalArgumentException("Usage exceed 1 (using more than it has available)");
    }

    private void validateUtilRatio(float ratio) {
        if (ratio < 0) throw new IllegalArgumentException("Utilization cannot be negative");
    }
}
