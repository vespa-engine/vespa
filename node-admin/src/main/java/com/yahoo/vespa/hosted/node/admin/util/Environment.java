// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Various utilities for getting values from node-admin's environment. Immutable.
 *
 * @author bakksjo
 * @author musum
 */
public class Environment {
    private static final String ENV_CONFIGSERVERS = "services__addr_configserver";
    private static final String ENVIRONMENT = "ENVIRONMENT";
    private static final String REGION = "REGION";

    private final Set<String> configServerHosts;
    private final String environment;
    private final String region;
    private final InetAddressResolver inetAddressResolver;

    public Environment() {
        this(getConfigServerHostsFromEnvironment(),
             getEnvironmentVariable(ENVIRONMENT),
             getEnvironmentVariable(REGION),
             new InetAddressResolver());
    }

    public Environment(Set<String> configServerHosts,
                       String environment,
                       String region,
                       InetAddressResolver inetAddressResolver) {
        this.configServerHosts = configServerHosts;
        this.environment = environment;
        this.region = region;
        this.inetAddressResolver = inetAddressResolver;
    }

    public Set<String> getConfigServerHosts() { return configServerHosts; }

    public String getEnvironment() {
        return environment;
    }

    public String getRegion() {
        return region;
    }

    private static String getEnvironmentVariable(String name) {
        final String value = System.getenv(name);
        if (value == null) throw new IllegalStateException(String.format("Environment variable %s not set", name));
        return value;
    }

    public String getZone() {
        return getEnvironment() + "." + getRegion();
    }

    private static Set<String> getConfigServerHostsFromEnvironment() {
        String configServerHosts = System.getenv(ENV_CONFIGSERVERS);
        if (configServerHosts == null) {
            return Collections.emptySet();
        }

        final List<String> hostNameStrings = Arrays.asList(configServerHosts.split("[,\\s]+"));
        return hostNameStrings.stream().collect(Collectors.toSet());
    }

    public InetAddress getInetAddressForHost(String hostname) throws UnknownHostException {
        return inetAddressResolver.getInetAddressForHost(hostname);
    }

}
