package com.yahoo.vespa.hosted.node.verification.spec.noderepo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo.DiskType;

/**
 * Created by olaa on 05/07/2017.
 * Object with the information node repositories has about the node.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeRepoJsonModel {
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
    @JsonProperty
    private String hostname;

    public String[] getAdditionalIpAddresses() {
        return additionalIpAddresses;
    }

    public HardwareInfo copyToHardwareInfo() {
        HardwareInfo hardwareInfo = new HardwareInfo();
        hardwareInfo.setMinMainMemoryAvailableGb(this.minMainMemoryAvailableGb);
        hardwareInfo.setMinDiskAvailableGb(this.minDiskAvailableGb);
        hardwareInfo.setMinCpuCores((int) Math.round(this.minCpuCores));
        hardwareInfo.setDiskType(this.fastDisk ? DiskType.FAST : DiskType.SLOW);
        hardwareInfo.setIpv6Connection(getIpv6Address() != null);
        return hardwareInfo;
    }

    public String getIpv6Address() {
        String ipv6Regex = "^((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4}))*::((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4}))*|((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4})){7}$";
        for (String ipAddress : ipAddresses) {
            if (ipAddress.matches(ipv6Regex)) {
                return ipAddress;
            }
        }
        return null;
    }

    public String getIpv4Address() {
        String ipv4Regex = "((1?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}(1?\\d\\d?|2[0-4]\\d|2\u200C\u200B5[0-5])";
        for (String ipAddress : ipAddresses) {
            if (ipAddress.matches(ipv4Regex)) {
                return ipAddress;
            }
        }
        return null;
    }

    public double getMinDiskAvailableGb() {
        return minDiskAvailableGb;
    }

    public double getMinMainMemoryAvailableGb() {
        return minMainMemoryAvailableGb;
    }

    public double getMinCpuCores() {
        return minCpuCores;
    }

    public boolean isFastDisk() {
        return fastDisk;
    }

    public String[] getIpAddresses() {
        return ipAddresses;
    }

    public String getHostname() {
        return hostname;
    }

}
