package com.yahoo.vespa.hosted.node.verification.spec.noderepo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.node.verification.spec.hardware.HardwareInfo;

/**
 * Created by olaa on 05/07/2017.
 */

@JsonIgnoreProperties(ignoreUnknown=true)
public class NodeJsonModel {

    @JsonProperty("minDiskAvailableGb")
    private double minDiskAvailableGb;
    @JsonProperty("minMainMemoryAvailableGb")
    private double minMainMemoryAvailableGb;
    @JsonProperty("minCpuCores")
    private double minCpuCores;
    @JsonProperty("fastDisk")
    private boolean fastDisk;
    @JsonProperty("ipAddresses")
    private String[] ipAddresses;
    @JsonProperty("additionalIpAddresses")
    private String[] additionalIpAddresses;


    public double getMinDiskAvailableGb() {
        return minDiskAvailableGb;
    }

    public double getMinMainMemoryAvailableGb() {
        return minMainMemoryAvailableGb;
    }
    public double getMinCpuCores() {
        return minCpuCores;
    }

    public HardwareInfo copyToHardwareInfo(){
        HardwareInfo hardwareInfo = new HardwareInfo();
        hardwareInfo.setMinMainMemoryAvailableGb(this.minMainMemoryAvailableGb);
        hardwareInfo.setMinDiskAvailableGb(this.minDiskAvailableGb);
        hardwareInfo.setMinCpuCores((int) Math.round(this.minCpuCores));
        hardwareInfo.setFastDisk(this.fastDisk);
        hardwareInfo.setAdditionalIpAddresses(this.additionalIpAddresses);
        hardwareInfo.setIpAddresses(this.ipAddresses);
        return hardwareInfo;
    }

}
