// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
