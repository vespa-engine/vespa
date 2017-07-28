package com.yahoo.vespa.hosted.node.verification.hardware.benchmarks;

/**
 * Created by sgrostad on 11/07/2017.
 * Stores results from benchmarks
 */
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
