// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance.retire;

import com.google.common.net.InetAddresses;
import com.yahoo.config.provision.Flavor;
import com.yahoo.vespa.hosted.provision.Node;

import java.net.Inet4Address;

/**
 * @author freva
 */
public class RetireIPv4OnlyNodes implements RetirementPolicy {

    @Override
    public boolean shouldRetire(Node node) {
        if (node.flavor().getType() == Flavor.Type.VIRTUAL_MACHINE) return false;
        return node.ipAddresses().stream()
                .map(InetAddresses::forString)
                .allMatch(address -> address instanceof Inet4Address);
    }
}
