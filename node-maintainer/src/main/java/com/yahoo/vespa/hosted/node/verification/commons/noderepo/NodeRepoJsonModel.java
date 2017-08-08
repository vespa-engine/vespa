package com.yahoo.vespa.hosted.node.verification.commons.noderepo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo.DiskType;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.stream.Stream;

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
    @JsonProperty
    private String hostname;
    @JsonProperty
    private String environment;
    @JsonProperty
    private String hardwareDivergence;
    private String nodeRepoUrl;

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
        return Stream.of(ipAddresses)
                .map(InetAddresses::forString)
                .filter(ip -> ip instanceof Inet6Address)
                .findFirst().map(InetAddress::getHostAddress).orElse(null);
    }

    public String getIpv4Address() {
        return Stream.of(ipAddresses)
                .map(InetAddresses::forString)
                .filter(ip -> ip instanceof Inet4Address)
                .findFirst().map(InetAddress::getHostAddress).orElse(null);
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

    public String getHostname() {
        return hostname;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getHardwareDivergence() {
        return hardwareDivergence;
    }
}
