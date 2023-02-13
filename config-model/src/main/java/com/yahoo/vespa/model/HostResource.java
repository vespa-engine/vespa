// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A host representation. The identity of this is the identity of its Host.
 * TODO: Merge with {@link Host}
 * Host resources are ordered by their host order.
 *
 * @author Ulf Lilleengen
 */
public class HostResource implements Comparable<HostResource> {

    private final HostSpec spec;

    private final HostPorts hostPorts;

    private final Host host;

    /** Map from "sentinel name" to service */
    private final Map<String, Service> services = new LinkedHashMap<>();

    /**
     * Create a new {@link HostResource} bound to a specific {@link com.yahoo.vespa.model.Host}.
     *
     * @param host {@link com.yahoo.vespa.model.Host} object to bind to.
     */
    public HostResource(Host host) {
        this(host, new HostSpec(host.getHostname(), Optional.empty()));
    }

    public HostResource(Host host, HostSpec spec) {
        this.host = host;
        this.spec = spec;
        this.hostPorts = new HostPorts(host.getHostname());
    }

    /**
     * Return the currently bound {@link com.yahoo.vespa.model.Host}.
     *
     * @return the {@link com.yahoo.vespa.model.Host} if bound, null if not.
     */
    public Host getHost() { return host; }

    public HostPorts ports() { return hostPorts; }

    public HostSpec spec() { return spec.withPorts(hostPorts.networkPorts()); }

    /**
     * Adds service and allocates resources for it.
     *
     * @param service the Service to allocate resources for
     * @param wantedPort the wanted port for this service
     * @return the allocated ports for the Service.
     */
    List<Integer> allocateService(DeployLogger deployLogger, AbstractService service, int wantedPort) {
        ports().useLogger(deployLogger);
        List<Integer> ports = hostPorts.allocatePorts(service, wantedPort);
        if (getService(service.getServiceName()) != null)
            throw new IllegalStateException("There is already a service with name '" + service.getServiceName() +
                                            "' registered on " + this + ". " +
                                            "Most likely a programming error - " +
                                            "all service classes must have unique names, even in different packages!");

        services.put(service.getServiceName(), service);
        return ports;
    }

    void deallocateService(AbstractService service) {
        hostPorts.deallocatePorts(service);
        services.remove(service.getServiceName());
    }

    /**
     * Returns the service with the given "sentinel name" on this Host,
     * or null if the name does not match any service.
     *
     * @param sentinelName the sentinel name of the service we want to return
     * @return the service with the given sentinel name
     */
    public Service getService(String sentinelName) {
        return services.get(sentinelName);
    }

    /** Returns a List of all services running on this Host. */
    public List<Service> getServices() {
        return new ArrayList<>(services.values());
    }

    public HostInfo getHostInfo() {
        return new HostInfo(getHostname(), services.values().stream()
                .map(Service::getServiceInfo)
                .collect(Collectors.toCollection(() -> new LinkedHashSet<>())));
    }

    /** The real resources available for Vespa processes on this node, after subtracting infrastructure overhead. */
    public NodeResources realResources() { return spec.realResources(); }

    /** The total advertised resources of this node, typically matching what's requested. */
    public NodeResources advertisedResources() { return spec.advertisedResources(); }

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
        if (this.spec.membership().isPresent() && other.spec.membership().isPresent())
            return Integer.compare(this.spec.membership().get().index(), other.spec.membership().get().index());
        else
            return this.getHostname().compareTo(other.getHostname());
    }

}
