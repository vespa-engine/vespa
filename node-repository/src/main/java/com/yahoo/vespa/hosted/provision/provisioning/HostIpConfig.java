// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.vespa.hosted.provision.node.IP;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * IP config of a host and its children, and an optional extra host ID.
 *
 * @author mpolden
 */
public record HostIpConfig(Map<String, IP.Config> ipConfigByHostname, Optional<String> hostId) {

    public static final HostIpConfig EMPTY = new HostIpConfig(Map.of(), Optional.empty());

    public HostIpConfig(Map<String, IP.Config> ipConfigByHostname, Optional<String> hostId) {
        this.ipConfigByHostname = Map.copyOf(Objects.requireNonNull(ipConfigByHostname));
        this.hostId = Objects.requireNonNull(hostId);
    }

    public Map<String, IP.Config> asMap() {
        return ipConfigByHostname;
    }

    public boolean contains(String hostname) {
        return ipConfigByHostname.containsKey(hostname);
    }

    public IP.Config require(String hostname) {
        IP.Config ipConfig = this.ipConfigByHostname.get(hostname);
        if (ipConfig == null) throw new IllegalArgumentException("No IP config exists for node '" + hostname + "'");
        return ipConfig;
    }

    public boolean isEmpty() {
        return this.equals(EMPTY);
    }

    @Override
    public String toString() {
        return ipConfigByHostname.toString();
    }
}
