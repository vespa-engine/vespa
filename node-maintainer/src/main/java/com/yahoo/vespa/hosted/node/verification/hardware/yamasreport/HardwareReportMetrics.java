package com.yahoo.vespa.hosted.node.verification.hardware.yamasreport;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by sgrostad on 12/07/2017.
 */
public class HardwareReportMetrics {

    @JsonProperty
    private double cpuCyclesPerSec;
    @JsonProperty
    private double diskSpeedMbs;
    @JsonProperty
    private double memoryWriteSpeedGBs;
    @JsonProperty
    private double memoryReadSpeedGBs;

    public void setCpuCyclesPerSec(double cpuCyclesPerSec) {
        this.cpuCyclesPerSec = cpuCyclesPerSec;
    }

    public void setDiskSpeedMbs(Double diskSpeedMbs) {
        this.diskSpeedMbs = diskSpeedMbs != null ? diskSpeedMbs : -1;
    }


    public void setMemoryWriteSpeedGBs(Double memoryWriteSpeedGBs) {
        this.memoryWriteSpeedGBs = memoryWriteSpeedGBs != null ? memoryWriteSpeedGBs : -1;
    }

    public void setMemoryReadSpeedGBs(Double memoryReadSpeedGBs) {
        this.memoryReadSpeedGBs = memoryReadSpeedGBs != null ? memoryReadSpeedGBs : -1;
    }

    public Double getCpuCyclesPerSec() {
        return cpuCyclesPerSec;
    }

    public double getDiskSpeedMbs() {
        return diskSpeedMbs;
    }

    public double getMemoryWriteSpeedGBs() {
        return memoryWriteSpeedGBs;
    }

    public double getMemoryReadSpeedGBs() {
        return memoryReadSpeedGBs;
    }

}
