// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON-mapped class for reporting benchmark results to node repo
 * 
 * @author sgrostad
 * @author olaaun
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

    @JsonIgnore
    public boolean isAllBenchmarksOK() {
        ObjectMapper om = new ObjectMapper();
        try {
            String jsonReport = om.writeValueAsString(this);
            return jsonReport.length() == 2;
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return false;
        }
    }

}
