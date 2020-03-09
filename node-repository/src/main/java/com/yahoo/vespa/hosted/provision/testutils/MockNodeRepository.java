// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.DiskSpeed.slow;
import static com.yahoo.config.provision.NodeResources.StorageType.local;
import static com.yahoo.config.provision.NodeResources.StorageType.remote;

/**
 * A mock repository prepopulated with some applications.
 * Instantiated by DI.
 */
public class MockNodeRepository extends NodeRepository {

    private final NodeFlavors flavors;

    /**
     * Constructor
     * @param flavors flavors to have in node repo
     */
    public MockNodeRepository(MockCurator curator, NodeFlavors flavors) {
        super(flavors, curator, Clock.fixed(Instant.ofEpochMilli(123), ZoneId.of("Z")), Zone.defaultZone(),
              new MockNameResolver().mockAnyLookup(),
              DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa"),
              true, new InMemoryFlagSource());
        this.flavors = flavors;

        curator.setZooKeeperEnsembleConnectionSpec("cfg1:1234,cfg2:1234,cfg3:1234");
        populate();
    }

    private void populate() {
        NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(this,
                                                                              Zone.defaultZone(),
                                                                              new MockProvisionServiceProvider(),
                                                                              new InMemoryFlagSource());
        List<Node> nodes = new ArrayList<>();

        // Regular nodes
        nodes.add(createNode("node1", "host1.yahoo.com", ipConfig(1), Optional.empty(),
                             new Flavor(new NodeResources(2, 8, 50, 1, fast, local)), Optional.empty(), NodeType.tenant));
        nodes.add(createNode("node2", "host2.yahoo.com", ipConfig(2), Optional.empty(),
                             new Flavor(new NodeResources(2, 8, 50, 1, fast, local)), Optional.empty(), NodeType.tenant));
        nodes.add(createNode("node3", "host3.yahoo.com", ipConfig(3), Optional.empty(),
                             new Flavor(new NodeResources(0.5, 48, 500, 1, fast, local)), Optional.empty(), NodeType.tenant));
        Node node4 = createNode("node4", "host4.yahoo.com", ipConfig(4), Optional.of("dockerhost1.yahoo.com"),
                                new Flavor(new NodeResources(1, 4, 100, 1, fast, local)), Optional.empty(), NodeType.tenant);
        node4 = node4.with(node4.status()
                                .withVespaVersion(new Version("6.41.0"))
                                .withDockerImage(DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa:6.41.0")));
        nodes.add(node4);

        Node node5 = createNode("node5", "host5.yahoo.com", ipConfig(5), Optional.of("dockerhost2.yahoo.com"),
                                new Flavor(new NodeResources(1, 8, 100, 1, slow, remote)), Optional.empty(), NodeType.tenant);
        nodes.add(node5.with(node5.status()
                                  .withVespaVersion(new Version("1.2.3"))
                                  .withDockerImage(DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa:1.2.3"))));


        nodes.add(createNode("node6", "host6.yahoo.com", ipConfig(6), Optional.empty(),
                             new Flavor(new NodeResources(2, 8, 50, 1, fast, local)), Optional.empty(), NodeType.tenant));
        Node node7 = createNode("node7", "host7.yahoo.com", ipConfig(7), Optional.empty(),
                                new Flavor(new NodeResources(2, 8, 50, 1, fast, local)), Optional.empty(), NodeType.tenant);
        nodes.add(node7);

        // 8, 9, 11 and 12 are added by web service calls
        Node node10 = createNode("node10", "host10.yahoo.com", ipConfig(10), Optional.of("parent1.yahoo.com"),
                                 new Flavor(new NodeResources(2, 8, 50, 1, fast, local)), Optional.empty(), NodeType.tenant);
        Status node10newStatus = node10.status();
        node10newStatus = node10newStatus
                .withVespaVersion(Version.fromString("5.104.142"))
                .withDockerImage(DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa:5.104.142"));
        node10 = node10.with(node10newStatus);
        nodes.add(node10);

        Node node55 = createNode("node55", "host55.yahoo.com", ipConfig(55), Optional.empty(),
                                 new Flavor(new NodeResources(2, 8, 50, 1, fast, local)), Optional.empty(), NodeType.tenant);
        nodes.add(node55.with(node55.status().withWantToRetire(true).withWantToDeprovision(true)));

        /* Setup docker hosts (two of these will be reserved for spares */
        nodes.add(createNode("dockerhost1", "dockerhost1.yahoo.com", ipConfig(100, 1, 3), Optional.empty(),
                             flavors.getFlavorOrThrow("large"), Optional.empty(), NodeType.host));
        nodes.add(createNode("dockerhost2", "dockerhost2.yahoo.com", ipConfig(101, 1, 3), Optional.empty(),
                             flavors.getFlavorOrThrow("large"), Optional.empty(), NodeType.host));
        nodes.add(createNode("dockerhost3", "dockerhost3.yahoo.com", ipConfig(102, 1, 3), Optional.empty(),
                             flavors.getFlavorOrThrow("large"), Optional.empty(), NodeType.host));
        nodes.add(createNode("dockerhost4", "dockerhost4.yahoo.com", ipConfig(103, 1, 3), Optional.empty(),
                             flavors.getFlavorOrThrow("large"), Optional.empty(), NodeType.host));
        nodes.add(createNode("dockerhost5", "dockerhost5.yahoo.com", ipConfig(104, 1, 3), Optional.empty(),
                             flavors.getFlavorOrThrow("large"), Optional.empty(), NodeType.host));

        // Config servers
        nodes.add(createNode("cfg1", "cfg1.yahoo.com", ipConfig(201), Optional.empty(),
                             flavors.getFlavorOrThrow("default"), Optional.empty(), NodeType.config));
        nodes.add(createNode("cfg2", "cfg2.yahoo.com", ipConfig(202), Optional.empty(),
                             flavors.getFlavorOrThrow("default"), Optional.empty(), NodeType.config));

        // Ready all nodes, except 7 and 55
        nodes = addNodes(nodes, Agent.system);
        nodes.remove(node7);
        nodes.remove(node55);
        nodes = setDirty(nodes, Agent.system, getClass().getSimpleName());
        setReady(nodes, Agent.system, getClass().getSimpleName());

        fail(node5.hostname(), Agent.system, getClass().getSimpleName());
        dirtyRecursively(node55.hostname(), Agent.system, getClass().getSimpleName());

        ApplicationId zoneApp = ApplicationId.from(TenantName.from("zoneapp"), ApplicationName.from("zoneapp"), InstanceName.from("zoneapp"));
        ClusterSpec zoneCluster = ClusterSpec.request(ClusterSpec.Type.container,
                                                      ClusterSpec.Id.from("node-admin"),
                                                      Version.fromString("6.42"),
                                                      false, Optional.empty());
        activate(provisioner.prepare(zoneApp, zoneCluster, Capacity.fromRequiredNodeType(NodeType.host), 1, null), zoneApp, provisioner);

        ApplicationId app1 = ApplicationId.from(TenantName.from("tenant1"), ApplicationName.from("application1"), InstanceName.from("instance1"));
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.container,
                                                   ClusterSpec.Id.from("id1"),
                                                   Version.fromString("6.42"),
                                                   false, Optional.empty());
        provisioner.prepare(app1, cluster1, Capacity.fromCount(2, new NodeResources(2, 8, 50, 1)), 1, null);

        ApplicationId app2 = ApplicationId.from(TenantName.from("tenant2"), ApplicationName.from("application2"), InstanceName.from("instance2"));
        ClusterSpec cluster2 = ClusterSpec.request(ClusterSpec.Type.content,
                                                   ClusterSpec.Id.from("id2"),
                                                   Version.fromString("6.42"),
                                                   false, Optional.empty());
        activate(provisioner.prepare(app2, cluster2, Capacity.fromCount(2, new NodeResources(2, 8, 50, 1)), 1, null), app2, provisioner);

        ApplicationId app3 = ApplicationId.from(TenantName.from("tenant3"), ApplicationName.from("application3"), InstanceName.from("instance3"));
        ClusterSpec cluster3 = ClusterSpec.request(ClusterSpec.Type.content,
                                                   ClusterSpec.Id.from("id3"),
                                                   Version.fromString("6.42"),
                                                   false, Optional.empty());
        activate(provisioner.prepare(app3, cluster3, Capacity.fromCount(2, new NodeResources(1, 4, 100, 1), false, true), 1, null), app3, provisioner);

        List<Node> largeNodes = new ArrayList<>();
        largeNodes.add(createNode("node13", "host13.yahoo.com", ipConfig(13), Optional.empty(),
                                  new Flavor(new NodeResources(10, 48, 500, 1, fast, local)), Optional.empty(), NodeType.tenant));
        largeNodes.add(createNode("node14", "host14.yahoo.com", ipConfig(14), Optional.empty(),
                                  new Flavor(new NodeResources(10, 48, 500, 1, fast, local)), Optional.empty(), NodeType.tenant));
        addNodes(largeNodes, Agent.system);
        setReady(largeNodes, Agent.system, getClass().getSimpleName());
        ApplicationId app4 = ApplicationId.from(TenantName.from("tenant4"), ApplicationName.from("application4"), InstanceName.from("instance4"));
        ClusterSpec cluster4 = ClusterSpec.request(ClusterSpec.Type.container,
                                                   ClusterSpec.Id.from("id4"),
                                                   Version.fromString("6.42"),
                                                   false, Optional.empty());
        activate(provisioner.prepare(app4, cluster4, Capacity.fromCount(2, new NodeResources(10, 48, 500, 1), false, true), 1, null), app4, provisioner);
    }

    private void activate(List<HostSpec> hosts, ApplicationId application, NodeRepositoryProvisioner provisioner) {
        NestedTransaction transaction = new NestedTransaction();
        provisioner.activate(transaction, application, hosts);
        transaction.commit();
    }

    private void addRecord(String name, String ipAddress) {
        var nameResolver = (MockNameResolver) nameResolver();
        nameResolver.addRecord(name, ipAddress);
        nameResolver.addReverseRecord(ipAddress, name);
    }

    private IP.Config ipConfig(int nodeIndex, int primarySize, int poolSize) {
        var primary = new LinkedHashSet<String>();
        var pool = new LinkedHashSet<String>();
        for (int i = 1; i <= primarySize + poolSize; i++) {
            var set = primary;
            if (i > primarySize) {
                set = pool;
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
        return new IP.Config(primary, pool);
    }

    private IP.Config ipConfig(int nodeIndex) {
        return ipConfig(nodeIndex, 1, 0);
    }

}
