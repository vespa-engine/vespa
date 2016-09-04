// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import java.util.ArrayList;
import java.util.List;

/**
 * A hostname with zero or more aliases.
 *
 * @author hmusum
 */
public class Host {

    private final String hostname;
    private final List<String> hostAliases;

    public Host(String hostname) {
        this.hostname = hostname;
        this.hostAliases = new ArrayList<>();
    }

    public Host(String hostname, List<String> hostAliases) {
        this.hostname = hostname;
        this.hostAliases = hostAliases;
    }

    public String getHostname() {
        return hostname;
    }

    public List<String> getHostAliases() {
        return hostAliases;
    }

    @Override
    public String toString() {
        return hostname + " (aliases: " + hostAliases + ")";
    }

}
