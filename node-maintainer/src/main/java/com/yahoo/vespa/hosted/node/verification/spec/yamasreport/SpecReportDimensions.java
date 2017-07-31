package com.yahoo.vespa.hosted.node.verification.spec.yamasreport;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by olaa on 12/07/2017.
 */
public class SpecReportDimensions {


    @JsonProperty
    private boolean memoryMatch;
    @JsonProperty
    private boolean cpuCoresMatch;
    @JsonProperty
    private boolean diskTypeMatch;
    @JsonProperty
    private boolean netInterfaceSpeedMatch;
    @JsonProperty
    private boolean diskAvailableMatch;
    @JsonProperty
    private boolean ipv4Match;
    @JsonProperty
    private boolean ipv6Match;

    public boolean isNetInterfaceSpeedMatch() {
        return netInterfaceSpeedMatch;
    }

    public void setNetInterfaceSpeedMatch(boolean netInterfaceSpeedMatch) {
        this.netInterfaceSpeedMatch = netInterfaceSpeedMatch;
    }

    public boolean isMemoryMatch() {
        return memoryMatch;
    }

    public void setMemoryMatch(boolean memoryMatch) {
        this.memoryMatch = memoryMatch;
    }

    public boolean isCpuCoresMatch() {
        return cpuCoresMatch;
    }

    public void setCpuCoresMatch(boolean cpuCoresMatch) {
        this.cpuCoresMatch = cpuCoresMatch;
    }

    public boolean isDiskTypeMatch() {
        return diskTypeMatch;
    }

    public void setDiskTypeMatch(boolean diskTypeMatch) {
        this.diskTypeMatch = diskTypeMatch;
    }

    public boolean isDiskAvailableMatch() {
        return diskAvailableMatch;
    }

    public void setDiskAvailableMatch(boolean diskAvailableMatch) {
        this.diskAvailableMatch = diskAvailableMatch;
    }

    public boolean isIpv4Match() {
        return ipv4Match;
    }

    public void setIpv4Match(boolean ipv4Match) {
        this.ipv4Match = ipv4Match;
    }

    public boolean isIpv6Match() {
        return ipv6Match;
    }

    public void setIpv6Match(boolean ipv6Match) {
        this.ipv6Match = ipv6Match;
    }

}
