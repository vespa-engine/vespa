// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.Collection;

/*
 * Contains information about a host and what services are running on it.
 *
 * @author Ulf Lilleengen
 */
public class HostInfo {

    private final String hostname;
    private final Collection<ServiceInfo> services;

    public HostInfo(String hostName, Collection<ServiceInfo> services) {
        this.hostname = hostName;
        this.services = services;
    }

    public String getHostname() {
        return hostname;
    }

    public Collection<ServiceInfo> getServices() {
        return services;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HostInfo hostInfo = (HostInfo) o;

        if (!hostname.equals(hostInfo.hostname)) return false;
        if (services != null ? !services.equals(hostInfo.services) : hostInfo.services != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = hostname.hashCode();
        result = 31 * result + (services != null ? services.hashCode() : 0);
        return result;
    }

}
