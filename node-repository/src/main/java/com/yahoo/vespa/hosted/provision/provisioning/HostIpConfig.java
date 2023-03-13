// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.vespa.hosted.provision.node.IP;

import java.util.Map;
import java.util.Objects;

/**
 * IP config of a host and its children.
 *
 * @author mpolden
 */
public record HostIpConfig(Map<String, IP.Config> ipConfigByHostname) {

    public static final HostIpConfig EMPTY = new HostIpConfig(Map.of());

    public HostIpConfig(Map<String, IP.Config> ipConfigByHostname) {
        this.ipConfigByHostname = Map.copyOf(Objects.requireNonNull(ipConfigByHostname));
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

}
