// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.config.provision;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Models an immutable list of network port allocations
 *
 * @author arnej
 */
public class NetworkPorts {

    public static class Allocation {

        public final int port;
        public final String serviceType;
        public final String configId;
        public final String portSuffix;

        public Allocation(int port, String serviceType, String configId, String portSuffix) {
            this.port = port;
            this.serviceType = Objects.requireNonNull(serviceType, "servceType cannot be null");
            this.configId = Objects.requireNonNull(configId, "configId cannot be null");
            this.portSuffix = Objects.requireNonNull(portSuffix, "portSuffix cannot be null");
        }

        public String key() {
            StringBuilder buf = new StringBuilder();
            buf.append("t=").append(serviceType);
            buf.append(" cfg=").append(configId);
            buf.append(" suf=").append(portSuffix);
            return buf.toString();
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("[port=").append(port);
            buf.append(" serviceType=").append(serviceType);
            buf.append(" configId=").append(configId);
            buf.append(" suffix=").append(portSuffix);
            buf.append("]");
            return buf.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(port, serviceType, configId, portSuffix);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! (o instanceof Allocation)) return false;
            Allocation other = (Allocation)o;
            if (other.port != this.port) return false;
            if ( ! other.serviceType.equals(this.serviceType)) return false;
            if ( ! other.configId.equals(this.configId)) return false;
            if ( ! other.portSuffix.equals(this.portSuffix)) return false;
            return true;
        }

    }

    private final List<Allocation> allocations; // immutable list

    public NetworkPorts(Collection<Allocation> allocations) {
        this.allocations = List.copyOf(allocations);
    }

    /** Returns a read only collection of the port allocations of this */
    public Collection<Allocation> allocations() {
        return this.allocations;
    }

    public int size() { return allocations.size(); }

    @Override
    public int hashCode() { return allocations.hashCode(); }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof NetworkPorts)) return false;
        return ((NetworkPorts)other).allocations.equals(this.allocations);
    }

}
