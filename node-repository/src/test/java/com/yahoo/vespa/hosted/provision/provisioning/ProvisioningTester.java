// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ActivationContext;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeResources.DiskSpeed;
import com.yahoo.config.provision.NodeResources.StorageType;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.ProvisionLock;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provisioning.FlavorsConfig;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.Nodelike;
import com.yahoo.vespa.hosted.provision.autoscale.MemoryMetricsDb;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerServiceMock;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.filter.NodeHostFilter;
import com.yahoo.vespa.hosted.provision.persistence.NameResolver;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;
import com.yahoo.vespa.hosted.provision.testutils.MockProvisionServiceProvider;
import com.yahoo.vespa.hosted.provision.testutils.OrchestratorMock;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.service.duper.ConfigServerApplication;

import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.NodeResources.StorageType.local;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A test utility for provisioning tests.
 *
 * @author bratseth
 */
public class ProvisioningTester {

    public static final ApplicationId tenantHostApp = ApplicationId.from("hosted-vespa", "tenant-host", "default");

    private final Curator curator;
    private final NodeFlavors nodeFlavors;
    private final ManualClock clock;
    private final NodeRepository nodeRepository;
    private final HostProvisioner hostProvisioner;
    private final NodeRepositoryProvisioner provisioner;
    private final CapacityPolicies capacityPolicies;
    private final ProvisionLogger provisionLogger;
    private final LoadBalancerServiceMock loadBalancerService;

    private int nextHost = 0;
    private int nextIP = 0;

    private ProvisioningTester(Curator curator,
                              NodeFlavors nodeFlavors,
                              HostResourcesCalculator resourcesCalculator,
                              Zone zone,
                              NameResolver nameResolver,
                              DockerImage containerImage,
                              Orchestrator orchestrator,
                              HostProvisioner hostProvisioner,
                              LoadBalancerServiceMock loadBalancerService,
                              FlagSource flagSource,
                              int spareCount) {
        this.curator = curator;
        this.nodeFlavors = nodeFlavors;
        this.clock = new ManualClock();
        this.hostProvisioner = hostProvisioner;
        ProvisionServiceProvider provisionServiceProvider = new MockProvisionServiceProvider(loadBalancerService, hostProvisioner, resourcesCalculator);
        this.nodeRepository = new NodeRepository(nodeFlavors,
                                                 provisionServiceProvider,
                                                 curator,
                                                 clock,
                                                 zone,
                                                 nameResolver,
                                                 containerImage,
                                                 Optional.empty(),
                                                 Optional.empty(),
                                                 flagSource,
                                                 new MemoryMetricsDb(clock),
                                                 orchestrator,
                                                 true,
                                                 spareCount,
                                                 1000);
        this.provisioner = new NodeRepositoryProvisioner(nodeRepository, zone, provisionServiceProvider);
        this.capacityPolicies = new CapacityPolicies(nodeRepository);
        this.provisionLogger = new NullProvisionLogger();
        this.loadBalancerService = loadBalancerService;
    }

    public static FlavorsConfig createConfig() {
        FlavorConfigBuilder b = new FlavorConfigBuilder();
        b.addFlavor("default", 2., 40., 100, 10, Flavor.Type.BARE_METAL).cost(3);
        b.addFlavor("small", 2., 20., 50, 5, Flavor.Type.BARE_METAL).cost(2);
        b.addFlavor("dockerSmall", 1., 10., 10, 1, Flavor.Type.DOCKER_CONTAINER).cost(1);
        b.addFlavor("dockerLarge", 2., 10., 20, 1, Flavor.Type.DOCKER_CONTAINER).cost(3);
        b.addFlavor("v-4-8-100", 4., 80., 100, 10, Flavor.Type.VIRTUAL_MACHINE).cost(4);
        b.addFlavor("large", 4., 80., 100, 10, Flavor.Type.BARE_METAL).cost(10);
        b.addFlavor("arm64", 4., 80., 100, 10, Flavor.Type.BARE_METAL, NodeResources.Architecture.arm64).cost(15);
        return b.build();
    }

    public Curator getCurator() {
        return curator;
    }

    public void advanceTime(TemporalAmount duration) { clock.advance(duration); }
    public NodeRepository nodeRepository() { return nodeRepository; }
    public Orchestrator orchestrator() { return nodeRepository.orchestrator(); }
    public ManualClock clock() { return clock; }
    public NodeRepositoryProvisioner provisioner() { return provisioner; }
    public HostProvisioner hostProvisioner() { return hostProvisioner; }
    public LoadBalancerServiceMock loadBalancerService() { return loadBalancerService; }
    public CapacityPolicies capacityPolicies() { return capacityPolicies; }
    public NodeList getNodes(ApplicationId id, Node.State ... inState) { return nodeRepository.nodes().list(inState).owner(id); }
    public InMemoryFlagSource flagSource() { return (InMemoryFlagSource) nodeRepository.flagSource(); }
    public Node node(String hostname) { return nodeRepository.nodes().node(hostname).get(); }

    public int decideSize(Capacity capacity, ApplicationId application) {
        return capacityPolicies.applyOn(capacity, application, false).minResources().nodes();
    }

    public Node patchNode(Node node, UnaryOperator<Node> patcher) {
        return patchNodes(List.of(node), patcher).get(0);
    }

    public List<Node> patchNodes(Predicate<Node> filter, UnaryOperator<Node> patcher) {
        return patchNodes(nodeRepository.nodes().list().matching(filter).asList(), patcher);
    }

    public List<Node> patchNodes(List<Node> nodes, UnaryOperator<Node> patcher) {
        List<Node> updated = new ArrayList<>();
        for (var node : nodes) {
            try (var lock = nodeRepository.nodes().lockAndGetRequired(node)) {
                node = patcher.apply(lock.node());
                nodeRepository.nodes().write(node, lock);
                updated.add(node);
            }
        }
        return updated;
    }

    public List<HostSpec> prepare(ApplicationId application, ClusterSpec cluster, int nodeCount, int groups, NodeResources resources) {
        return prepare(application, cluster, nodeCount, groups, false, resources);
    }

    public List<HostSpec> prepare(ApplicationId application, ClusterSpec cluster, int nodeCount, int groups, boolean required, NodeResources resources) {
        return prepare(application, cluster, Capacity.from(new ClusterResources(nodeCount, groups, resources), required, true));
    }

    public List<HostSpec> prepare(ApplicationId application, ClusterSpec cluster, Capacity capacity) {
        List<HostSpec> hosts1 = provisioner.prepare(application, cluster, capacity, provisionLogger);
        List<HostSpec> hosts2 = provisioner.prepare(application, cluster, capacity, provisionLogger);
        assertEquals("Prepare is idempotent", hosts1, hosts2);
        return hosts1;
    }

    public Collection<HostSpec> activate(ApplicationId application, ClusterSpec cluster, Capacity capacity) {
        List<HostSpec> preparedNodes = prepare(application, cluster, capacity);

        // Add ip addresses and activate parent host if necessary
        for (HostSpec prepared : preparedNodes) {
            try (var lock = nodeRepository.nodes().lockAndGetRequired(prepared.hostname())) {
                Node node = lock.node();
                if (node.ipConfig().primary().isEmpty()) {
                    node = node.with(new IP.Config(Set.of("::" + 0 + ":0"), Set.of()));
                    nodeRepository.nodes().write(node, lock);
                }
                if (node.parentHostname().isEmpty()) continue;
                Node parent = nodeRepository.nodes().node(node.parentHostname().get()).get();
                if (parent.state() == Node.State.active) continue;
                NestedTransaction t = new NestedTransaction();
                if (parent.ipConfig().primary().isEmpty())
                    parent = parent.with(new IP.Config(Set.of("::" + 0 + ":0"), Set.of("::" + 0 + ":2")));
                nodeRepository.nodes().activate(List.of(parent), t);
                t.commit();
            }
        }

        return activate(application, preparedNodes);
    }

    public Collection<HostSpec> activate(ApplicationId application, Collection<HostSpec> hosts) {
        try (var lock = provisioner.lock(application)) {
            NestedTransaction transaction = new NestedTransaction();
            transaction.add(new CuratorTransaction(curator));
            provisioner.activate(hosts, new ActivationContext(0), new ApplicationTransaction(lock, transaction));
            transaction.commit();
        }
        assertEquals(toHostNames(hosts), nodeRepository.nodes().list(Node.State.active).owner(application).hostnames());
        return hosts;
    }

    /** Remove all resources allocated to application */
    public void remove(ApplicationId application) {
        try (var lock = provisioner.lock(application)) {
            NestedTransaction transaction = new NestedTransaction();
            provisioner.remove(new ApplicationTransaction(lock, transaction));
            transaction.commit();
        }
    }

    public List<HostSpec> prepareAndActivateInfraApplication(ApplicationId application, NodeType nodeType, Version version) {
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from(nodeType.toString()))
                                         .vespaVersion(version)
                                         .stateful(nodeType == NodeType.config || nodeType == NodeType.controller)
                                         .build();
        Capacity capacity = Capacity.fromRequiredNodeType(nodeType);
        List<HostSpec> hostSpecs = prepare(application, cluster, capacity);
        activate(application, hostSpecs);
        return hostSpecs;
    }

    public List<HostSpec> prepareAndActivateInfraApplication(ApplicationId application, NodeType nodeType) {
        return prepareAndActivateInfraApplication(application, nodeType, Version.fromString("6.42"));
    }

    public void deactivate(ApplicationId applicationId) {
        try (var lock = nodeRepository.applications().lock(applicationId)) {
            NestedTransaction deactivateTransaction = new NestedTransaction();
            nodeRepository.remove(new ApplicationTransaction(new ProvisionLock(applicationId, lock),
                                                             deactivateTransaction));
            deactivateTransaction.commit();
        }
    }

    public Collection<String> toHostNames(Collection<HostSpec> hosts) {
        return hosts.stream().map(HostSpec::hostname).collect(Collectors.toSet());
    }

    /**
     * Asserts that each active node in this application has a restart count equaling the
     * number of matches to the given filters
     */
    public void assertRestartCount(ApplicationId application, HostFilter... filters) {
        for (Node node : nodeRepository.nodes().list(Node.State.active).owner(application)) {
            int expectedRestarts = 0;
            for (HostFilter filter : filters)
                if (NodeHostFilter.from(filter).test(node))
                    expectedRestarts++;
            assertEquals(expectedRestarts, node.allocation().get().restartGeneration().wanted());
        }
    }

    /** Assert on the current *non retired* nodes */
    public void assertNodes(String explanation, int nodes, int groups, double vcpu, double memory, double disk,
                            ApplicationId app, ClusterSpec cluster) {
        assertNodes(explanation, nodes, groups, vcpu, memory, disk, 0.1, app, cluster);
    }

    /** Assert on the current *non retired* nodes */
    public void assertNodes(String explanation, int nodes, int groups, double vcpu, double memory, double disk, double bandwidth,
                            ApplicationId app, ClusterSpec cluster) {
        assertNodes(explanation, nodes, groups, vcpu, memory, disk, bandwidth, DiskSpeed.getDefault(), StorageType.getDefault(), app, cluster);
    }

    public void assertNodes(String explanation, int nodes, int groups,
                            double vcpu, double memory, double disk,
                            DiskSpeed diskSpeed, StorageType storageType,
                            ApplicationId app, ClusterSpec cluster) {
        assertNodes(explanation, nodes, groups, vcpu, memory, disk, 0.1, diskSpeed, storageType, app, cluster);
    }

    public void assertNodes(String explanation, int nodes, int groups,
                            double vcpu, double memory, double disk, double bandwidth,
                            DiskSpeed diskSpeed, StorageType storageType,
                            ApplicationId app, ClusterSpec cluster) {
        List<Node> nodeList = nodeRepository.nodes().list().owner(app).cluster(cluster.id()).state(Node.State.active).not().retired().asList();
        assertEquals(explanation + ": Node count",
                     nodes,
                     nodeList.size());
        assertEquals(explanation + ": Group count",
                     groups,
                     nodeList.stream().map(n -> n.allocation().get().membership().cluster().group().get()).distinct().count());
        for (Node node : nodeList) {
            var expected = new NodeResources(vcpu, memory, disk, bandwidth, diskSpeed, storageType);
            assertTrue(explanation + ": Resources: Expected " + expected + " but was " + node.resources(),
                       expected.justNonNumbers().compatibleWith(node.resources().justNonNumbers()));
            assertEquals(explanation + ": Vcpu: Expected " + expected.vcpu() + " but was " + node.resources().vcpu(),
                         expected.vcpu(), node.resources().vcpu(), 0.05);
            assertEquals(explanation + ": Memory: Expected " + expected.memoryGb() + " but was " + node.resources().memoryGb(),
                         expected.memoryGb(), node.resources().memoryGb(), 0.05);
            assertEquals(explanation + ": Disk: Expected " + expected.diskGb() + " but was " + node.resources().diskGb(),
                         expected.diskGb(), node.resources().diskGb(), 0.05);
        }
    }

    public void fail(HostSpec host) {
        fail(host.hostname());
    }

    public void fail(String hostname) {
        int beforeFailCount = nodeRepository.nodes().node(hostname).get().status().failCount();
        Node failedNode = nodeRepository.nodes().fail(hostname, Agent.system, "Failing to unit test");
        assertTrue(nodeRepository.nodes().list(Node.State.failed).nodeType(NodeType.tenant).asList().contains(failedNode));
        assertEquals(beforeFailCount + 1, failedNode.status().failCount());
    }

    public void assertMembersOf(ClusterSpec requestedCluster, Collection<HostSpec> hosts) {
        Set<Integer> indices = new HashSet<>();
        for (HostSpec host : hosts) {
            ClusterSpec nodeCluster = host.membership().get().cluster();
            assertTrue(requestedCluster.satisfies(nodeCluster));
            if (requestedCluster.group().isPresent())
                assertEquals(requestedCluster.group(), nodeCluster.group());
            else
                assertEquals(0, nodeCluster.group().get().index());

            indices.add(host.membership().get().index());
        }
        assertEquals("Indexes in " + requestedCluster + " are disjunct", hosts.size(), indices.size());
    }

    public HostSpec removeOne(Set<HostSpec> hosts) {
        Iterator<HostSpec> i = hosts.iterator();
        HostSpec removed = i.next();
        i.remove();
        return removed;
    }

    public static ApplicationId applicationId() {
        return ApplicationId.from(
                TenantName.from(UUID.randomUUID().toString()),
                ApplicationName.from(UUID.randomUUID().toString()),
                InstanceName.from(UUID.randomUUID().toString()));
    }

    public static ApplicationId applicationId(String applicationName) {
        return ApplicationId.from("tenant", applicationName, "default");
    }

    public List<Node> makeReadyNodes(int n, String flavor) {
        return makeReadyNodes(n, flavor, NodeType.tenant);
    }

    /** Call {@link this#activateTenantHosts()} after this before deploying applications */
    public ProvisioningTester makeReadyHosts(int n, NodeResources resources) {
        makeReadyNodes(n, resources, NodeType.host, 5);
        return this;
    }

    public List<Node> makeReadyNodes(int n, NodeResources resources) {
        return makeReadyNodes(n, resources, NodeType.tenant, 0);
    }

    public List<Node> makeReadyNodes(int n, String flavor, NodeType type) {
        return makeReadyNodes(n, asFlavor(flavor, type), Optional.empty(), type, 0);
    }
    public List<Node> makeReadyNodes(int n, NodeResources resources, NodeType type) {
        return makeReadyNodes(n, new Flavor(resources), Optional.empty(), type, 0);
    }
    public List<Node> makeReadyNodes(int n, NodeResources resources, NodeType type, int ipAddressPoolSize) {
        return makeReadyNodes(n, resources, Optional.empty(), type, ipAddressPoolSize);
    }
    public List<Node> makeReadyNodes(int n, NodeResources resources, Optional<TenantName> reservedTo, NodeType type, int ipAddressPoolSize) {
        return makeReadyNodes(n, new Flavor(resources), reservedTo, type, ipAddressPoolSize);
    }

    public List<Node> makeProvisionedNodes(int count, String flavor, NodeType type, int ipAddressPoolSize) {
        return makeProvisionedNodes(count, flavor, type, ipAddressPoolSize, false);
    }

    public List<Node> makeProvisionedNodes(int n, String flavor, NodeType type, int ipAddressPoolSize, boolean dualStack) {
        return makeProvisionedNodes(n, asFlavor(flavor, type), Optional.empty(), type, ipAddressPoolSize, dualStack);
    }
    public List<Node> makeProvisionedNodes(int n, Flavor flavor, Optional<TenantName> reservedTo, NodeType type, int ipAddressPoolSize, boolean dualStack) {
        return makeProvisionedNodes(n, (index) -> "host-" + index + ".yahoo.com", flavor, reservedTo, type, ipAddressPoolSize, dualStack);
    }

    public List<Node> makeProvisionedNodes(int n, Function<Integer, String> nodeNamer, Flavor flavor, Optional<TenantName> reservedTo, NodeType type, int ipAddressPoolSize, boolean dualStack) {
        if (ipAddressPoolSize == 0 && type == NodeType.host) {
            ipAddressPoolSize = 1; // Tenant hosts must have at least one IP in their pool
        }
        List<Node> nodes = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            nextHost++;
            nextIP++;

            // One test involves two provision testers - to detect this we check if the
            // name resolver already contains the next host - if this is the case - bump the indices and move on
            String testIp = String.format("127.0.%d.%d", nextIP / 256, nextIP % 256);
            MockNameResolver nameResolver = (MockNameResolver)nodeRepository().nameResolver();
            if (nameResolver.resolveHostname(testIp).isPresent()) {
                nextHost += 100;
                nextIP += 100;
            }

            String hostname = nodeNamer.apply(nextHost);
            String[] hostnameParts = hostname.split("\\.", 2);
            String ipv4 = String.format("127.0.%d.%d", nextIP / 256, nextIP % 256);
            String ipv6 = String.format("::%x", nextIP);

            nameResolver.addRecord(hostname, ipv4, ipv6);
            HashSet<String> hostIps = new HashSet<>();
            hostIps.add(ipv4);
            hostIps.add(ipv6);

            Set<String> ipAddressPool = new LinkedHashSet<>();
            for (int poolIp = 1; poolIp <= ipAddressPoolSize; poolIp++) {
                nextIP++;
                String nodeHostname = hostnameParts[0] + "-" + poolIp + (hostnameParts.length > 1 ? "." + hostnameParts[1] : "");
                String ipv6Addr = String.format("::%x", nextIP);
                ipAddressPool.add(ipv6Addr);
                nameResolver.addRecord(nodeHostname, ipv6Addr);
                if (dualStack) {
                    String ipv4Addr = String.format("127.0.%d.%d", (127 + (nextIP / 256)), nextIP % 256);
                    ipAddressPool.add(ipv4Addr);
                    nameResolver.addRecord(nodeHostname, ipv4Addr);
                }
            }
            Node.Builder builder = Node.create(hostname, new IP.Config(hostIps, ipAddressPool), hostname, flavor, type);
            reservedTo.ifPresent(builder::reservedTo);
            nodes.add(builder.build());
        }
        nodes = nodeRepository.nodes().addNodes(nodes, Agent.system);
        return nodes;
    }

    public NodeList makeConfigServers(int n, String flavor, Version configServersVersion) {
        List<Node> nodes = new ArrayList<>(n);
        MockNameResolver nameResolver = (MockNameResolver)nodeRepository().nameResolver();

        for (int i = 1; i <= n; i++) {
            String hostname = "cfg" + i;
            String ipv4 = "127.0.1." + i;

            nameResolver.addRecord(hostname, ipv4);
            Node node = Node.create(hostname, new IP.Config(Set.of(ipv4), Set.of()), hostname,
                    nodeFlavors.getFlavorOrThrow(flavor), NodeType.config).build();
            nodes.add(node);
        }

        nodes = nodeRepository.nodes().addNodes(nodes, Agent.system);
        nodes = nodeRepository.nodes().deallocate(nodes, Agent.system, getClass().getSimpleName());
        move(Node.State.ready, nodes);

        ConfigServerApplication application = new ConfigServerApplication();
        List<HostSpec> hosts = prepare(application.getApplicationId(),
                                       application.getClusterSpecWithVersion(configServersVersion),
                                       application.getCapacity());
        activate(application.getApplicationId(), new HashSet<>(hosts));
        return nodeRepository.nodes().list(Node.State.active).owner(application.getApplicationId());
    }

    public List<Node> makeReadyNodes(int n, String flavor, NodeType type, int ipAddressPoolSize) {
        return makeReadyNodes(n, asFlavor(flavor, type), Optional.empty(), type, ipAddressPoolSize);
    }
    public List<Node> makeReadyNodes(int n, Flavor flavor, Optional<TenantName> reservedTo, NodeType type, int ipAddressPoolSize) {
        return makeReadyNodes(n, flavor, reservedTo, type, ipAddressPoolSize, false);
    }

    public List<Node> makeReadyNodes(int n, String flavor, NodeType type, int ipAddressPoolSize, boolean dualStack) {
        return makeReadyNodes(n, asFlavor(flavor, type), type, ipAddressPoolSize, dualStack);
    }
    public List<Node> makeReadyNodes(int n, Flavor flavor, NodeType type, int ipAddressPoolSize, boolean dualStack) {
        return makeReadyNodes(n, flavor, Optional.empty(), type, ipAddressPoolSize, dualStack);
    }
    public List<Node> makeReadyNodes(int n, Flavor flavor, Optional<TenantName> reservedTo, NodeType type, int ipAddressPoolSize, boolean dualStack) {
        List<Node> nodes = makeProvisionedNodes(n, flavor, reservedTo, type, ipAddressPoolSize, dualStack);
        nodes = nodeRepository.nodes().deallocate(nodes, Agent.system, getClass().getSimpleName());
        nodes.forEach(node -> { if (node.resources().isUnspecified()) throw new IllegalArgumentException(); });
        return move(Node.State.ready, nodes);
    }

    public Flavor asFlavor(String flavorString, NodeType type) {
        Optional<Flavor> flavor = nodeFlavors.getFlavor(flavorString);
        if (flavor.isEmpty()) {
            // TODO: Remove the need for this by always adding hosts with a given capacity
            if (type == NodeType.tenant) // Tenant nodes can have any (docker) flavor
                flavor = Optional.of(new Flavor(NodeResources.fromLegacyName(flavorString)));
            else
                throw new IllegalArgumentException("No flavor '" + flavorString + "'");
        }
        return flavor.get();
    }

    /** Create one or more child nodes on a single host starting with index 1 */
    public List<Node> makeReadyChildren(int n, NodeResources resources, String parentHostname) {
        return makeReadyChildren(n, 1, resources, parentHostname,
                                     index -> String.format("%s-%03d", parentHostname, index));
    }

    /** Create one or more child nodes without a parent starting with index 1 */
    public List<Node> makeReadyChildren(int n, NodeResources resources) {
        return makeReadyChildren(n, 1, resources, null,
                                     index -> UUID.randomUUID().toString());
    }

    /** Create one or more child nodes on given parent host */
    public List<Node> makeReadyChildren(int count, int startIndex, NodeResources resources, NodeType nodeType,
                                        String parentHostname, Function<Integer, String> nodeNamer) {
        if (nodeType.isHost()) throw new IllegalArgumentException("Non-child node type: " + nodeType);
        List<Node> nodes = new ArrayList<>(count);
        for (int i = startIndex; i < count + startIndex; i++) {
            String hostname = nodeNamer.apply(i);
            IP.Config ipConfig = new IP.Config(nodeRepository.nameResolver().resolveAll(hostname), Set.of());
            Node node = Node.create("node-id", ipConfig, hostname, new Flavor(resources), nodeType)
                            .parentHostname(parentHostname)
                            .build();
            nodes.add(node);
        }
        nodes = nodeRepository.nodes().addNodes(nodes, Agent.system);
        nodes = nodeRepository.nodes().deallocate(nodes, Agent.system, getClass().getSimpleName());
        return move(Node.State.ready, nodes);
    }

    /** Create one or more child nodes on given parent host */
    public List<Node> makeReadyChildren(int count, int startIndex, NodeResources resources, String parentHostname,
                                        Function<Integer, String> nodeNamer) {
        return makeReadyChildren(count, startIndex, resources, NodeType.tenant, parentHostname, nodeNamer);
    }

    public void activateTenantHosts() {
        prepareAndActivateInfraApplication(tenantHostApp, NodeType.host);
    }

    public static ClusterSpec containerClusterSpec() {
        return ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
    }

    public static ClusterSpec contentClusterSpec() {
        return ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("test")).vespaVersion("6.42").build();
    }

    public List<Node> deploy(ApplicationId application, Capacity capacity) {
        return deploy(application, containerClusterSpec(), capacity);
    }

    public List<Node> deploy(ApplicationId application, ClusterSpec cluster, Capacity capacity) {
        List<HostSpec> prepared = prepare(application, cluster, capacity);
        activate(application, Set.copyOf(prepared));
        return getNodes(application, Node.State.active).asList();
    }

    /** Returns the hosts from the input list which are not retired */
    public List<HostSpec> nonRetired(Collection<HostSpec> hosts) {
        return hosts.stream().filter(host -> ! host.membership().get().retired()).toList();
    }

    public void assertAllocatedOn(String explanation, String hostFlavor, ApplicationId app) {
        for (Node node : nodeRepository.nodes().list().owner(app)) {
            Node parent = nodeRepository.nodes().node(node.parentHostname().get()).get();
            assertEquals(node + ": " + explanation, hostFlavor, parent.flavor().name());
        }
    }

    public void assertSwitches(Set<String> expectedSwitches, ApplicationId application, ClusterSpec.Id cluster) {
        NodeList allNodes = nodeRepository.nodes().list();
        NodeList activeNodes = allNodes.owner(application).state(Node.State.active).cluster(cluster);
        assertEquals(expectedSwitches, switchesOf(activeNodes, allNodes));
    }

    public List<Node> activeNodesOn(String switchHostname, ApplicationId application, ClusterSpec.Id cluster) {
        NodeList allNodes = nodeRepository.nodes().list();
        NodeList activeNodes = allNodes.state(Node.State.active).owner(application).cluster(cluster);
        return activeNodes.stream().filter(node -> {
            Optional<String> allocatedSwitchHostname = allNodes.parentOf(node).flatMap(Node::switchHostname);
            return allocatedSwitchHostname.isPresent() &&
                   allocatedSwitchHostname.get().equals(switchHostname);
        }).toList();
    }

    public Set<String> switchesOf(NodeList applicationNodes, NodeList allNodes) {
        assertTrue("All application nodes are children", applicationNodes.stream().allMatch(node -> node.parentHostname().isPresent()));
        Set<String> switches = new HashSet<>();
        for (var parent : allNodes.parentsOf(applicationNodes)) {
            Optional<String> switchHostname = parent.switchHostname();
            if (switchHostname.isEmpty()) continue;
            switches.add(switchHostname.get());
        }
        return switches;
    }

    public int hostFlavorCount(String hostFlavor, ApplicationId app) {
        return (int)nodeRepository().nodes().list().owner(app).stream()
                                    .map(n -> nodeRepository().nodes().node(n.parentHostname().get()).get())
                                    .filter(p -> p.flavor().name().equals(hostFlavor))
                                    .count();
    }

    public Node move(Node.State toState, String hostname) {
        return move(toState, nodeRepository.nodes().node(hostname).orElseThrow());
    }
    public Node move(Node.State toState, Node node) {
        return move(toState, List.of(node)).get(0);
    }
    public List<Node> move(Node.State toState, List<Node> nodes) {
        return nodeRepository.database().writeTo(toState, nodes, Agent.operator, Optional.of("ProvisionTester"));
    }

    public static final class Builder {

        private Curator curator;
        private FlavorsConfig flavorsConfig;
        private HostResourcesCalculator resourcesCalculator = new EmptyProvisionServiceProvider().getHostResourcesCalculator();
        private Zone zone;
        private NameResolver nameResolver;
        private Orchestrator orchestrator;
        private HostProvisioner hostProvisioner;
        private FlagSource flagSource;
        private int spareCount = 0;
        private DockerImage defaultImage = DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa");

        public Builder curator(Curator curator) {
            this.curator = curator;
            return this;
        }

        public Builder flavorsConfig(FlavorsConfig flavorsConfig) {
            this.flavorsConfig = flavorsConfig;
            return this;
        }

        public Builder flavors(List<Flavor> flavors) {
            this.flavorsConfig = asConfig(flavors);
            return this;
        }

        public Builder resourcesCalculator(HostResourcesCalculator resourcesCalculator) {
            if (resourcesCalculator != null)
                this.resourcesCalculator = resourcesCalculator;
            return this;
        }

        public Builder resourcesCalculator(int memoryTax, int diskTax) {
            this.resourcesCalculator = new MockResourcesCalculator(memoryTax, diskTax);
            return this;
        }

        public Builder zone(Zone zone) {
            this.zone = zone;
            return this;
        }

        public Builder nameResolver(NameResolver nameResolver) {
            this.nameResolver = nameResolver;
            return this;
        }

        public Builder defaultImage(DockerImage defaultImage) {
            this.defaultImage = defaultImage;
            return this;
        }

        public Builder orchestrator(Orchestrator orchestrator) {
            this.orchestrator = orchestrator;
            return this;
        }

        public Builder hostProvisioner(HostProvisioner hostProvisioner) {
            this.hostProvisioner = hostProvisioner;
            return this;
        }

        public Builder flagSource(FlagSource flagSource) {
            this.flagSource = flagSource;
            return this;
        }

        public Builder spareCount(int spareCount) {
            this.spareCount = spareCount;
            return this;
        }

        private FlagSource defaultFlagSource() {
            return new InMemoryFlagSource();
        }

        public ProvisioningTester build() {
            return new ProvisioningTester(Optional.ofNullable(curator).orElseGet(MockCurator::new),
                                          new NodeFlavors(Optional.ofNullable(flavorsConfig).orElseGet(ProvisioningTester::createConfig)),
                                          resourcesCalculator,
                                          Optional.ofNullable(zone).orElseGet(Zone::defaultZone),
                                          Optional.ofNullable(nameResolver).orElseGet(() -> new MockNameResolver().mockAnyLookup()),
                                          defaultImage,
                                          Optional.ofNullable(orchestrator).orElseGet(OrchestratorMock::new),
                                          hostProvisioner,
                                          new LoadBalancerServiceMock(),
                                          Optional.ofNullable(flagSource).orElse(defaultFlagSource()),
                                          spareCount);
        }

        private static FlavorsConfig asConfig(List<Flavor> flavors) {
            FlavorsConfig.Builder b = new FlavorsConfig.Builder();
            for (Flavor flavor : flavors)
                b.flavor(asFlavorConfig(flavor.name(), flavor.resources()));
            return b.build();
        }

        private static FlavorsConfig.Flavor.Builder asFlavorConfig(String flavorName, NodeResources resources) {
            FlavorsConfig.Flavor.Builder flavor = new FlavorsConfig.Flavor.Builder();
            flavor.name(flavorName);
            flavor.minCpuCores(resources.vcpu());
            flavor.minMainMemoryAvailableGb(resources.memoryGb());
            flavor.minDiskAvailableGb(resources.diskGb());
            flavor.bandwidth(resources.bandwidthGbps() * 1000);
            flavor.fastDisk(resources.diskSpeed().compatibleWith(NodeResources.DiskSpeed.fast));
            flavor.remoteStorage(resources.storageType().compatibleWith(NodeResources.StorageType.remote));
            flavor.architecture(resources.architecture().toString());
            flavor.gpuCount(resources.gpuResources().count());
            flavor.gpuMemoryGb(resources.gpuResources().memoryGb());
            return flavor;
        }

    }

    private static class NullProvisionLogger implements ProvisionLogger {
        @Override public void log(Level level, String message) { }
    }

    static class MockResourcesCalculator implements HostResourcesCalculator {

        private final int memoryTaxGb;
        private final int localDiskTax;

        public MockResourcesCalculator(int memoryTaxGb, int localDiskTax) {
            this.memoryTaxGb = memoryTaxGb;
            this.localDiskTax = localDiskTax;
        }

        @Override
        public NodeResources realResourcesOf(Nodelike node, NodeRepository nodeRepository) {
            NodeResources resources = node.resources();
            if (node.type() == NodeType.host) return resources;
            return resources.withMemoryGb(resources.memoryGb() - memoryTaxGb)
                            .withDiskGb(resources.diskGb() - ( resources.storageType() == local ? localDiskTax : 0));
        }

        @Override
        public NodeResources advertisedResourcesOf(Flavor flavor) {
            NodeResources resources = flavor.resources();
            if ( ! flavor.isConfigured()) return resources;
            return resources.withMemoryGb(resources.memoryGb() + memoryTaxGb);
        }

        @Override
        public NodeResources requestToReal(NodeResources resources, boolean exclusive, boolean bestCase) {
            return resources.withMemoryGb(resources.memoryGb() - memoryTaxGb)
                            .withDiskGb(resources.diskGb() - ( resources.storageType() == local ? localDiskTax : 0) );
        }

        @Override
        public NodeResources realToRequest(NodeResources resources, boolean exclusive, boolean bestCase) {
            return resources.withMemoryGb(resources.memoryGb() + memoryTaxGb)
                            .withDiskGb(resources.diskGb() + ( resources.storageType() == local ? localDiskTax : 0) );
        }

        @Override
        public long reservedDiskSpaceInBase2Gb(NodeType nodeType, boolean sharedHost) { return 0; }

    }

}
