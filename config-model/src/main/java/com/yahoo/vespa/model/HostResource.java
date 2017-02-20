// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.model.api.HostInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * A host representation. The identity of this is the identity of its Host.
 * TODO: Merge with {@link Host}
 * Host resources are ordered by their host order.
 *
 * @author lulf
 * @since 5.12
 */
public class HostResource implements Comparable<HostResource> {

    public final static int BASE_PORT = 19100;
    public final static int MAX_PORTS = 799;
    private final Host host;

    // Map from "sentinel name" to service
    private final Map<String, Service> services = new LinkedHashMap<>();
    private final Map<Integer, Service> portDB = new LinkedHashMap<>();

    private int allocatedPorts = 0;

    // Empty for self-hosted Vespa.
    private Optional<String> flavor = Optional.empty();

    /**
     * Create a new {@link HostResource} bound to a specific {@link com.yahoo.vespa.model.Host}.
     *
     * @param host {@link com.yahoo.vespa.model.Host} object to bind to.
     */
    public HostResource(Host host) {
        this.host = host;
    }

    /**
     * Return the currently bounded {@link com.yahoo.vespa.model.Host}.
     * @return the {@link com.yahoo.vespa.model.Host} if bound, null if not.
     */
    public Host getHost() { return host; }

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
        return range == numPorts ?
                port - range :
                0;
    }

    boolean isPortRangeAvailable(int start, int numPorts) {
        int range = 0;
        int port = start;
        for (; port < BASE_PORT + MAX_PORTS && (range < numPorts); port++) {
            if (portDB.containsKey(port)) {
                return false;
            }
            range++;
        }
        return range == numPorts;
    }

    /**
     * Adds service and allocates resources for it.
     *
     * @param service The Service to allocate resources for
     * @param wantedPort the wanted port for this service
     * @return  The allocated ports for the Service.
     */
    List<Integer> allocateService(AbstractService service, int wantedPort) {
        List<Integer> ports = allocatePorts(service, wantedPort);
        assert (getService(service.getServiceName()) == null) :
                ("There is already a service with name '" + service.getServiceName() + "' registered on " + this +
                ". Most likely a programming error - all service classes must have unique names, even in different packages!");

        services.put(service.getServiceName(), service);
        return ports;
    }

    // TODO: make private when LocalApplication and all model services has stopped reallocating ports _after_ allocateServices has been called
    List<Integer> allocatePorts(AbstractService service, int wantedPort) {
        List<Integer> ports = new ArrayList<>();
        if (service.getPortCount() < 1)
            return ports;

        int serviceBasePort = BASE_PORT + allocatedPorts;
        if (wantedPort > 0) {
            if (service.getPortCount() < 1) {
                throw new RuntimeException(service + " wants baseport " + wantedPort +
                        ", but it has not reserved any ports, so it cannot name a desired baseport.");
            }
            if (service.requiresWantedPort() || canUseWantedPort(service, wantedPort, serviceBasePort))
                serviceBasePort = wantedPort;
        }

        reservePort(service, serviceBasePort);
        ports.add(serviceBasePort);

        int remainingPortsStart =  service.requiresConsecutivePorts() ?
                serviceBasePort + 1:
                BASE_PORT + allocatedPorts;
        for (int i = 0; i < service.getPortCount() - 1; i++) {
            int port = remainingPortsStart + i;
            reservePort(service, port);
            ports.add(port);
        }
        return ports;
    }

    // TODO: this is a hack to allow calling AbstractService.setBasePort _after_ the services has been initialized,
    //       i.e. modifying the baseport. Done by e.g. LocalApplication. Try to remove usage of this method!
    void deallocatePorts(AbstractService service) {
        for (Iterator<Map.Entry<Integer,Service>> i=portDB.entrySet().iterator(); i.hasNext();) {
            Map.Entry<Integer, Service> e = i.next();
            Service s = e.getValue();
            if (s.equals(service))
                i.remove();
        }
    }

    private boolean canUseWantedPort(AbstractService service, int wantedPort, int serviceBasePort) {
        for (int i = 0; i < service.getPortCount(); i++) {
            int port = wantedPort + i;
            if (portDB.containsKey(port)) {
                AbstractService s = (AbstractService)portDB.get(port);
                s.getRoot().getDeployState().getDeployLogger().log(Level.WARNING, service.getServiceName() +" cannot reserve port " + port + " on " +
                        this + ": Already reserved for " + s.getServiceName() +
                        ". Using default port range from " + serviceBasePort);
                return false;
            }
            if (!service.requiresConsecutivePorts()) break;
        }
        return true;
    }

    /**
     * Reserves the desired port for the given service, or throws as exception if the port
     * is not available.
     *
     * @param service the service that wishes to reserve the port.
     * @param port the port to be reserved.
     */
    void reservePort(AbstractService service, int port) {
        if (portDB.containsKey(port)) {
            portAlreadyReserved(service, port);
        } else {
            if (inVespasPortRange(port)) {
                allocatedPorts++;
                if (allocatedPorts > MAX_PORTS) {
                    noMoreAvailablePorts();
                }
            }
            portDB.put(port, service);
        }
    }

    private boolean inVespasPortRange(int port) {
        return port >= BASE_PORT &&
                port < BASE_PORT + MAX_PORTS;
    }
        private void portAlreadyReserved(AbstractService service, int port) {
        AbstractService otherService = (AbstractService)portDB.get(port);
        int nextAvailablePort = nextAvailableBaseport(service.getPortCount());
        if (nextAvailablePort == 0) {
            noMoreAvailablePorts();
        }
        String msg = (service.getClass().equals(otherService.getClass()) && service.requiresWantedPort())
                ? "You must set port explicitly for all instances of this service type, except the first one. "
                : "";
        throw new RuntimeException(service.getServiceName() + " cannot reserve port " + port +
                    " on " + this + ": Already reserved for " + otherService.getServiceName() +
                    ". " + msg + "Next available port is: " + nextAvailablePort);
    }


    private void noMoreAvailablePorts() {
        throw new RuntimeException
            ("Too many ports are reserved in Vespa's port range (" +
                    BASE_PORT  + ".." + (BASE_PORT+MAX_PORTS) + ") on " + this +
                    ". Move one or more services to another host, or outside this port range.");
    }


    /**
     * Returns the service with the given "sentinel name" on this Host,
     * or null if the name does not match any service.
     *
     * @param sentinelName the sentinel name of the service we want to return
     * @return The service with the given sentinel name
     */
    public Service getService(String sentinelName) {
        return services.get(sentinelName);
    }

    /**
     * Returns a List of all services running on this Host.
     * @return a List of all services running on this Host.
     */
    public List<Service> getServices() {
        return new ArrayList<>(services.values());
    }

    public HostInfo getHostInfo() {
        return new HostInfo(getHostName(), services.values().stream()
                .map(service -> service.getServiceInfo())
                .collect(Collectors.toSet()));
    }

    public void setFlavor(Optional<String> flavor) { this.flavor = flavor; }

    /** Returns the flavor of this resource. Empty for self-hosted Vespa. */
    public Optional<String> getFlavor() { return flavor; }

    @Override
    public String toString() {
        return "host '" + host.getHostName() + "'";
    }

    public String getHostName() {
        return host.getHostName();
    }

    @Override
    public int hashCode() { return host.hashCode(); }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof HostResource)) return false;
        return ((HostResource)other).host.equals(this.host);
    }

    @Override
    public int compareTo(HostResource other) {
        return this.host.compareTo(other.host);
    }

}
