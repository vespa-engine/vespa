// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

/**
 * The hosts allocated to an application.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
public class AllocatedHosts {

    private final Set<HostSpec> hosts;

    private AllocatedHosts(Set<HostSpec> hosts) {
        this.hosts = ImmutableSet.copyOf(hosts);
    }

    public static AllocatedHosts withHosts(Set<HostSpec> hosts) {
        return new AllocatedHosts(hosts);
    }

    /** Returns the hosts of this allocation */
    public Set<HostSpec> getHosts() { return hosts; }
    
    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof AllocatedHosts)) return false;
        return ((AllocatedHosts) other).hosts.equals(this.hosts);
    }
    
    @Override
    public int hashCode() {
        return hosts.hashCode();
    }

    @Override
    public String toString() {
        return hosts.toString();
    }

}
