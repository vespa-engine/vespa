// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

/**
 * All information the different retrievers retrieve is stored as a HardwareInfo object.
 *
 * @author olaaun
 * @author sgrostad
 */
// TODO: This should be immutable
public class HardwareInfo {

    private double minDiskAvailableGb;
    private double minMainMemoryAvailableGb;
    private int minCpuCores;
    private boolean ipv4Interface;
    private boolean ipv6Interface;
    private boolean ipv6Connection;
    private double interfaceSpeedMbs;
    private DiskType diskType;

    public double getInterfaceSpeedMbs() {
        return interfaceSpeedMbs;
    }

    public void setInterfaceSpeedMbs(double interfaceSpeedMbs) {
        this.interfaceSpeedMbs = interfaceSpeedMbs;
    }

    public double getMinDiskAvailableGb() {
        return minDiskAvailableGb;
    }

    public void setMinDiskAvailableGb(double minDiskAvailableGb) {
        this.minDiskAvailableGb = minDiskAvailableGb;
    }

    public boolean getIpv6Interface() {
        return ipv6Interface;
    }

    public void setIpv6Interface(boolean ipv6Interface) {
        this.ipv6Interface = ipv6Interface;
    }

    public boolean getIpv4Interface() {
        return ipv4Interface;
    }

    public void setIpv4Interface(boolean ipv4Interface) {
        this.ipv4Interface = ipv4Interface;
    }

    public boolean isIpv6Connection() {
        return ipv6Connection;
    }

    public void setIpv6Connection(boolean ipv6Connection) {
        this.ipv6Connection = ipv6Connection;
    }

    public double getMinMainMemoryAvailableGb() {
        return minMainMemoryAvailableGb;
    }

    public void setMinMainMemoryAvailableGb(double minMainMemoryAvailableGb) {
        this.minMainMemoryAvailableGb = minMainMemoryAvailableGb;
    }

    public void setDiskType(DiskType diskType) {
        this.diskType = diskType;
    }

    public DiskType getDiskType() {
        return diskType;
    }

    public int getMinCpuCores() {
        return minCpuCores;
    }

    public void setMinCpuCores(int minCpuCores) {
        this.minCpuCores = minCpuCores;
    }

    public enum DiskType {SLOW, FAST, UNKNOWN}

}
