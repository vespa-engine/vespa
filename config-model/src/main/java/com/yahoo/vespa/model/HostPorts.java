// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.NetworkPorts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Allocator for network ports on a host
 * @author arnej
 */
public class HostPorts {

    public HostPorts(String hostname) {
        this.hostname = hostname;
    }

    final String hostname;
    public final static int BASE_PORT = 19100;
    final static int MAX_PORTS = 799;

    private DeployLogger deployLogger = new DeployLogger() {
        public void log(Level level, String message) {
            System.err.println("deploy log["+level+"]: "+message);
        }
    };

    private final Map<Integer, NetworkPortRequestor> portDB = new LinkedHashMap<>();

    private int allocatedPorts = 0;

    private PortFinder portFinder = new PortFinder(Collections.emptyList());

    private Optional<NetworkPorts> networkPortsList = Optional.empty();

    public Optional<NetworkPorts> networkPorts() { return networkPortsList; }

    public void addNetworkPorts(NetworkPorts ports) {
        this.networkPortsList = Optional.of(ports);
        this.portFinder = new PortFinder(ports.allocations());
    }

    public void useLogger(DeployLogger logger) {
        this.deployLogger = logger;
    }

    /**
     * Returns the baseport of the first available port range of length numPorts,
     * or 0 if there is no range of that length available.
     *
     * @param numPorts  The length of the desired port range.
     * @return  The baseport of the first available range, or 0 if no range is available.
     */
    public int nextAvailableBaseport(int numPorts) {
        int range = 0;
        int port = BASE_PORT;
        for (; port < BASE_PORT + MAX_PORTS && (range < numPorts); port++) {
            if (portDB.containsKey(port)) {
                range = 0;
                continue;
            }
            range++;
        }
        return range == numPorts ? port - range : 0;
    }

    private int nextAvailableNetworkPort() {
        int port = BASE_PORT;
        for (; port < BASE_PORT + MAX_PORTS; port++) {
            if (!portDB.containsKey(port)) return port;
        }
        return 0;
    }

    public int requireNetworkPort(int port, NetworkPortRequestor service, String suffix) {
        reservePort(service, port, suffix);
        String servType = service.getServiceType();
        String configId = service.getConfigId();
        portFinder.use(new NetworkPorts.Allocation(port, servType, configId, suffix));
        return port;
    }

    public int wantNetworkPort(int port, NetworkPortRequestor service, String suffix) {
        if (portDB.containsKey(port)) {
            int fallback = nextAvailableNetworkPort();
            NetworkPortRequestor s = portDB.get(port);
            deployLogger.log(Level.WARNING,
                service.getServiceName() +" cannot reserve port " + port + " on " +
                hostname + ": Already reserved for " + s.getServiceName() +
                ". Using default port range from " + fallback);
            return allocateNetworkPort(service, suffix);
        }
        return requireNetworkPort(port, service, suffix);
    }

    public int wantNetworkPort(int port, NetworkPortRequestor service, String suffix, boolean forceRequired) {
        return forceRequired ? requireNetworkPort(port, service, suffix) : wantNetworkPort(port, service, suffix);
    }

    public int allocateNetworkPort(NetworkPortRequestor service, String suffix) {
        String servType = service.getServiceType();
        String configId = service.getConfigId();
        int fallback = nextAvailableNetworkPort();
        int port = portFinder.findPort(new NetworkPorts.Allocation(fallback, servType, configId, suffix));
        reservePort(service, port, suffix);
        portFinder.use(new NetworkPorts.Allocation(port, servType, configId, suffix));
        return port;
    }

    List<Integer> allocatePorts(NetworkPortRequestor service, int wantedPort) {
        List<Integer> ports = new ArrayList<>();
        final int count = service.getPortCount();
        if (count < 1)
            return ports;

        String[] suffixes = service.getPortSuffixes();
        if (suffixes.length != count) {
            throw new IllegalArgumentException("service "+service+" had "+suffixes.length+" port suffixes, but port count "+count+", mismatch");
        }

        if (wantedPort > 0) {
            boolean force = service.requiresWantedPort();
            if (service.requiresConsecutivePorts()) {
                for (int i = 0; i < count; i++) {
                    ports.add(wantNetworkPort(wantedPort+i, service, suffixes[i], force));
                }
            } else {
                ports.add(wantNetworkPort(wantedPort, service, suffixes[0], force));
                for (int i = 1; i < count; i++) {
                    ports.add(allocateNetworkPort(service, suffixes[i]));
                }
            }
        } else {
            for (int i = 0; i < count; i++) {
                ports.add(allocateNetworkPort(service, suffixes[i]));
            }
        }
        return ports;
    }

    public void flushPortReservations() {
        this.networkPortsList = Optional.of(new NetworkPorts(portFinder.allocations()));
    }

    /**
     * Reserves the desired port for the given service, or throws as exception if the port
     * is not available.
     *
     * @param service the service that wishes to reserve the port.
     * @param port the port to be reserved.
     */
    void reservePort(NetworkPortRequestor service, int port, String suffix) {
        if (portDB.containsKey(port)) {
            portAlreadyReserved(service, port);
        }
        if (inVespasPortRange(port)) {
            allocatedPorts++;
            if (allocatedPorts > MAX_PORTS) {
                noMoreAvailablePorts();
            }
        }
        portDB.put(port, service);
    }

    private boolean inVespasPortRange(int port) {
        return port >= BASE_PORT &&
                port < BASE_PORT + MAX_PORTS;
    }

    private void portAlreadyReserved(NetworkPortRequestor service, int port) {
        NetworkPortRequestor otherService = portDB.get(port);
        int nextAvailablePort = nextAvailableBaseport(service.getPortCount());
        if (nextAvailablePort == 0) {
            noMoreAvailablePorts();
        }
        String msg = (service.getClass().equals(otherService.getClass()) && service.requiresWantedPort())
                ? "You must set port explicitly for all instances of this service type, except the first one. "
                : "";
        throw new RuntimeException(service.getServiceName() + " cannot reserve port " + port +
                    " on " + hostname + ": Already reserved for " + otherService.getServiceName() +
                    ". " + msg + "Next available port is: " + nextAvailablePort + " ports used: " + portDB);
    }

    private void noMoreAvailablePorts() {
        throw new RuntimeException
            ("Too many ports are reserved in Vespa's port range (" +
                    BASE_PORT  + ".." + (BASE_PORT+MAX_PORTS) + ") on " + hostname +
                    ". Move one or more services to another host, or outside this port range.");
    }

    @Override
    public String toString() {
        return "HostPorts{"+hostname+"}";
    }

}
