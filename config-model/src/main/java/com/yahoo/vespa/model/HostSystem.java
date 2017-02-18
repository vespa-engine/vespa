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
    private final Map<HostResource, Set<ClusterMembership>> mapping = new LinkedHashMap<>();

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
        for (HostResource host : mapping.keySet()) {
            sb.append(host).append(",");
        }
        if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    public HostResource getHost(String hostAlias) {
        HostSpec hostSpec = provisioner.allocateHost(hostAlias);
        for (Map.Entry<HostResource, Set<ClusterMembership>> entrySet : mapping.entrySet()) {
            HostResource resource = entrySet.getKey();
            if (resource.getHostName().equals(hostSpec.hostname())) {
                entrySet.getValue().add(hostSpec.membership().orElse(null));
                return resource;
            }
        }
        return addNewHost(hostSpec);
    }

    private HostResource addNewHost(HostSpec hostSpec) {
        Host host = new Host(this, hostSpec.hostname());
        HostResource hostResource = new HostResource(host);
        hostResource.setFlavor(hostSpec.flavor());
        hostname2host.put(host.getHostName(), hostResource);
        Set<ClusterMembership> hostMemberships = new LinkedHashSet<>();
        if (hostSpec.membership().isPresent())
            hostMemberships.add(hostSpec.membership().get());
        mapping.put(hostResource, hostMemberships);
        return hostResource;
    }

    /** Returns the hosts owned by the application having this system - i.e all hosts except shared ones */
    public List<HostResource> getHosts() {
        return mapping.keySet().stream()
                .filter(host -> !host.getHost().isMultitenant())
                .collect(Collectors.toList());
    }

    public Map<HostResource, ClusterMembership> allocateHosts(ClusterSpec cluster, Capacity capacity, int groups, DeployLogger logger) {
        List<HostSpec> allocatedHosts = provisioner.prepare(cluster, capacity, groups, new ProvisionDeployLogger(logger));
        // TODO: Let hostresource own a set of memberships, but we still need the map here because the caller needs the current membership.
        Map<HostResource, ClusterMembership> retAllocatedHosts = new LinkedHashMap<>();
        for (HostSpec spec : allocatedHosts) {
            // This is needed for single node host provisioner to work in unit tests for hosted vespa applications.
            HostResource host = getExistingHost(spec).orElseGet(() -> addNewHost(spec));
            retAllocatedHosts.put(host, spec.membership().orElse(null));
            if (! host.getFlavor().isPresent())
                host.setFlavor(spec.flavor());
        }
        return retAllocatedHosts;
    }

    private Optional<HostResource> getExistingHost(HostSpec key) {
        List<HostResource> hosts = mapping.keySet().stream()
                .filter(resource -> resource.getHostName().equals(key.hostname()))
                .collect(Collectors.toList());
        if (hosts.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(hosts.get(0));
        }
    }

    public void addBoundHost(HostResource host) {
        mapping.put(host, new LinkedHashSet<>());
        hostname2host.put(host.getHostName(), host);
    }

    Map<HostSpec, Set<ClusterMembership>> getHostToServiceSpecMapping() {
        Map<HostSpec, Set<ClusterMembership>> specMapping = new LinkedHashMap<>();
        for (Map.Entry<HostResource, Set<ClusterMembership>> entrySet : mapping.entrySet()) {
            if (!entrySet.getKey().getHost().isMultitenant()) {
                Optional<ClusterMembership> membership = entrySet.getValue().stream().filter(m -> m != null).findFirst();
                specMapping.put(new HostSpec(entrySet.getKey().getHostName(), membership), new LinkedHashSet<>(entrySet.getValue()));
            }
        }
        return specMapping;
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
