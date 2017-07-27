package com.yahoo.vespa.hosted.node.verification.spec.yamasreport;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo.DiskType;

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
    private DiskType expectedDiskType;
    @JsonProperty
    private DiskType actualDiskType;
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
    @JsonProperty
    private Boolean actualIpv6Connection;

    public void setActualIpv6Connection(boolean actualIpv6Connection) {
        this.actualIpv6Connection = actualIpv6Connection;
    }


    public void setMatch(boolean match) {
        this.match = match;
    }

    public boolean isMatch() {
        return this.match;
    }

    public void setExpectedMemoryAvailable(Double expectedMemoryAvailable) {
        this.expectedMemoryAvailable = expectedMemoryAvailable;
    }

    public void setActualMemoryAvailable(Double actualMemoryAvailable) {
        this.actualMemoryAvailable = actualMemoryAvailable;
    }

    public void setExpectedDiskType(DiskType expectedFastDisk) {
        this.expectedDiskType = expectedFastDisk;
    }

    public void setActualDiskType(DiskType actualFastDisk) {
        this.actualDiskType = actualFastDisk;
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
