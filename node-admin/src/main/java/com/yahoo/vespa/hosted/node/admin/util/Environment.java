// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import com.yahoo.vespa.applicationmodel.HostName;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Various utilities for interacting with node-admin's environment.
 *
 * @author bakksjo
 */
public class Environment {
    private static final String ENV_CONFIGSERVERS = "services__addr_configserver";
    private static final String ENV_NETWORK_TYPE = "NETWORK_TYPE";

    public enum NetworkType { normal, local, vm }

    public Set<HostName> getConfigServerHosts() {
        final String configServerHosts = System.getenv(ENV_CONFIGSERVERS);
        if (configServerHosts == null) {
            return Collections.emptySet();
        }

        final List<String> hostNameStrings = Arrays.asList(configServerHosts.split("[,\\s]+"));
        return hostNameStrings.stream()
                .map(HostName::new)
                .collect(Collectors.toSet());
    }

    public NetworkType networkType() throws IllegalArgumentException {
        String networkTypeInEnvironment = System.getenv(ENV_NETWORK_TYPE);
        if (networkTypeInEnvironment == null) {
            return NetworkType.normal;
        }
        return NetworkType.valueOf(networkTypeInEnvironment);
    }

    public InetAddress getInetAddressForHost(String hostname) throws UnknownHostException {
        return InetAddress.getByName(hostname);
    }
}
