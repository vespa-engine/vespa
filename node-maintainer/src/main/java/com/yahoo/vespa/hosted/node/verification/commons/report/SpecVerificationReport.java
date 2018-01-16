// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;


/**
 * Stores results of comparing node repo spec and actual hardware info.
 * In case of divergent values, set the corresponding attribute to the actual hardware info value.
 * Attributes of equal value remain null.
 *
 * @author sgrostad
 * @author olaaun
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpecVerificationReport {

    @JsonProperty
    private Double actualMemoryAvailable;
    @JsonProperty
    private HardwareInfo.DiskType actualDiskType;
    @JsonProperty
    private Double actualDiskSpaceAvailable;
    @JsonProperty
    private Double actualInterfaceSpeed;
    @JsonProperty
    private Integer actualcpuCores;
    @JsonProperty
    private String[] faultyIpAddresses;
    @JsonProperty
    private Boolean actualIpv6Connection;

    public void setActualIpv6Connection(boolean actualIpv6Connection) {
        this.actualIpv6Connection = actualIpv6Connection;
    }

    public void setActualMemoryAvailable(Double actualMemoryAvailable) {
        this.actualMemoryAvailable = actualMemoryAvailable;
    }

    public void setActualDiskType(HardwareInfo.DiskType actualFastDisk) {
        this.actualDiskType = actualFastDisk;
    }

    public void setActualDiskSpaceAvailable(Double actualDiskSpaceAvailable) {
        this.actualDiskSpaceAvailable = actualDiskSpaceAvailable;
    }

    public void setActualcpuCores(int actualcpuCores) {
        this.actualcpuCores = actualcpuCores;
    }

    public void setActualInterfaceSpeed(Double actualInterfaceSpeed) {
        this.actualInterfaceSpeed = actualInterfaceSpeed;
    }

    public void setFaultyIpAddresses(String[] faultyIpAddresses) {
        this.faultyIpAddresses = faultyIpAddresses;
    }

    @JsonIgnore
    public boolean isValidSpec() {
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
