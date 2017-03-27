// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.test.MockRoot;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ProvisionLogger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.yahoo.log.LogLevel.DEBUG;

/**
 * The parent node for all Host instances, and thus accessible
 * to enable services to get their Host.
 *
 * @author gjoranv
 */
public class HostSystem extends AbstractConfigProducer<Host> {

    private Map<String,String> ipAddresses = new LinkedHashMap<>();
    private Map<String,String> hostnames = new LinkedHashMap<>();

    private final Map<String, HostResource> hostname2host = new LinkedHashMap<>();
    private final HostProvisioner provisioner;

    public HostSystem(AbstractConfigProducer parent, String name, HostProvisioner provisioner) {
        super(parent, name);
        this.provisioner = provisioner;
    }

    /**
     * Returns the host with the given hostname.
     *
     * @param name the hostname of the host.
     * @return the host with the given hostname.
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

    /**
     * Returns the canonical name of a given host. This will cache names for faster lookup.
     *
     * @param hostname the hostname to retrieve the canonical hostname for.
     * @return The canonical hostname, or null if unable to resolve.
     * @throws UnknownHostException if the hostname cannot be resolved
     */
    public String getCanonicalHostname(String hostname) throws UnknownHostException {
        if ( ! hostnames.containsKey(hostname)) {
            hostnames.put(hostname, lookupCanonicalHostname(hostname));
        }
        return hostnames.get(hostname);
    }

    /**
     * Static helper method that looks up the canonical name of a given host.
     *
     * @param hostname the hostname to retrieve the canonical hostname for.
     * @return The canonical hostname, or null if unable to resolve.
     * @throws UnknownHostException if the hostname cannot be resolved
     */
    // public - This is used by amenders outside this repo
    public static String lookupCanonicalHostname(String hostname) throws UnknownHostException {
        return java.net.InetAddress.getByName(hostname).getCanonicalHostName();
    }

    /**
     * Returns the if address of a host.
     *
     * @param hostname the hostname to retrieve the ip address for.
     * @return The string representation of the ip-address.
     */
    public String getIp(String hostname) {
        if (ipAddresses.containsKey(hostname)) return ipAddresses.get(hostname);

        String ipAddress;
        if (hostname.startsWith(MockRoot.MOCKHOST)) { // TODO: Remove
            ipAddress = "0.0.0.0";
        } else {
            try {
                InetAddress address = InetAddress.getByName(hostname);
                ipAddress = address.getHostAddress();
            } catch (java.net.UnknownHostException e) {
                log.warning("Unable to find valid IP address of host: " + hostname);
                ipAddress = "0.0.0.0";
            }
        }
        ipAddresses.put(hostname, ipAddress);
        return ipAddress;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (HostResource host : hostname2host.values()) {
            sb.append(host).append(",");
        }
        if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    public HostResource getHost(String hostAlias) {
        HostSpec hostSpec = provisioner.allocateHost(hostAlias);
        for (HostResource resource : hostname2host.values()) {
            if (resource.getHostName().equals(hostSpec.hostname())) {
                hostSpec.membership().ifPresent(resource::addClusterMembership);
                return resource;
            }
        }
        return addNewHost(hostSpec);
    }

    private HostResource addNewHost(HostSpec hostSpec) {
        Host host = new Host(this, hostSpec.hostname());
        HostResource hostResource = new HostResource(host);
        hostResource.setFlavor(hostSpec.flavor());
        hostSpec.membership().ifPresent(hostResource::addClusterMembership);
        hostname2host.put(host.getHostName(), hostResource);
        log.log(DEBUG, () -> "Added new host resource for " + host.getHostName() + " with flavor " + hostResource.getFlavor());
        return hostResource;
    }

    /** Returns the hosts owned by the application having this system - i.e all hosts except shared ones */
    public List<HostResource> getHosts() {
        return hostname2host.values().stream()
                .filter(host -> !host.getHost().isMultitenant())
                .collect(Collectors.toList());
    }

    public Map<HostResource, ClusterMembership> allocateHosts(ClusterSpec cluster, Capacity capacity, int groups, DeployLogger logger) {
        List<HostSpec> allocatedHosts = provisioner.prepare(cluster, capacity, groups, new ProvisionDeployLogger(logger));
        // TODO: Even if HostResource owns a set of memberships, we need to return a map because the caller needs the current membership.
        Map<HostResource, ClusterMembership> retAllocatedHosts = new LinkedHashMap<>();
        for (HostSpec spec : allocatedHosts) {
            // This is needed for single node host provisioner to work in unit tests for hosted vespa applications.
            HostResource host = getExistingHost(spec).orElseGet(() -> addNewHost(spec));
            retAllocatedHosts.put(host, spec.membership().orElse(null));
            if (! host.getFlavor().isPresent()) {
                host.setFlavor(spec.flavor());
                log.log(DEBUG, () -> "Host resource " + host.getHostName() + " had no flavor, setting to " + spec.flavor());
            }
        }
        retAllocatedHosts.keySet().forEach(host -> log.log(DEBUG, () -> "Allocated host " + host.getHostName() + " with flavor " + host.getFlavor()));
        return retAllocatedHosts;
    }

    private Optional<HostResource> getExistingHost(HostSpec key) {
        List<HostResource> hosts = hostname2host.values().stream()
                .filter(resource -> resource.getHostName().equals(key.hostname()))
                .collect(Collectors.toList());
        if (hosts.isEmpty()) {
            return Optional.empty();
        } else {
            log.log(DEBUG, () -> "Found existing host resource for " + key.hostname() + " with flavor " + hosts.get(0).getFlavor());
            return Optional.of(hosts.get(0));
        }
    }

    public void addBoundHost(HostResource host) {
        hostname2host.put(host.getHostName(), host);
    }

    Set<HostSpec> getSingleTenantHosts() {
        LinkedHashSet<HostSpec> hostSpecs = new LinkedHashSet<>();
        for (HostResource host: hostname2host.values()) {
            if (! host.getHost().isMultitenant()) {
                hostSpecs.add(new HostSpec(host.getHostName(), host.primaryClusterMembership()));
            }
        }
        return hostSpecs;
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
