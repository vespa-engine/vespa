// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model;

import java.util.ArrayList;
import java.util.List;

/**
 * API for allocating network ports
 * This class acts as a bridge between NetworkPortRequestor and HostPorts
 * for a single call to allocatePorts(), gathering the resulting port
 * allocations in a list of integers.
 *
 * @author arnej
 */
public class PortAllocBridge {

    private final HostPorts host;
    private final NetworkPortRequestor service;
    private final List<Integer> ports = new ArrayList<>();

    public PortAllocBridge(HostPorts host, NetworkPortRequestor service) {
        this.host = host;
        this.service = service;
    }

    public int requirePort(int port, String suffix) {
        int got = host.requireNetworkPort(port, service, suffix);
        ports.add(got);
        return got;
    }

    public int wantPort(int port, String suffix) {
        int got = host.wantNetworkPort(port, service, suffix);
        ports.add(got);
        return got;
    }

    public int allocatePort(String suffix) {
        int got = host.allocateNetworkPort(service, suffix);
        ports.add(got);
        return got;
    }

    public List<Integer> result() {
        return ports;
    }

}
