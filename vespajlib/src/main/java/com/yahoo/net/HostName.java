// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Utilities for getting the hostname on a system running with the JVM. This is moved here from the old
 * HostSystem#getHostName in config-model.
 *
 * @author lulf
 */
public class HostName {

    private static String myHost = null;

    /**
     * Static method that returns the name of localhost using shell
     * command "hostname".
     *
     * @return the name of localhost.
     * @throws RuntimeException if executing the command 'hostname' fails.
     */
    // Note. This will not currently return a FQDN in Mac.
    // If that is needed, add 
    // java.net.InetAddress.getByName(myHost).getCanonicalHostName()
    public static synchronized String getLocalhost() {
        if (myHost == null) {
            try {
                Process p = Runtime.getRuntime().exec("hostname");
                BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                myHost = in.readLine();
                p.waitFor();
                if (p.exitValue() != 0) {
                    throw new RuntimeException("Command 'hostname' failed: exit("+p.exitValue()+")");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed when executing command 'hostname'", e);
            }
        }
        return myHost;
    }

}
