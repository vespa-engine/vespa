// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostEvent;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Address;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.FatalProvisioningException;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionedHost;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author mpolden
 */
public class MockHostProvisioner implements HostProvisioner {

    private final List<ProvisionedHost> provisionedHosts = new ArrayList<>();
    private final List<HostEvent> hostEvents = new ArrayList<>();
    private final List<Flavor> flavors;
    private final MockNameResolver nameResolver;
    private final int memoryTaxGb;
    private final Set<String> rebuildsCompleted = new HashSet<>();

    private int deprovisionedHosts = 0;
    private EnumSet<Behaviour> behaviours = EnumSet.noneOf(Behaviour.class);
    private Optional<Flavor> hostFlavor = Optional.empty();

    public MockHostProvisioner(List<Flavor> flavors, MockNameResolver nameResolver, int memoryTaxGb) {
        this.flavors = List.copyOf(flavors);
        this.nameResolver = nameResolver;
        this.memoryTaxGb = memoryTaxGb;
    }

    public MockHostProvisioner(List<Flavor> flavors) {
        this(flavors, 0);
    }

    public MockHostProvisioner(List<Flavor> flavors, int memoryTaxGb) {
        this(flavors, new MockNameResolver().mockAnyLookup(), memoryTaxGb);
    }

    @Override
    public void provisionHosts(List<Integer> provisionIndices, NodeType hostType, NodeResources resources,
                               ApplicationId applicationId, Version osVersion, HostSharing sharing,
                               Optional<ClusterSpec.Type> clusterType, CloudAccount cloudAccount,
                               Consumer<List<ProvisionedHost>> provisionedHostsConsumer) {
        Flavor hostFlavor = this.hostFlavor.orElseGet(() -> flavors.stream()
                                                                   .filter(f -> compatible(f, resources))
                                                                   .findFirst()
                                                                   .orElseThrow(() -> new NodeAllocationException("No host flavor matches " + resources, true)));
        List<ProvisionedHost> hosts = new ArrayList<>();
        for (int index : provisionIndices) {
            String hostHostname = hostType == NodeType.host ? "host" + index : hostType.name() + index;
            hosts.add(new ProvisionedHost("id-of-" + hostType.name() + index,
                                          hostHostname,
                                          hostFlavor,
                                          hostType,
                                          sharing == HostSharing.exclusive ? Optional.of(applicationId) : Optional.empty(),
                                          Optional.empty(),
                                          createAddressesForHost(hostType, hostFlavor, index),
                                          resources,
                                          osVersion,
                                          cloudAccount));
        }
        provisionedHosts.addAll(hosts);
        provisionedHostsConsumer.accept(hosts);
    }

    @Override
    public List<Node> provision(Node host, Set<Node> children) throws FatalProvisioningException {
        if (behaviours.contains(Behaviour.failProvisioning)) throw new FatalProvisioningException("Failed to provision node(s)");
        if (host.state() != Node.State.provisioned) throw new IllegalStateException("Host to provision must be in " + Node.State.provisioned);
        List<Node> result = new ArrayList<>();
        result.add(withIpAssigned(host));
        for (var child : children) {
            if (child.state() != Node.State.reserved) throw new IllegalStateException("Child to provisioned must be in " + Node.State.reserved);
            result.add(withIpAssigned(child));
        }
        return result;
    }

    @Override
    public void deprovision(Node host) {
        if (behaviours.contains(Behaviour.failDeprovisioning)) throw new FatalProvisioningException("Failed to deprovision node");
        provisionedHosts.removeIf(provisionedHost -> provisionedHost.hostHostname().equals(host.hostname()));
        deprovisionedHosts++;
    }

    @Override
    public Node replaceRootDisk(Node host) {
        if (!host.type().isHost()) throw new IllegalArgumentException(host + " is not a host");
        if (rebuildsCompleted.remove(host.hostname())) {
            return host.withWantToRetire(host.status().wantToRetire(), host.status().wantToDeprovision(),
                                         false, Agent.system, Instant.ofEpochMilli(123));
        }
        return host;
    }

    @Override
    public List<HostEvent> hostEventsIn(List<CloudAccount> cloudAccounts) {
        return Collections.unmodifiableList(hostEvents);
    }

    /** Returns the hosts that have been provisioned by this  */
    public List<ProvisionedHost> provisionedHosts() {
        return Collections.unmodifiableList(provisionedHosts);
    }

    /** Returns the number of hosts deprovisioned by this */
    public int deprovisionedHosts() {
        return deprovisionedHosts;
    }

    public MockHostProvisioner with(Behaviour first, Behaviour... rest) {
        this.behaviours = EnumSet.of(first, rest);
        return this;
    }

    public MockHostProvisioner without(Behaviour first, Behaviour... rest) {
        Set<Behaviour> behaviours = new HashSet<>(this.behaviours);
        behaviours.removeAll(EnumSet.of(first, rest));
        this.behaviours = behaviours.isEmpty() ? EnumSet.noneOf(Behaviour.class) : EnumSet.copyOf(behaviours);
        return this;
    }

    public MockHostProvisioner completeRebuildOf(String hostname) {
        rebuildsCompleted.add(hostname);
        return this;
    }

    public MockHostProvisioner overrideHostFlavor(String flavorName) {
        Flavor flavor = flavors.stream().filter(f -> f.name().equals(flavorName))
                               .findFirst()
                               .orElseThrow(() -> new IllegalArgumentException("No such flavor '" + flavorName + "'"));
        hostFlavor = Optional.of(flavor);
        return this;
    }

    public MockHostProvisioner addEvent(HostEvent event) {
        hostEvents.add(event);
        return this;
    }

    public boolean compatible(Flavor flavor, NodeResources resources) {
        NodeResources resourcesToVerify = resources.withMemoryGb(resources.memoryGb() - memoryTaxGb);

        if (flavor.resources().storageType() == NodeResources.StorageType.remote
            && flavor.resources().diskGb() >= resources.diskGb())
            resourcesToVerify = resourcesToVerify.withDiskGb(flavor.resources().diskGb());
        if (flavor.resources().bandwidthGbps() >= resources.bandwidthGbps())
            resourcesToVerify = resourcesToVerify.withBandwidthGbps(flavor.resources().bandwidthGbps());
        return flavor.resources().compatibleWith(resourcesToVerify);
    }

    private List<Address> createAddressesForHost(NodeType hostType, Flavor flavor, int hostIndex) {
        long numAddresses = Math.max(2, Math.round(flavor.resources().bandwidthGbps()));
        return IntStream.range(1, (int) numAddresses)
                        .mapToObj(i -> {
                            String hostname = hostType == NodeType.host
                                    ? "host" + hostIndex + "-" + i
                                    : hostType.childNodeType().name() + i;
                            return new Address(hostname);
                        })
                        .collect(Collectors.toList());
    }

    private Node withIpAssigned(Node node) {
        if (!node.type().isHost()) {
            return node.with(node.ipConfig().withPrimary(nameResolver.resolveAll(node.hostname())));
        }
        int hostIndex = Integer.parseInt(node.hostname().replaceAll("^[a-z]+|-\\d+$", ""));
        Set<String> addresses = Set.of("::" + hostIndex + ":0");
        Set<String> ipAddressPool = new HashSet<>();
        if (!behaviours.contains(Behaviour.failDnsUpdate)) {
            nameResolver.addRecord(node.hostname(), addresses.iterator().next());
            for (int i = 1; i <= 2; i++) {
                String ip = "::" + hostIndex + ":" + i;
                ipAddressPool.add(ip);
                nameResolver.addRecord(node.hostname() + "-" + i, ip);
            }
        }
        IP.Pool pool = node.ipConfig().pool().withIpAddresses(ipAddressPool);
        return node.with(node.ipConfig().withPrimary(addresses).withPool(pool));
    }

    public enum Behaviour {

        /** Fail all calls to {@link MockHostProvisioner#provision(com.yahoo.vespa.hosted.provision.Node, java.util.Set)} */
        failProvisioning,

        /** Fail all calls to {@link MockHostProvisioner#deprovision(com.yahoo.vespa.hosted.provision.Node)} */
        failDeprovisioning,

        /** Fail DNS updates of provisioned hosts */
        failDnsUpdate,

    }

}
