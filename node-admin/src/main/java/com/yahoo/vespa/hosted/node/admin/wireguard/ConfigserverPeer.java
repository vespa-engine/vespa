package com.yahoo.vespa.hosted.node.admin.wireguard;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.WireguardKey;
import com.yahoo.vespa.hosted.node.admin.task.util.network.VersionedIpAddress;

import java.util.Collection;
import java.util.List;

/**
 * @author gjoranv
 */
public record ConfigserverPeer(HostName hostname,
                               Collection<VersionedIpAddress> ipAddresses,
                               WireguardKey publicKey) {

    public ConfigserverPeer {
        if (ipAddresses.isEmpty()) throw new IllegalArgumentException("No IP addresses for configserver " + hostname.value());
        ipAddresses = List.copyOf(ipAddresses);
    }

}
