// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
public record HostIpConfig(Map<String, IP.Config> ipConfigByHostname, Optional<String> hostId, Map<String, IP.Config> publicIpConfigByHostname) {

    public static final HostIpConfig EMPTY = new HostIpConfig(Map.of(), Optional.empty(), Map.of());

    public HostIpConfig(Map<String, IP.Config> ipConfigByHostname, Optional<String> hostId) {
        this(ipConfigByHostname, hostId, Map.of());
    }

    public HostIpConfig(Map<String, IP.Config> ipConfigByHostname, Optional<String> hostId, Map<String, IP.Config> publicIpConfigByHostname) {
        this.ipConfigByHostname = Map.copyOf(Objects.requireNonNull(ipConfigByHostname));
        this.hostId = Objects.requireNonNull(hostId);
        this.publicIpConfigByHostname = Map.copyOf(Objects.requireNonNull(publicIpConfigByHostname));
    }

    public Map<String, IP.Config> asMap() {
        return ipConfigByHostname;
    }

    public Map<String, IP.Config> publicAsMap() {
        return publicIpConfigByHostname;
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
        return "HostIpConfig{private=" + ipConfigByHostname + ", public=" + publicIpConfigByHostname + "}";
    }
}
