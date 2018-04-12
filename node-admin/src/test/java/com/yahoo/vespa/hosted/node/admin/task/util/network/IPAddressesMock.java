package com.yahoo.vespa.hosted.node.admin.task.util.network;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author smorgrav
 */
public class IPAddressesMock implements IPAddresses {

    Map<String, List<InetAddress>> otherAddresses = new HashMap<>();

    public IPAddressesMock addAddress(String hostname, String ip) {
        List<InetAddress> addresses = otherAddresses.getOrDefault(hostname, new ArrayList<>());
        try {
            addresses.add(InetAddress.getByName(ip));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
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
