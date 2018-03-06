package com.yahoo.vespa.hosted.node.admin.task.util.network;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author smorgrav
 */
public class IPAddressesImpl implements IPAddresses {

    @Override
    public InetAddress[] getAddresses(String hostname) {
        try {
            return InetAddress.getAllByName(hostname);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
