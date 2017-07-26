package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

/**
 * Created by olaa on 04/07/2017.
 * All information the different retrievers retrieve is stored as a HardwareInfo object.
 */

public class HardwareInfo {

    private double minDiskAvailableGb;
    private double minMainMemoryAvailableGb;
    private int minCpuCores;
    private boolean ipv4Connectivity;
    private boolean ipv6Connectivity;
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

    public boolean getIpv6Connectivity() {
        return ipv6Connectivity;
    }

    public void setIpv6Connectivity(boolean ipv6Connectivity) {
        this.ipv6Connectivity = ipv6Connectivity;
    }

    public boolean getIpv4Connectivity() {
        return ipv4Connectivity;
    }

    public void setIpv4Connectivity(boolean ipv4Connectivity) {
        this.ipv4Connectivity = ipv4Connectivity;
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

    public enum DiskType {SLOW, FAST, UNKNOWN};

}
