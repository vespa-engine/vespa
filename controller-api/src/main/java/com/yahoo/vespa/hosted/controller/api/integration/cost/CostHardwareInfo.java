package com.yahoo.vespa.hosted.controller.api.integration.cost;// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * Value object for hardware resources.
 *
 * Can be for actual readings or target numbers.
 *
 * @author smorgrav
 */
public class CostHardwareInfo {

    private final double memoryGb;
    private final double cpuCores;
    private final double diskGb;
    private final double diskBusyPercentage;

    public CostHardwareInfo(double memoryGb, double cpuCores, double diskGb, double diskBusyPercentage) {
        this.memoryGb = memoryGb;
        this.cpuCores = cpuCores;
        this.diskGb = diskGb;
        this.diskBusyPercentage = diskBusyPercentage;
    }

    public double getMemoryGb() {
        return memoryGb;
    }

    public double getCpuCores() {
        return cpuCores;
    }

    public double getDiskGb() {
        return diskGb;
    }

    public double getDiskBusyPercentage() {
        return diskBusyPercentage;
    }
}
