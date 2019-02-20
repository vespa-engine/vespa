// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.config.provision;

import java.util.Collection;
import java.util.List;

/**
 * Models an immutable list of network port allocations
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
            this.serviceType = serviceType;
            this.configId = configId;
            this.portSuffix = portSuffix;
        }
        public String key() {
            StringBuilder buf = new StringBuilder();
            buf.append("t=").append(serviceType);
            buf.append(" cfg=").append(configId);
            buf.append(" suf=").append(portSuffix);
            return buf.toString();
        }
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("[port=").append(port);
            buf.append(" serviceType=").append(serviceType);
            buf.append(" configId=").append(configId);
            buf.append(" suffix=").append(portSuffix);
            buf.append("]");
            return buf.toString();
        }
    }

    private final List<Allocation> allocations;

    public NetworkPorts(Collection<Allocation> allocations) {
        this.allocations = List.copyOf(allocations);
    }

    public Collection<Allocation> allocations() {
        return this.allocations;
    }
}
