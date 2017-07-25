package com.yahoo.vespa.hosted.node.verification.hardware.yamasreport;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by sgrostad on 12/07/2017.
 */
public class HardwareReportMetrics {

    @JsonProperty
    private Double cpuCyclesPerSec;
    @JsonProperty
    private Double diskSpeedMbs;
    @JsonProperty
    private Boolean ipv6Connectivity;
    @JsonProperty
    private Double memoryWriteSpeedGBs;
    @JsonProperty
    private Double memoryReadSpeedGBs;

    public void setCpuCyclesPerSec(Double cpuCyclesPerSec) {
        this.cpuCyclesPerSec = cpuCyclesPerSec;
    }

    public void setDiskSpeedMbs(Double diskSpeedMbs) {
        this.diskSpeedMbs = diskSpeedMbs != null ? diskSpeedMbs : -1;
    }

    public void setIpv6Connectivity(Boolean ipv6Connectivity) {
        this.ipv6Connectivity = ipv6Connectivity;
    }

    public void setMemoryWriteSpeedGBs(Double memoryWriteSpeedGBs) {
        this.memoryWriteSpeedGBs = memoryWriteSpeedGBs != null ? memoryWriteSpeedGBs : -1;
    }

    public void setMemoryReadSpeedGBs(Double memoryReadSpeedGBs) {
        this.memoryReadSpeedGBs = memoryReadSpeedGBs != null ? memoryReadSpeedGBs : -1;
    }
}
