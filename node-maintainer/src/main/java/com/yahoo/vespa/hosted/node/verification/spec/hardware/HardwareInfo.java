package com.yahoo.vespa.hosted.node.verification.spec.hardware;

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
    private String[] additionalIpAddresses;
    private String ipv6Address;
    private String ipv4Address;


    public String[] getAdditionalIpAddresses() {
        return additionalIpAddresses;
    }

    public void setAdditionalIpAddresses(String[] additionalIpAddresses) {
        this.additionalIpAddresses = additionalIpAddresses;
    }
    public double getInterfaceSpeedMbs() {
        return interfaceSpeedMbs;
    }

    public void setIpv6Address(String ipv6Address) {
        this.ipv6Address = ipv6Address;
    }

    public void setIpv4Address(String ipv4Address) {
        this.ipv4Address = ipv4Address;
    }

    public String getIpv6Address() {
        return ipv6Address;
    }

    public String getIpv4Address() {
        return ipv4Address;
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

    public void setIpAddresses(String[] ipAddresses) {
        String ipv6Regex = "^((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4}))*::((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4}))*|((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4})){7}$";
        for (String ipAddress : ipAddresses) {
            if (ipAddress.matches(ipv6Regex)) {
                 this.ipv6Address = ipAddress;
            }
            else {
                this.ipv4Address = ipAddress;
            }
        }
    }

}
