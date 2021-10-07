// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.network;

import com.google.common.net.InetAddresses;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author smorgrav
 */
public class IPAddressesMock implements IPAddresses {

    private final Map<String, List<InetAddress>> otherAddresses = new HashMap<>();

    public IPAddressesMock addAddress(String hostname, String ip) {
        List<InetAddress> addresses = otherAddresses.getOrDefault(hostname, new ArrayList<>());
        addresses.add(InetAddresses.forString(ip));
        otherAddresses.put(hostname, addresses);
        return this;
    }

    @Override
    public InetAddress[] getAddresses(String hostname) {
        List<InetAddress> addresses = otherAddresses.get(hostname);
        if (addresses == null) return new InetAddress[0];
        return addresses.toArray(new InetAddress[addresses.size()]);
    }
}
