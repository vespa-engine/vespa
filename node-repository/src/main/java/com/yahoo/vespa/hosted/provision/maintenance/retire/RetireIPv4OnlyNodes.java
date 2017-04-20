package com.yahoo.vespa.hosted.provision.maintenance.retire;

import com.google.common.net.InetAddresses;
import com.yahoo.vespa.hosted.provision.Node;

import java.net.Inet4Address;

/**
 * @author freva
 */
public class RetireIPv4OnlyNodes implements RetirementPolicy {

    @Override
    public boolean shouldRetire(Node node) {
        return node.ipAddresses().stream()
                .map(InetAddresses::forString)
                .allMatch(address -> address instanceof Inet4Address);
    }
}
