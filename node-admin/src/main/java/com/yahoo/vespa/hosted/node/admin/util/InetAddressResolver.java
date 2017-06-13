package com.yahoo.vespa.hosted.node.admin.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author musum
 */
public class InetAddressResolver {

    public InetAddress getInetAddressForHost(String hostname) throws UnknownHostException {
        return InetAddress.getByName(hostname);
    }

}
