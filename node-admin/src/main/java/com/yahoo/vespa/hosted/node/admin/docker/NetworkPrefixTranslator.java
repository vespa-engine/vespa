// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * @author smorgrav
 */
package com.yahoo.vespa.hosted.node.admin.docker;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

class NetworkPrefixTranslator {

    /**
     * For NPTed networks we want to find the private address from a public.
     *
     * @param address    The original address to translate
     * @param prefix     The prefix address
     * @param subnetSize nof bits - e.g /64 subnet is 64
     * @return The translated address
     */
    static Inet6Address translate(InetAddress address, InetAddress prefix, int subnetSize) {

        byte[] originalAddress = address.getAddress();
        byte[] prefixAddress = prefix.getAddress();
        byte[] translatedAddress = new byte[16];

        for (int i = 0; i < 16; i++) {
            translatedAddress[i] = i < subnetSize / 8 ? prefixAddress[i] : originalAddress[i];
        }

        try {
            return (Inet6Address) InetAddress.getByAddress(address.getHostName(), translatedAddress);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
