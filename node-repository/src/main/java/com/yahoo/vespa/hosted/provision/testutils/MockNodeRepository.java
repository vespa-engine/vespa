// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.config.provision.ClusterInfo;
import com.yahoo.config.provision.IntRange;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ActivationContext;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.WireguardKey;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provision.ZoneEndpoint;
import com.yahoo.config.provision.ZoneEndpoint.AccessType;
import com.yahoo.config.provision.ZoneEndpoint.AllowedUrn;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaling;
import com.yahoo.vespa.hosted.provision.autoscale.Load;
import com.yahoo.vespa.hosted.provision.autoscale.MemoryMetricsDb;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.provisioning.EmptyProvisionServiceProvider;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static com.yahoo.config.provision.NodeResources.Architecture;
import static com.yahoo.config.provision.NodeResources.Architecture.x86_64;
import static com.yahoo.config.provision.NodeResources.DiskSpeed;
import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.DiskSpeed.slow;
import static com.yahoo.config.provision.NodeResources.StorageType;
import static com.yahoo.config.provision.NodeResources.StorageType.local;
import static com.yahoo.config.provision.NodeResources.StorageType.remote;

/**
 * A mock repository prepopulated with some applications.
 * Instantiated by DI.
 */
public class MockNodeRepository extends NodeRepository {

    public static final CloudAccount tenantAccount = CloudAccount.from("777888999000");

    private final NodeFlavors flavors;
    private final CloudAccount defaultCloudAccount;

    /**
     * Constructor
     *
     * @param flavors flavors to have in node repo
     */
    @Inject
    public MockNodeRepository(MockCurator curator, NodeFlavors flavors, Zone zone) {
        super(flavors,
              new EmptyProvisionServiceProvider(),
              curator,
              Clock.fixed(Instant.ofEpochMilli(123), ZoneId.of("Z")),
              zone,
              new MockNameResolver().mockAnyLookup(),
              DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa"),
              Optional.empty(),
              Optional.empty(),
              new InMemoryFlagSource(),
              new MemoryMetricsDb(Clock.fixed(Instant.ofEpochMilli(123), ZoneId.of("Z"))),
              new OrchestratorMock(),
              true,
              0, 1000);
        this.flavors = flavors;
        defaultCloudAccount = zone.cloud().account();

        curator.setZooKeeperEnsembleConnectionSpec("cfg1:1234,cfg2:1234,cfg3:1234");
        populate();
    }

    private void populate() {
        NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(this, Zone.defaultZone(), new MockProvisionServiceProvider());
        List<Node> nodes = new ArrayList<>();

        // Regular nodes
        nodes.add(Node.create("node1", ipConfig(1), "host1.yahoo.com", resources(2, 8, 50, 1, fast, local), NodeType.tenant)
                          .cloudAccount(defaultCloudAccount).build());
        nodes.add(Node.create("node2", ipConfig(2), "host2.yahoo.com", resources(2, 8, 50, 1, fast, local), NodeType.tenant)
                          .cloudAccount(defaultCloudAccount).build());
        // Emulate node in tenant account
        nodes.add(Node.create("node3", ipConfig(3), "host3.yahoo.com", resources(0.5, 48, 500, 1, fast, local), NodeType.tenant)
                          .cloudAccount(tenantAccount).build());
        Node node4 = Node.create("node4", ipConfig(4), "host4.yahoo.com", resources(1, 4, 100, 1, fast, local), NodeType.tenant)
                .parentHostname("dockerhost1.yahoo.com")
                .status(Status.initial()
                        .withVespaVersion(new Version("6.41.0"))
                        .withContainerImage(DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa:6.41.0")))
                .cloudAccount(defaultCloudAccount)
                .build();
        nodes.add(node4);

        Node node5 = Node.create("node5", ipConfig(5), "host5.yahoo.com", resources(1, 8, 100, 1, slow, remote), NodeType.tenant)
                .parentHostname("dockerhost2.yahoo.com")
                .status(Status.initial()
                        .withVespaVersion(new Version("1.2.3"))
                        .withContainerImage(DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa:1.2.3")))
                .cloudAccount(defaultCloudAccount)
                .build();
        nodes.add(node5);


        nodes.add(Node.create("node6", ipConfig(6), "host6.yahoo.com", resources(2, 8, 50, 1, fast, local), NodeType.tenant)
                              .cloudAccount(defaultCloudAccount).build());
        Node node7 = Node.create("node7", ipConfig(7), "host7.yahoo.com", resources(2, 8, 50, 1, fast, local), NodeType.tenant)
                                  .cloudAccount(defaultCloudAccount).build();
        nodes.add(node7);

        // 8, 9, 11 and 12 are added by web service calls
        Node node10 = Node.create("node10", ipConfig(10), "host10.yahoo.com", resources(2, 8, 50, 1, fast, local), NodeType.tenant)
                .parentHostname("parent1.yahoo.com")
                .status(Status.initial()
                        .withVespaVersion(Version.fromString("5.104.142"))
                        .withContainerImage(DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa:5.104.142")))
                .cloudAccount(defaultCloudAccount)
                .build();
        nodes.add(node10);

        Node node55 = Node.create("node55", ipConfig(55), "host55.yahoo.com", resources(2, 8, 50, 1, fast, local), NodeType.tenant)
                          .status(Status.initial().withWantToRetire(true, true, false, false))
                .cloudAccount(defaultCloudAccount).build();
        nodes.add(node55);

        /* Setup docker hosts (two of these will be reserved for spares */
        nodes.add(Node.create("dockerhost1", ipConfig(100, 1, 3), "dockerhost1.yahoo.com",
                             flavors.getFlavorOrThrow("large"), NodeType.host).cloudAccount(defaultCloudAccount).build());
        // Emulate host in tenant account
        nodes.add(Node.create("dockerhost2", ipConfig(101, 1, 3), "dockerhost2.yahoo.com",
                              flavors.getFlavorOrThrow("large"), NodeType.host)
                          .wireguardPubKey(WireguardKey.from("000011112222333344445555666677778888999900c="))
                          .cloudAccount(tenantAccount).build());
        nodes.add(Node.create("dockerhost3", ipConfig(102, 1, 3), "dockerhost3.yahoo.com",
                             flavors.getFlavorOrThrow("large"), NodeType.host).cloudAccount(defaultCloudAccount).build());
        nodes.add(Node.create("dockerhost4", ipConfig(103, 1, 3), "dockerhost4.yahoo.com",
                             flavors.getFlavorOrThrow("large"), NodeType.host).cloudAccount(defaultCloudAccount).build());
        nodes.add(Node.create("dockerhost5", ipConfig(104, 1, 3), "dockerhost5.yahoo.com",
                             flavors.getFlavorOrThrow("large"), NodeType.host).cloudAccount(defaultCloudAccount).build());
        nodes.add(Node.create("dockerhost6", ipConfig(105, 1, 3), "dockerhost6.yahoo.com",
                flavors.getFlavorOrThrow("arm64"), NodeType.host).cloudAccount(defaultCloudAccount).build());

        // Config servers
        nodes.add(Node.create("cfg1", ipConfig(201), "cfg1.yahoo.com", flavors.getFlavorOrThrow("default"), NodeType.config)
                          .wireguardPubKey(WireguardKey.from("lololololololololololololololololololololoo=")).build());
        nodes.add(Node.create("cfg2", ipConfig(202), "cfg2.yahoo.com", flavors.getFlavorOrThrow("default"), NodeType.config)
                          .build());

        // Ready all nodes, except 7 and 55
        nodes = nodes().addNodes(nodes, Agent.system);
        nodes.remove(node7);
        nodes.remove(node55);
        nodes = nodes().deallocate(nodes, Agent.system, getClass().getSimpleName());
        nodes.forEach(node -> nodes().setReady(new NodeMutex(node, () -> {}), Agent.system, getClass().getSimpleName()));

        nodes().fail(node5.hostname(), Agent.system, getClass().getSimpleName());
        nodes().deallocateRecursively(node55.hostname(), Agent.system, getClass().getSimpleName());

        nodes().fail("dockerhost6.yahoo.com", Agent.operator, getClass().getSimpleName());
        nodes().removeRecursively("dockerhost6.yahoo.com");

        // Activate config servers
        ApplicationId cfgApp = ApplicationId.from("cfg", "cfg", "cfg");
        ClusterSpec cfgCluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("configservers")).vespaVersion("6.42").build();
        activate(provisioner.prepare(cfgApp, cfgCluster, Capacity.fromRequiredNodeType(NodeType.config), null), cfgApp, provisioner);

        ApplicationId zoneApp = ApplicationId.from(TenantName.from("zoneapp"), ApplicationName.from("zoneapp"), InstanceName.from("zoneapp"));
        ClusterSpec zoneCluster = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("node-admin")).vespaVersion("6.42").build();
        activate(provisioner.prepare(zoneApp, zoneCluster, Capacity.fromRequiredNodeType(NodeType.host), null), zoneApp, provisioner);

        ApplicationId app1Id = ApplicationId.from(TenantName.from("tenant1"), ApplicationName.from("application1"), InstanceName.from("instance1"));
        ClusterSpec cluster1Id = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("id1"))
                                            .vespaVersion("6.42")
                                            .loadBalancerSettings(new ZoneEndpoint(false, true, List.of(new AllowedUrn(AccessType.awsPrivateLink, "arne"))))
                                            .build();
        activate(provisioner.prepare(app1Id,
                                     cluster1Id,
                                     Capacity.from(new ClusterResources(2, 1, new NodeResources(2, 8, 50, 1)),
                                                   new ClusterResources(8, 2, new NodeResources(4, 16, 1000, 1)),
                                                   IntRange.empty(),
                                                   false,
                                                   true,
                                                   Optional.empty(),
                                                   ClusterInfo.empty()),
                                null), app1Id, provisioner);
        Application app1 = applications().get(app1Id).get();
        Cluster cluster1 = app1.cluster(cluster1Id.id()).get();
        cluster1 = cluster1.withSuggested(new Autoscaling(Autoscaling.Status.unavailable,
                                                          "",
                                                          Optional.of(new ClusterResources(6, 2,
                                                                                           new NodeResources(3, 20, 100, 1))),
                                                          clock().instant(),
                                                          Load.zero(),
                                                          Load.zero(),
                                                          Autoscaling.Metrics.zero()));
        cluster1 = cluster1.withTarget(new Autoscaling(Autoscaling.Status.unavailable,
                                                       "",
                                                       Optional.of(new ClusterResources(4, 1,
                                                                                        new NodeResources(3, 16, 100, 1))),
                                                       clock().instant(),
                                                       new Load(0.1, 0.2, 0.3),
                                                       new Load(0.4, 0.5, 0.6),
                                                       new Autoscaling.Metrics(0.7, 0.8, 0.9)));
        try (Mutex lock = applications().lock(app1Id)) {
            applications().put(app1.with(cluster1), lock);
        }

        ApplicationId app2 = ApplicationId.from(TenantName.from("tenant2"), ApplicationName.from("application2"), InstanceName.from("instance2"));
        ClusterSpec cluster2 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("id2")).vespaVersion("6.42").build();
        activate(provisioner.prepare(app2, cluster2, Capacity.from(new ClusterResources(2, 1, new NodeResources(2, 8, 50, 1))), null), app2, provisioner);

        ApplicationId app3 = ApplicationId.from(TenantName.from("tenant3"), ApplicationName.from("application3"), InstanceName.from("instance3"));
        ClusterSpec cluster3 = ClusterSpec.request(ClusterSpec.Type.content, ClusterSpec.Id.from("id3")).vespaVersion("6.42").build();
        activate(provisioner.prepare(app3, cluster3, Capacity.from(new ClusterResources(2, 1, new NodeResources(1, 4, 100, 1)), false, true), null), app3, provisioner);

        List<Node> largeNodes = new ArrayList<>();
        largeNodes.add(Node.create("node13", ipConfig(13), "host13.yahoo.com", resources(10, 48, 500, 1, fast, local), NodeType.tenant).build());
        largeNodes.add(Node.create("node14", ipConfig(14), "host14.yahoo.com", resources(10, 48, 500, 1, fast, local), NodeType.tenant).build());
        nodes().addNodes(largeNodes, Agent.system);
        largeNodes.forEach(node -> nodes().setReady(new NodeMutex(node, () -> {}), Agent.system, getClass().getSimpleName()));
        ApplicationId app4 = ApplicationId.from(TenantName.from("tenant4"), ApplicationName.from("application4"), InstanceName.from("instance4"));
        ClusterSpec cluster4 = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from("id4")).vespaVersion("6.42").build();
        activate(provisioner.prepare(app4, cluster4, Capacity.from(new ClusterResources(2, 1, new NodeResources(10, 48, 500, 1)), false, true), null), app4, provisioner);
    }

    private void activate(List<HostSpec> hosts, ApplicationId application, NodeRepositoryProvisioner provisioner) {
        try (var lock = provisioner.lock(application)) {
            NestedTransaction transaction = new NestedTransaction();
            provisioner.activate(hosts, new ActivationContext(0), new ApplicationTransaction(lock, transaction));
            transaction.commit();
        }
    }

    private void addRecord(String name, String ipAddress) {
        var nameResolver = (MockNameResolver) nameResolver();
        nameResolver.addRecord(name, ipAddress);
        nameResolver.addReverseRecord(ipAddress, name);
    }

    private IP.Config ipConfig(int nodeIndex, int primarySize, int poolSize) {
        var primary = new LinkedHashSet<String>();
        var ipPool = new LinkedHashSet<String>();
        for (int i = 1; i <= primarySize + poolSize; i++) {
            var set = primary;
            if (i > primarySize) {
                set = ipPool;
            }
            var rootName = "test-node-primary";
            if (i > primarySize) {
                rootName = "test-node-pool";
            }
            var name = rootName + "-" + nodeIndex + "-" + i;
            var ipv6Address = "::" + nodeIndex + ":" + i;
            addRecord(name, ipv6Address);
            set.add(ipv6Address);
            if (i <= primarySize) {
                var ipv4Address = "127.0." + nodeIndex + "." + i;
                addRecord(name, ipv4Address);
                set.add(ipv4Address);
            }
        }
        return IP.Config.of(primary, ipPool, List.of());
    }

    private IP.Config ipConfig(int nodeIndex) {
        return ipConfig(nodeIndex, 1, 0);
    }

    private static Flavor resources(double vcpu, double memoryGb, double diskGb, double bandwidth, DiskSpeed diskSpeed, StorageType storageType) {
        return resources(vcpu, memoryGb, diskGb, bandwidth, diskSpeed, storageType, x86_64);
    }

    private static Flavor resources(double vcpu, double memoryGb, double diskGb, double bandwidth, DiskSpeed diskSpeed,
                                    StorageType storageType, Architecture architecture) {
        return new Flavor(new NodeResources(vcpu, memoryGb, diskGb, bandwidth, diskSpeed, storageType, architecture));
    }

}
