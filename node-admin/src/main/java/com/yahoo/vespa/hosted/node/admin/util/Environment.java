// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import com.yahoo.vespa.applicationmodel.HostName;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Various utilities for interacting with node-admin's environment.
 *
 * @author <a href="mailto:bakksjo@yahoo-inc.com">Oyvind Bakksjo</a>
 */
public class Environment {
    private Environment() {} // Prevents instantiation.

    private static final String ENV_CONFIGSERVERS = "services__addr_configserver";
    private static final String ENV_NETWORK_TYPE = "NETWORK_TYPE";

    public enum NetworkType { normal, local, vm }

    public static Set<HostName> getConfigServerHosts() {
        final String configServerHosts = System.getenv(ENV_CONFIGSERVERS);
        if (configServerHosts == null) {
            return Collections.emptySet();
        }

        final List<String> hostNameStrings = Arrays.asList(configServerHosts.split("[,\\s]+"));
        return hostNameStrings.stream()
                .map(HostName::new)
                .collect(Collectors.toSet());
    }

    public static NetworkType networkType() throws IllegalArgumentException {
        String networkTypeInEnvironment = System.getenv(ENV_NETWORK_TYPE);
        if (networkTypeInEnvironment == null) {
            return NetworkType.normal;
        }
        return NetworkType.valueOf(networkTypeInEnvironment);
    }
}
