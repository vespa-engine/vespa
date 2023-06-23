// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostEvent;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.FatalProvisioningException;
import com.yahoo.vespa.hosted.provision.provisioning.HostIpConfig;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisionRequest;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionedHost;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
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
    private final Map<ClusterSpec.Type, Flavor> hostFlavors = new HashMap<>();
    private final Set<String> upgradableFlavors = new HashSet<>();
    private final Map<Behaviour, Integer> behaviours = new HashMap<>();

    private int deprovisionedHosts = 0;

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

    /** Returns whether given behaviour is active for this invocation */
    private boolean behaviour(Behaviour behaviour) {
        return behaviours.computeIfPresent(behaviour, (k, old) -> old == 0 ? null : --old) != null;
    }

    @Override
    public void provisionHosts(HostProvisionRequest request, Consumer<List<ProvisionedHost>> whenProvisioned) {
        if (behaviour(Behaviour.failProvisionRequest)) throw new NodeAllocationException("No capacity for provision request", true);
        Flavor hostFlavor = hostFlavors.get(request.clusterType().orElse(ClusterSpec.Type.content));
        if (hostFlavor == null)
            hostFlavor = flavors.stream()
                                .filter(f -> request.sharing() == HostSharing.exclusive ? compatible(f, request.resources())
                                                                              : f.resources().satisfies(request.resources()))
                                .findFirst()
                                .orElseThrow(() -> new NodeAllocationException("No host flavor matches " + request.resources(), true));

        List<ProvisionedHost> hosts = new ArrayList<>();
        for (int index : request.indices()) {
            String hostHostname = request.type() == NodeType.host ? "host" + index : request.type().name() + index;
            hosts.add(new ProvisionedHost("id-of-" + request.type().name() + index,
                                          hostHostname,
                                          hostFlavor,
                                          request.type(),
                                          request.sharing() == HostSharing.exclusive ? Optional.of(request.owner()) : Optional.empty(),
                                          Optional.empty(),
                                          createHostnames(request.type(), hostFlavor, index),
                                          request.resources(),
                                          request.osVersion(),
                                          request.cloudAccount()));
        }
        provisionedHosts.addAll(hosts);
        whenProvisioned.accept(hosts);
    }

    @Override
    public HostIpConfig provision(Node host) throws FatalProvisioningException {
        if (behaviour(Behaviour.failProvisioning)) throw new FatalProvisioningException("Failed to provision node(s)");
        if (host.state() != Node.State.provisioned) throw new IllegalStateException("Host to provision must be in " + Node.State.provisioned);
        Map<String, IP.Config> result = new HashMap<>();
        result.put(host.hostname(), createIpConfig(host));
        host.ipConfig().pool().hostnames().forEach(hostname ->
                result.put(hostname.value(), IP.Config.ofEmptyPool(nameResolver.resolveAll(hostname.value()))));
        return new HostIpConfig(result);
    }

    @Override
    public void deprovision(Node host) {
        if (behaviour(Behaviour.failDeprovisioning)) throw new FatalProvisioningException("Failed to deprovision node");
        provisionedHosts.removeIf(provisionedHost -> provisionedHost.hostHostname().equals(host.hostname()));
        deprovisionedHosts++;
    }

    @Override
    public Node replaceRootDisk(Node host) {
        if (!host.type().isHost()) throw new IllegalArgumentException(host + " is not a host");
        if (rebuildsCompleted.remove(host.hostname())) {
            return host.withWantToRetire(host.status().wantToRetire(), host.status().wantToDeprovision(),
                                         false, false, Agent.system, Instant.ofEpochMilli(123));
        }
        return host;
    }

    @Override
    public List<HostEvent> hostEventsIn(List<CloudAccount> cloudAccounts) {
        return Collections.unmodifiableList(hostEvents);
    }

    @Override
    public boolean canUpgradeFlavor(Node host, Node child) {
        return upgradableFlavors.contains(host.flavor().name());
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
        behaviours.put(first, Integer.MAX_VALUE);
        for (var b : rest) {
            behaviours.put(b, Integer.MAX_VALUE);
        }
        return this;
    }

    public MockHostProvisioner with(Behaviour behaviour, int count) {
        behaviours.put(behaviour, count);
        return this;
    }

    public MockHostProvisioner without(Behaviour first, Behaviour... rest) {
        behaviours.remove(first);
        for (var b : rest) {
            behaviours.remove(b);
        }
        return this;
    }

    public MockHostProvisioner completeRebuildOf(String hostname) {
        rebuildsCompleted.add(hostname);
        return this;
    }

    public MockHostProvisioner setHostFlavor(String flavorName, ClusterSpec.Type ... types) {
        Flavor flavor = flavors.stream().filter(f -> f.name().equals(flavorName))
                               .findFirst()
                               .orElseThrow(() -> new IllegalArgumentException("No such flavor '" + flavorName + "'"));
        if (types.length == 0)
            types = ClusterSpec.Type.values();
        for (var type : types)
            hostFlavors.put(type, flavor);
        return this;
    }

    public MockHostProvisioner addUpgradableFlavor(String name) {
        upgradableFlavors.add(name);
        return this;
    }

    /** Sets the host flavor to use to the flavor matching these resources exactly, if any. */
    public MockHostProvisioner setHostFlavorIfAvailable(NodeResources flavorAdvertisedResources, HostResourcesCalculator calculator, ClusterSpec.Type ... types) {
        Optional<Flavor> hostFlavor = flavors.stream().filter(f -> calculator.advertisedResourcesOf(f).compatibleWith(flavorAdvertisedResources))
                                             .findFirst();
        if (types.length == 0)
            types = ClusterSpec.Type.values();
        for (var type : types)
            hostFlavor.ifPresent(f -> hostFlavors.put(type, f));
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

    private List<HostName> createHostnames(NodeType hostType, Flavor flavor, int hostIndex) {
        long numAddresses = Math.max(2, Math.round(flavor.resources().bandwidthGbps()));
        return IntStream.range(1, (int) numAddresses)
                        .mapToObj(i -> {
                            String hostname = hostType == NodeType.host
                                    ? "host" + hostIndex + "-" + i
                                    : hostType.childNodeType().name() + i;
                            return HostName.of(hostname);
                        })
                        .toList();
    }

    public IP.Config createIpConfig(Node node) {
        if (!node.type().isHost()) throw new IllegalArgumentException("Node " + node + " is not a host");
        int hostIndex = Integer.parseInt(node.hostname().replaceAll("^[a-z]+|-\\d+$", ""));
        Set<String> addresses = Set.of("::" + hostIndex + ":0");
        Set<String> ipAddressPool = new HashSet<>();
        if (!behaviour(Behaviour.failDnsUpdate)) {
            nameResolver.addRecord(node.hostname(), addresses.iterator().next());
            int i = 1;
            for (HostName hostName : node.ipConfig().pool().hostnames()) {
                String ip = "::" + hostIndex + ":" + i++;
                ipAddressPool.add(ip);
                nameResolver.addRecord(hostName.value(), ip);
            }
        }
        IP.Pool pool = node.ipConfig().pool().withIpAddresses(ipAddressPool);
        return node.ipConfig().withPrimary(addresses).withPool(pool);
    }

    public enum Behaviour {

        /** Fail call to {@link MockHostProvisioner#provision(com.yahoo.vespa.hosted.provision.Node)} */
        failProvisioning,

        /** Fail call to {@link MockHostProvisioner#provisionHosts(HostProvisionRequest, Consumer)} */
        failProvisionRequest,

        /** Fail call to {@link MockHostProvisioner#deprovision(com.yahoo.vespa.hosted.provision.Node)} */
        failDeprovisioning,

        /** Fail DNS updates of provisioned hosts */
        failDnsUpdate,

    }

}
