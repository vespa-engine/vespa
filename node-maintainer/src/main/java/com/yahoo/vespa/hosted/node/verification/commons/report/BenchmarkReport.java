package com.yahoo.vespa.hosted.node.verification.commons.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by sgrostad on 12/07/2017.
 * JSON-mapped class for reporting benchmark results to node repo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BenchmarkReport {

    @JsonProperty
    private Double cpuCyclesPerSec;
    @JsonProperty
    private Double diskSpeedMbs;
    @JsonProperty
    private Double memoryWriteSpeedGBs;
    @JsonProperty
    private Double memoryReadSpeedGBs;

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

    public Double getDiskSpeedMbs() {
        return diskSpeedMbs;
    }

    public Double getMemoryWriteSpeedGBs() {
        return memoryWriteSpeedGBs;
    }

    public Double getMemoryReadSpeedGBs() {
        return memoryReadSpeedGBs;
    }

}
