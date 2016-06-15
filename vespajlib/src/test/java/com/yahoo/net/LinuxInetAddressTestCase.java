// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import java.net.UnknownHostException;
import java.net.InetAddress;
import java.net.Inet4Address;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class LinuxInetAddressTestCase extends junit.framework.TestCase {

    public void testPreferIPv4() throws UnknownHostException {
        try {
            // This test only works if there is at least one inet address returned.
            InetAddress[] arr = LinuxInetAddress.getAllLocal();
            if (arr.length > 0) {
                // System.out.println("Got " + arr.length + " addresses.");

                // And it can only make sure it is preferred if there is at least one ip v4 address.
                boolean ipv4 = false;
                for (int i = 0; i < arr.length; ++i) {
                    // System.out.println("Address " + i + " is an instance of " + arr[i].getClass() + ".");
                    if (arr[i] instanceof Inet4Address) {
                        ipv4 = true;
                    }
                }

                // And the only thing we test is that an ip v4 address is preferred.
                if (ipv4) {
                    InetAddress addr = LinuxInetAddress.getLocalHost();
                    assertNotNull("IPv4 is prefered", addr instanceof Inet4Address);
                }
            }
        }
        catch (java.net.UnknownHostException e) {
            // We're on vpn or have no network
        }
    }

}
