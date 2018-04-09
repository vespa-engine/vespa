// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Flavor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * A host representation. The identity of this is the identity of its Host.
 * TODO: Merge with {@link Host}
 * Host resources are ordered by their host order.
 *
 * @author Ulf Lilleengen
 */
public class HostResource implements Comparable<HostResource> {

    public final static int BASE_PORT = 19100;
    final static int MAX_PORTS = 799;
    private final Host host;

    /** Map from "sentinel name" to service */
    private final Map<String, Service> services = new LinkedHashMap<>();

    private final Map<Integer, Service> portDB = new LinkedHashMap<>();

    private int allocatedPorts = 0;

    private Set<ClusterMembership> clusterMemberships = new LinkedHashSet<>();

    // Empty for self-hosted Vespa.
    private Optional<Flavor> flavor = Optional.empty();

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
        return range == numPorts ? port - range : 0;
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

    private List<Integer> allocatePorts(AbstractService service, int wantedPort) {
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
                    ". " + msg + "Next available port is: " + nextAvailablePort + " ports used: " + portDB);
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
        return new HostInfo(getHostname(), services.values().stream()
                .map(Service::getServiceInfo)
                .collect(Collectors.toSet()));
    }

    public void setFlavor(Optional<Flavor> flavor) { this.flavor = flavor; }

    /** Returns the flavor of this resource. Empty for self-hosted Vespa. */
    public Optional<Flavor> getFlavor() { return flavor; }

    public void addClusterMembership(@Nullable ClusterMembership clusterMembership) {
        if (clusterMembership != null)
            clusterMemberships.add(clusterMembership);
    }

    public Set<ClusterMembership> clusterMemberships() {
        return Collections.unmodifiableSet(clusterMemberships);
    }

    /**
     * Returns the "primary" cluster membership.
     * Content clusters are preferred, then container clusters, and finally admin clusters.
     * If there is more than one cluster of the preferred type, the cluster that was added first will be chosen.
     */
    public Optional<ClusterMembership> primaryClusterMembership() {
        return clusterMemberships().stream()
                .sorted(HostResource::compareClusters)
                .findFirst();
    }

    private static int compareClusters(ClusterMembership cluster1, ClusterMembership cluster2) {
        // This depends on the declared order of enum constants.
        return cluster2.cluster().type().compareTo(cluster1.cluster().type());
    }

    @Override
    public String toString() {
        return "host '" + host.getHostname() + "'";
    }

    public String getHostname() {
        return host.getHostname();
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

    /**
     * Compares by the index of the primary membership, if both hosts are members in at least one cluster at this time.
     * Compare by hostname otherwise.
     */
    public int comparePrimarilyByIndexTo(HostResource other) {
        Optional<ClusterMembership> thisMembership = this.primaryClusterMembership();
        Optional<ClusterMembership> otherMembership = other.primaryClusterMembership();
        if (thisMembership.isPresent() && otherMembership.isPresent())
            return Integer.compare(thisMembership.get().index(), otherMembership.get().index());
        else
            return this.getHostname().compareTo(other.getHostname());
    }

    /**
     * Picks hosts by some mixture of host name and index 
     * (where the mix of one or the other is decided by the last parameter).
     */
    public static List<HostResource> pickHosts(Collection<HostResource> hosts, int count, int targetHostsSelectedByIndex) {
        targetHostsSelectedByIndex = Math.min(Math.min(targetHostsSelectedByIndex, count), hosts.size());

        List<HostResource> hostsSortedByName = new ArrayList<>(hosts);
        Collections.sort(hostsSortedByName);

        List<HostResource> hostsSortedByIndex = new ArrayList<>(hosts);
        hostsSortedByIndex.sort((a, b) -> a.comparePrimarilyByIndexTo(b));
        return pickHosts(hostsSortedByName, hostsSortedByIndex, count, targetHostsSelectedByIndex);
    }
    public static List<HostResource> pickHosts(List<HostResource> hostsSelectedByName, List<HostResource> hostsSelectedByIndex, 
                                               int count, int targetHostsSelectedByIndex) {
        hostsSelectedByName = hostsSelectedByName.subList(0, Math.min(count - targetHostsSelectedByIndex, hostsSelectedByName.size()));
        hostsSelectedByIndex.removeAll(hostsSelectedByName);
        hostsSelectedByIndex = hostsSelectedByIndex.subList(0, Math.min(targetHostsSelectedByIndex, hostsSelectedByIndex.size()));

        List<HostResource> finalHosts = new ArrayList<>();
        finalHosts.addAll(hostsSelectedByName);
        finalHosts.addAll(hostsSelectedByIndex);
        return finalHosts;
    }

}
