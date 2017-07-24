package com.yahoo.vespa.hosted.node.verification.spec.yamasreport;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by olaa on 12/07/2017.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpecReportMetrics {

    @JsonProperty
    private boolean match;
    @JsonProperty
    private Double expectedMemoryAvailable;
    @JsonProperty
    private Double actualMemoryAvailable;
    @JsonProperty
    private Boolean expectedFastDisk;
    @JsonProperty
    private Boolean actualFastDisk;
    @JsonProperty
    private Double expectedDiskSpaceAvailable;
    @JsonProperty
    private Double actualDiskSpaceAvailable;
    @JsonProperty
    private Double expectedInterfaceSpeed;
    @JsonProperty
    private Double actualInterfaceSpeed;
    @JsonProperty
    private Integer expectedcpuCores;
    @JsonProperty
    private Integer actualcpuCores;
    @JsonProperty
    private String[] faultyIpAddresses;

    public void setMatch(boolean match){
        this.match = match;
    }

    public boolean isMatch(){
        return this.match;
    }
    public void setExpectedMemoryAvailable(Double expectedMemoryAvailable) {
        this.expectedMemoryAvailable = expectedMemoryAvailable;
    }

    public void setActualMemoryAvailable(Double actualMemoryAvailable) {
        this.actualMemoryAvailable = actualMemoryAvailable;
    }

    public void setExpectedDiskType(Boolean expectedFastDisk) {
        this.expectedFastDisk = expectedFastDisk;
    }

    public void setActualDiskType(Boolean actualFastDisk) {
        this.actualFastDisk = actualFastDisk;
    }

    public void setExpectedDiskSpaceAvailable(Double expectedDiskSpaceAvailable) {
        this.expectedDiskSpaceAvailable = expectedDiskSpaceAvailable;
    }

    public void setActualDiskSpaceAvailable(Double actualDiskSpaceAvailable) {
        this.actualDiskSpaceAvailable = actualDiskSpaceAvailable;
    }

    public void setExpectedcpuCores(int expectedcpuCores) {
        this.expectedcpuCores = expectedcpuCores;
    }

    public void setActualcpuCores(int actualcpuCores) {
        this.actualcpuCores = actualcpuCores;
    }

    public void setExpectedInterfaceSpeed(Double expectedInterfaceSpeed) {
        this.expectedInterfaceSpeed = expectedInterfaceSpeed;
    }

    public void setActualInterfaceSpeed(Double actualInterfaceSpeed) {
        this.actualInterfaceSpeed = actualInterfaceSpeed;
    }

    public void setFaultyIpAddresses(String[] faultyIpAddresses) {
        this.faultyIpAddresses = faultyIpAddresses;
    }

}
