// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.commons.noderepo;

import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo;
import com.yahoo.vespa.hosted.node.verification.spec.retrievers.HardwareInfo.DiskType;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.stream.Stream;

/**
 * Object with the information about a node
 *
 * @author freva
 */
public class NodeSpec {
    private final double minDiskAvailableGb;
    private final double minMainMemoryAvailableGb;
    private final double minCpuCores;
    private final boolean fastDisk;
    private final String[] ipAddresses;

    public NodeSpec(double minDiskAvailableGb, double minMainMemoryAvailableGb, double minCpuCores, boolean fastDisk,
                    String[] ipAddresses) {
        this.minDiskAvailableGb = minDiskAvailableGb;
        this.minMainMemoryAvailableGb = minMainMemoryAvailableGb;
        this.minCpuCores = minCpuCores;
        this.fastDisk = fastDisk;
        this.ipAddresses = ipAddresses;
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
}
