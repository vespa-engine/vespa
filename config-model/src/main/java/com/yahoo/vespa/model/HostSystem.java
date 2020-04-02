// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ProvisionLogger;

import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.log.LogLevel.DEBUG;

/**
 * The parent node for all Host instances, and thus accessible
 * to enable services to get their Host.
 *
 * @author gjoranv
 */
public class HostSystem extends AbstractConfigProducer<Host> {

    private static Logger log = Logger.getLogger(HostSystem.class.getName());

    private final Map<String, HostResource> hostname2host = new LinkedHashMap<>();
    private final HostProvisioner provisioner;
    private final DeployLogger deployLogger;

    public HostSystem(AbstractConfigProducer<?> parent, String name, HostProvisioner provisioner, DeployLogger deployLogger) {
        super(parent, name);
        this.provisioner = provisioner;
        this.deployLogger = deployLogger;
    }

    void checkName(String hostname) {
        // Give a warning if the host does not exist
        try {
            @SuppressWarnings("unused")
            Object ignore = java.net.InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            deployLogger.log(Level.WARNING, "Unable to lookup IP address of host: " + hostname);
        }
        if (! hostname.contains(".")) {
            deployLogger.log(Level.WARNING, "Host named '" + hostname + "' may not receive any config " +
                                            "since it is not a canonical hostname. " +
                                            "Disregard this warning when testing in a Docker container.");
        }
    }

    /**
     * Returns the host with the given hostname.
     *
     * @param name the hostname of the host
     * @return the host with the given hostname, or null if no such host
     */
    public HostResource getHostByHostname(String name) {
        // TODO: please eliminate the following ugly hack
        if ("localhost.fortestingpurposesonly".equals(name)) {
            String localhost = "localhost";
            if ( ! getChildren().containsKey(localhost)) {
                new Host(this, localhost);
            }
            return new HostResource(getChildren().get(localhost));
        }
        return hostname2host.get(name);
    }

    @Override
    public String toString() {
        return "hosts [" + hostname2host.values().stream()
                                                 .map(HostResource::getHostname)
                                                 .collect(Collectors.joining(", ")) +
               "]";
    }

    public HostResource getHost(String hostAlias) {
        HostSpec hostSpec = provisioner.allocateHost(hostAlias);
        HostResource resource = hostname2host.get(hostSpec.hostname());
        return resource != null ? resource : addNewHost(hostSpec);
    }

    private HostResource addNewHost(HostSpec hostSpec) {
        Host host = Host.createHost(this, hostSpec.hostname());
        HostResource hostResource = new HostResource(host, hostSpec);
        hostSpec.networkPorts().ifPresent(np -> hostResource.ports().addNetworkPorts(np));
        hostname2host.put(host.getHostname(), hostResource);
        return hostResource;
    }

    /** Returns the hosts owned by the application having this system - i.e all hosts except config servers */
    public List<HostResource> getHosts() {
        return hostname2host.values().stream()
                .filter(host -> !host.getHost().runsConfigServer())
                .collect(Collectors.toList());
    }

    public void dumpPortAllocations() {
        for (HostResource hr : getHosts()) {
            hr.ports().flushPortReservations();
        }
    }

    public Map<HostResource, ClusterMembership> allocateHosts(ClusterSpec cluster, Capacity capacity, DeployLogger logger) {
        List<HostSpec> allocatedHosts = provisioner.prepare(cluster, capacity, new ProvisionDeployLogger(logger));
        // TODO: Even if HostResource owns a set of memberships, we need to return a map because the caller needs the current membership.
        Map<HostResource, ClusterMembership> retAllocatedHosts = new LinkedHashMap<>();
        for (HostSpec spec : allocatedHosts) {
            // This is needed for single node host provisioner to work in unit tests for hosted vespa applications.
            HostResource host = getExistingHost(spec).orElseGet(() -> addNewHost(spec));
            retAllocatedHosts.put(host, spec.membership().orElse(null));
        }
        retAllocatedHosts.keySet().forEach(host -> log.log(DEBUG, () -> "Allocated host " + host.getHostname() + " with flavor " + host.getFlavor()));
        return retAllocatedHosts;
    }

    private Optional<HostResource> getExistingHost(HostSpec key) {
        List<HostResource> hosts = hostname2host.values().stream()
                .filter(resource -> resource.getHostname().equals(key.hostname()))
                .collect(Collectors.toList());
        if (hosts.isEmpty()) {
            return Optional.empty();
        } else {
            log.log(DEBUG, () -> "Found existing host resource for " + key.hostname() + " with flavor " + hosts.get(0).getFlavor());
            return Optional.of(hosts.get(0));
        }
    }

    public void addBoundHost(HostResource host) {
        hostname2host.put(host.getHostname(), host);
    }

    Set<HostSpec> getHostSpecs() {
        return getHosts().stream().map(HostResource::spec).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** A provision logger which forwards to a deploy logger */
    private static class ProvisionDeployLogger implements ProvisionLogger {

        private final DeployLogger deployLogger;

        public ProvisionDeployLogger(DeployLogger deployLogger) {
            this.deployLogger = deployLogger;
        }

        @Override
        public void log(Level level, String message) {
            deployLogger.log(level, message);
        }

    }

}
