package com.yahoo.vespa.hosted.controller.application;// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * System resources as _ratios_ of available resources.
 *
 * Can be for actual readings or target numbers.
 *
 * @author smorgrav
 */
public class ClusterUtilization {

    private final double memory;
    private final double cpu;
    private final double disk;
    private final double diskBusy;
    private final double maxUtilization;

    /**
     * Resource utilization as ratios. The ratio is normally between 0 and 1 where
     * one is fully utilized but can be higher as it consumes more than it are guaranteed.
     *
     * @param memory Memory utilization
     * @param cpu  CPU utilization
     * @param disk  Disk utilization
     * @param diskBusy Disk busy
     */
    public ClusterUtilization(double memory, double cpu, double disk, double diskBusy) {
        this.memory = memory;
        this.cpu = cpu;
        this.disk = disk;
        this.diskBusy = diskBusy;

        double maxUtil = Math.max(cpu, disk);
        maxUtil = Math.max(maxUtil, memory);
        this.maxUtilization = Math.max(maxUtil, diskBusy);
    }

    /** @return The utilization ratio of the resource that is utilized the most. */
    public double getMaxUtilization() {
        return maxUtilization;
    }

    /** @return The utilization ratio for memory */
    public double getMemory() {
        return memory;
    }

    /** @return The utilization ratio for cpu */
    public double getCpu() {
        return cpu;
    }

    /** @return The utilization ratio for disk */
    public double getDisk() {
        return disk;
    }

    /** @return The disk busy ratio */
    public double getDiskBusy() {
        return diskBusy;
    }
}
