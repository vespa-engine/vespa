// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author freva
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NodeResources {

    @JsonProperty
    private Double vcpu;
    @JsonProperty
    private Double memoryGb;
    @JsonProperty
    private Double diskGb;
    @JsonProperty
    private Double bandwidthGbps;
    @JsonProperty
    private String diskSpeed;
    @JsonProperty
    private String storageType;

    public Double getVcpu() {
        return vcpu;
    }

    public void setVcpu(Double vcpu) {
        this.vcpu = vcpu;
    }

    public Double getMemoryGb() {
        return memoryGb;
    }

    public void setMemoryGb(Double memoryGb) {
        this.memoryGb = memoryGb;
    }

    public Double getDiskGb() {
        return diskGb;
    }

    public void setDiskGb(Double diskGb) {
        this.diskGb = diskGb;
    }

    public Double getBandwidthGbps() {
        return bandwidthGbps;
    }

    public void setBandwidthGbps(Double bandwidthGbps) {
        this.bandwidthGbps = bandwidthGbps;
    }

    public String getDiskSpeed() {
        return diskSpeed;
    }

    public void setDiskSpeed(String diskSpeed) {
        this.diskSpeed = diskSpeed;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    public com.yahoo.config.provision.NodeResources toNodeResources() {
        return new com.yahoo.config.provision.NodeResources(vcpu, memoryGb, diskGb, bandwidthGbps,
                                                            toDiskSpeed(diskSpeed),
                                                            toStorageType(storageType));
    }

    private com.yahoo.config.provision.NodeResources.DiskSpeed toDiskSpeed(String diskSpeed) {
        switch (diskSpeed) {
            case "fast" : return com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
            case "slow" : return com.yahoo.config.provision.NodeResources.DiskSpeed.slow;
            case "any" : return com.yahoo.config.provision.NodeResources.DiskSpeed.any;
            default : throw new IllegalArgumentException("Unknown disk speed '" + diskSpeed + "'");
        }
    }

    private com.yahoo.config.provision.NodeResources.StorageType toStorageType(String storageType) {
        switch (storageType) {
            case "remote" : return com.yahoo.config.provision.NodeResources.StorageType.remote;
            case "local" : return com.yahoo.config.provision.NodeResources.StorageType.local;
            case "any" : return com.yahoo.config.provision.NodeResources.StorageType.any;
            default : throw new IllegalArgumentException("Unknown storage type '" + storageType + "'");
        }
    }

    @Override
    public String toString() {
        return "NodeResources{" +
                "vcpu=" + vcpu +
                ", memoryGb=" + memoryGb +
                ", diskGb=" + diskGb +
                ", bandwidthGbps=" + bandwidthGbps +
                ", diskSpeed='" + diskSpeed + '\'' +
                ", storageType='" + storageType + '\'' +
                '}';
    }
}
