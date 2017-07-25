package com.yahoo.vespa.hosted.node.verification.spec.retrievers;

/**
 * Created by olaa on 04/07/2017.
 */

public class HardwareInfo {

    private double minDiskAvailableGb;
    private double minMainMemoryAvailableGb;
    private int minCpuCores;
    private Boolean fastDisk;
    private Boolean ipv4Connectivity;
    private Boolean ipv6Connectivity;
    private double interfaceSpeedMbs;



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
    public Boolean getIpv6Connectivity() {
        return ipv6Connectivity;
    }

    public void setIpv6Connectivity(Boolean ipv6Connectivity) {
        this.ipv6Connectivity = ipv6Connectivity;
    }

    public Boolean getIpv4Connectivity() {
        return ipv4Connectivity;
    }

    public void setIpv4Connectivity(Boolean ipv4Connectivity) {
        this.ipv4Connectivity = ipv4Connectivity;
    }
    public double getMinMainMemoryAvailableGb() {
        return minMainMemoryAvailableGb;
    }

    public void setMinMainMemoryAvailableGb(double minMainMemoryAvailableGb) {
        this.minMainMemoryAvailableGb = minMainMemoryAvailableGb;
    }

    public void setFastDisk(Boolean fastDisk){
        this.fastDisk = fastDisk;
    }

    public Boolean getFastDisk(){
        return fastDisk;
    }

    public int getMinCpuCores() {
        return minCpuCores;
    }

    public void setMinCpuCores(int minCpuCores) {
        this.minCpuCores = minCpuCores;
    }

}
