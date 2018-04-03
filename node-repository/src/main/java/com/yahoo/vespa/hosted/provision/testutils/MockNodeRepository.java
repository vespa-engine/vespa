// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.provisioning.NodeRepositoryProvisioner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * A mock repository prepopulated with some applications.
 * Instantiated by DI from application package above.
 */
public class MockNodeRepository extends NodeRepository {

    private final NodeFlavors flavors;

    /**
     * Constructor
     * @param flavors flavors to have in node repo
     */
    public MockNodeRepository(MockCurator curator, NodeFlavors flavors) {
        super(flavors, curator, Clock.fixed(Instant.ofEpochMilli(123), ZoneId.of("Z")), Zone.defaultZone(),
              new MockNameResolver()
                      .addRecord("test-container-1", "::2")
                      .mockAnyLookup(),
              new DockerImage("docker-registry.domain.tld:8080/dist/vespa"),
              true);
        this.flavors = flavors;

        curator.setZooKeeperEnsembleConnectionSpec("cfg1:1234,cfg2:1234,cfg3:1234");
        populate();
    }

    private void populate() {
        NodeRepositoryProvisioner provisioner = new NodeRepositoryProvisioner(this, flavors, Zone.defaultZone());

        List<Node> nodes = new ArrayList<>();

        final List<String> ipAddressesForAllHost = Arrays.asList("127.0.0.1", "::1");
        Collections.sort(ipAddressesForAllHost);
        final HashSet<String> ipAddresses = new HashSet<>(ipAddressesForAllHost);

        final List<String> additionalIpAddressesForAllHost = Arrays.asList("::2", "::3", "::4");
        Collections.sort(additionalIpAddressesForAllHost);
        final HashSet<String> additionalIpAddresses = new HashSet<>(additionalIpAddressesForAllHost);

        nodes.add(createNode("node1", "host1.yahoo.com", ipAddresses, Optional.empty(), flavors.getFlavorOrThrow("default"), NodeType.tenant));
        nodes.add(createNode("node2", "host2.yahoo.com", ipAddresses, Optional.empty(), flavors.getFlavorOrThrow("default"), NodeType.tenant));
        nodes.add(createNode("node3", "host3.yahoo.com", ipAddresses, Optional.empty(), flavors.getFlavorOrThrow("expensive"), NodeType.tenant));

        Node node4 = createNode("node4", "host4.yahoo.com", ipAddresses, Optional.of("dockerhost1.yahoo.com"), flavors.getFlavorOrThrow("docker"), NodeType.tenant);
        node4 = node4.with(node4.status().withVespaVersion(new Version("6.41.0")));
        nodes.add(node4);

        Node node5 = createNode("node5", "host5.yahoo.com", ipAddresses, Optional.of("dockerhost2.yahoo.com"), flavors.getFlavorOrThrow("docker"), NodeType.tenant);
        nodes.add(node5.with(node5.status().withVespaVersion(new Version("1.2.3"))));


        nodes.add(createNode("node6", "host6.yahoo.com", ipAddresses, Optional.empty(), flavors.getFlavorOrThrow("default"), NodeType.tenant));
        nodes.add(createNode("node7", "host7.yahoo.com", ipAddresses, Optional.empty(), flavors.getFlavorOrThrow("default"), NodeType.tenant));
        // 8 and 9 are added by web service calls
        Node node10 = createNode("node10", "host10.yahoo.com", ipAddresses, Optional.of("parent1.yahoo.com"), flavors.getFlavorOrThrow("default"), NodeType.tenant);
        Status node10newStatus = node10.status();
        node10newStatus = node10newStatus
                .withVespaVersion(Version.fromString("5.104.142"));
        node10 = node10.with(node10newStatus);
        nodes.add(node10);

        Node node55 = createNode("node55", "host55.yahoo.com", ipAddresses, Optional.empty(), flavors.getFlavorOrThrow("default"), NodeType.tenant);
        nodes.add(node55.with(node55.status().withWantToRetire(true).withWantToDeprovision(true)));

        /* Setup docker hosts (two of these will be reserved for spares */
        nodes.add(createNode("dockerhost1", "dockerhost1.yahoo.com", ipAddresses, additionalIpAddresses, Optional.empty(), flavors.getFlavorOrThrow("large"), NodeType.host));
        nodes.add(createNode("dockerhost2", "dockerhost2.yahoo.com", ipAddresses, additionalIpAddresses, Optional.empty(), flavors.getFlavorOrThrow("large"), NodeType.host));
        nodes.add(createNode("dockerhost3", "dockerhost3.yahoo.com", ipAddresses, additionalIpAddresses, Optional.empty(), flavors.getFlavorOrThrow("large"), NodeType.host));
        nodes.add(createNode("dockerhost4", "dockerhost4.yahoo.com", ipAddresses, additionalIpAddresses, Optional.empty(), flavors.getFlavorOrThrow("large"), NodeType.host));
        nodes.add(createNode("dockerhost5", "dockerhost5.yahoo.com", ipAddresses, additionalIpAddresses, Optional.empty(), flavors.getFlavorOrThrow("large"), NodeType.host));

        nodes = addNodes(nodes);
        nodes.remove(6);
        nodes.remove(7);
        nodes = setDirty(nodes, Agent.system, getClass().getSimpleName());
        setReady(nodes, Agent.system, getClass().getSimpleName());

        fail("host5.yahoo.com", Agent.system, getClass().getSimpleName());
        setDirty("host55.yahoo.com", Agent.system, getClass().getSimpleName());

        ApplicationId zoneApp = ApplicationId.from(TenantName.from("zoneapp"), ApplicationName.from("zoneapp"), InstanceName.from("zoneapp"));
        ClusterSpec zoneCluster = ClusterSpec.request(ClusterSpec.Type.container,
                                                      ClusterSpec.Id.from("node-admin"),
                                                      Version.fromString("6.42"),
                                                      false);
        activate(provisioner.prepare(zoneApp, zoneCluster, Capacity.fromRequiredNodeType(NodeType.host), 1, null), zoneApp, provisioner);

        ApplicationId app1 = ApplicationId.from(TenantName.from("tenant1"), ApplicationName.from("application1"), InstanceName.from("instance1"));
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.container,
                                                   ClusterSpec.Id.from("id1"),
                                                   Version.fromString("6.42"),
                                                   false);
        provisioner.prepare(app1, cluster1, Capacity.fromNodeCount(2), 1, null);

        ApplicationId app2 = ApplicationId.from(TenantName.from("tenant2"), ApplicationName.from("application2"), InstanceName.from("instance2"));
        ClusterSpec cluster2 = ClusterSpec.request(ClusterSpec.Type.content,
                                                   ClusterSpec.Id.from("id2"),
                                                   Version.fromString("6.42"),
                                                   false);
        activate(provisioner.prepare(app2, cluster2, Capacity.fromNodeCount(2), 1, null), app2, provisioner);

        ApplicationId app3 = ApplicationId.from(TenantName.from("tenant3"), ApplicationName.from("application3"), InstanceName.from("instance3"));
        ClusterSpec cluster3 = ClusterSpec.request(ClusterSpec.Type.content,
                                                   ClusterSpec.Id.from("id3"),
                                                   Version.fromString("6.42"),
                                                   false);
        activate(provisioner.prepare(app3, cluster3, Capacity.fromNodeCount(2, Optional.of("docker"), false), 1, null), app3, provisioner);
    }

    private void activate(List<HostSpec> hosts, ApplicationId application, NodeRepositoryProvisioner provisioner) {
        NestedTransaction transaction = new NestedTransaction();
        provisioner.activate(transaction, application, hosts);
        transaction.commit();
    }
}
