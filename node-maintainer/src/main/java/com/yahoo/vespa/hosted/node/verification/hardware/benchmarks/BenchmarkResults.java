// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.hardware.benchmarks;

/**
 * Stores results from benchmarks
 * 
 * @author sgrostad
 * @author olaaun
 */
// TODO: This should be immutable
public class BenchmarkResults {

    private double cpuCyclesPerSec;
    private double diskSpeedMbs;
    private double memoryWriteSpeedGBs;
    private double memoryReadSpeedGBs;

    public double getMemoryWriteSpeedGBs() {
        return memoryWriteSpeedGBs;
    }

    public void setMemoryWriteSpeedGBs(double memoryWriteSpeedGBs) {
        this.memoryWriteSpeedGBs = memoryWriteSpeedGBs;
    }

    public double getMemoryReadSpeedGBs() {
        return memoryReadSpeedGBs;
    }

    public void setMemoryReadSpeedGBs(double memoryReadSpeedGBs) {
        this.memoryReadSpeedGBs = memoryReadSpeedGBs;
    }

    public double getCpuCyclesPerSec() {
        return cpuCyclesPerSec;
    }

    public void setCpuCyclesPerSec(double cpuCycles) {
        this.cpuCyclesPerSec = cpuCycles;
    }

    public double getDiskSpeedMbs() {
        return diskSpeedMbs;
    }

    public void setDiskSpeedMbs(double diskSpeedMbs) {
        this.diskSpeedMbs = diskSpeedMbs;
    }

}
