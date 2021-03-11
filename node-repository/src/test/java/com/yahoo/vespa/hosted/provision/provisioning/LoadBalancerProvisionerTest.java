// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.common.collect.Iterators;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.Real;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author mpolden
 */
public class LoadBalancerProvisionerTest {

    private final ApplicationId app1 = ApplicationId.from("tenant1", "application1", "default");
    private final ApplicationId app2 = ApplicationId.from("tenant2", "application2", "default");
    private final ApplicationId infraApp1 = ApplicationId.from("vespa", "tenant-host", "default");

    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();
    private final ProvisioningTester tester = new ProvisioningTester.Builder().flagSource(flagSource).build();

    @Test
    public void provision_load_balancer() {
        Supplier<List<LoadBalancer>> lbApp1 = () -> tester.nodeRepository().loadBalancers().list(app1).asList();
        Supplier<List<LoadBalancer>> lbApp2 = () -> tester.nodeRepository().loadBalancers().list(app2).asList();
        ClusterSpec.Id containerCluster1 = ClusterSpec.Id.from("qrs1");
        ClusterSpec.Id contentCluster = ClusterSpec.Id.from("content");

        // Provision a load balancer for each application
        var nodes = prepare(app1,
                            clusterRequest(ClusterSpec.Type.container, containerCluster1),
                            clusterRequest(ClusterSpec.Type.content, contentCluster));
        assertEquals(1, lbApp1.get().size());
        assertEquals("Prepare provisions load balancer with reserved nodes", 2, lbApp1.get().get(0).instance().reals().size());
        tester.activate(app1, nodes);
        tester.activate(app2, prepare(app2, clusterRequest(ClusterSpec.Type.container, ClusterSpec.Id.from("qrs"))));
        assertEquals(1, lbApp2.get().size());

        // Reals are configured after activation
        assertEquals(app1, lbApp1.get().get(0).id().application());
        assertEquals(containerCluster1, lbApp1.get().get(0).id().cluster());
        assertEquals(Collections.singleton(4443), lbApp1.get().get(0).instance().ports());
        assertEquals("127.0.0.1", get(lbApp1.get().get(0).instance().reals(), 0).ipAddress());
        assertEquals(4443, get(lbApp1.get().get(0).instance().reals(), 0).port());
        assertEquals("127.0.0.2", get(lbApp1.get().get(0).instance().reals(), 1).ipAddress());
        assertEquals(4443, get(lbApp1.get().get(0).instance().reals(), 1).port());

        // A container is failed
        Supplier<List<Node>> containers = () -> tester.getNodes(app1).container().asList();
        Node toFail = containers.get().get(0);
        tester.nodeRepository().nodes().fail(toFail.hostname(), Agent.system, this.getClass().getSimpleName());

        // Redeploying replaces failed node and removes it from load balancer
        tester.activate(app1, prepare(app1,
                                      clusterRequest(ClusterSpec.Type.container, containerCluster1),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));
        LoadBalancer loadBalancer = tester.nodeRepository().loadBalancers().list(app1).asList().get(0);
        assertEquals(2, loadBalancer.instance().reals().size());
        assertTrue("Failed node is removed", loadBalancer.instance().reals().stream()
                                                         .map(Real::hostname)
                                                         .map(HostName::value)
                                                         .noneMatch(hostname -> hostname.equals(toFail.hostname())));
        assertEquals(containers.get().get(0).hostname(), get(loadBalancer.instance().reals(), 0).hostname().value());
        assertEquals(containers.get().get(1).hostname(), get(loadBalancer.instance().reals(), 1).hostname().value());
        assertSame("State is unchanged", LoadBalancer.State.active, loadBalancer.state());

        // Add another container cluster to first app
        ClusterSpec.Id containerCluster2 = ClusterSpec.Id.from("qrs2");
        tester.activate(app1, prepare(app1,
                                      clusterRequest(ClusterSpec.Type.container, containerCluster1),
                                      clusterRequest(ClusterSpec.Type.container, containerCluster2),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));

        // Load balancer is provisioned for second container cluster
        assertEquals(2, lbApp1.get().size());
        List<HostName> activeContainers = tester.getNodes(app1, Node.State.active)
                                                .container().asList()
                                                .stream()
                                                .map(Node::hostname)
                                                .map(HostName::from)
                                                .sorted()
                                                .collect(Collectors.toList());
        List<HostName> reals = lbApp1.get().stream()
                                            .map(LoadBalancer::instance)
                                            .map(LoadBalancerInstance::reals)
                                            .flatMap(Collection::stream)
                                            .map(Real::hostname)
                                            .sorted()
                                            .collect(Collectors.toList());
        assertEquals(activeContainers, reals);

        // Cluster removal deactivates relevant load balancer
        tester.activate(app1, prepare(app1, clusterRequest(ClusterSpec.Type.container, containerCluster1)));
        assertEquals(2, lbApp1.get().size());
        assertEquals("Deactivated load balancer for cluster " + containerCluster2, LoadBalancer.State.inactive,
                     lbApp1.get().stream()
                           .filter(lb -> lb.id().cluster().equals(containerCluster2))
                           .map(LoadBalancer::state)
                           .findFirst()
                           .get());
        assertEquals("Load balancer for cluster " + containerCluster1 + " remains active", LoadBalancer.State.active,
                     lbApp1.get().stream()
                           .filter(lb -> lb.id().cluster().equals(containerCluster1))
                           .map(LoadBalancer::state)
                           .findFirst()
                           .get());

        // Entire application is removed: Nodes and load balancer are deactivated
        tester.remove(app1);
        dirtyNodesOf(app1);
        assertTrue("No nodes are allocated to " + app1, tester.nodeRepository().nodes().list(Node.State.reserved, Node.State.active).owner(app1).isEmpty());
        assertEquals(2, lbApp1.get().size());
        assertTrue("Deactivated load balancers", lbApp1.get().stream().allMatch(lb -> lb.state() == LoadBalancer.State.inactive));
        assertTrue("Load balancers for " + app2 + " remain active", lbApp2.get().stream().allMatch(lb -> lb.state() == LoadBalancer.State.active));

        // Application is redeployed with one cluster and load balancer is re-activated
        tester.activate(app1, prepare(app1,
                                      clusterRequest(ClusterSpec.Type.container, containerCluster1),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));
        assertSame("Re-activated load balancer for " + containerCluster1, LoadBalancer.State.active,
                   lbApp1.get().stream()
                         .filter(lb -> lb.id().cluster().equals(containerCluster1))
                         .map(LoadBalancer::state)
                         .findFirst()
                         .orElseThrow());

        // Next redeploy does not create a new load balancer instance
        tester.loadBalancerService().throwOnCreate(true);
        tester.activate(app1, prepare(app1,
                                      clusterRequest(ClusterSpec.Type.container, containerCluster1),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));
    }

    @Test
    public void provision_load_balancers_with_dynamic_node_provisioning() {
        NodeResources resources = new NodeResources(1, 4, 10, 0.3);
        tester.makeReadyHosts(2, resources);
        tester.activateTenantHosts();
        var nodes = tester.prepare(app1, clusterRequest(ClusterSpec.Type.container, ClusterSpec.Id.from("qrs")), 2 , 1, resources);
        Supplier<LoadBalancer> lb = () -> tester.nodeRepository().loadBalancers().list(app1).asList().get(0);
        assertTrue("Load balancer provisioned with empty reals", tester.loadBalancerService().instances().get(lb.get().id()).reals().isEmpty());
        assignIps(tester.nodeRepository().nodes().list().owner(app1));
        tester.activate(app1, nodes);
        assertFalse("Load balancer is reconfigured with reals", tester.loadBalancerService().instances().get(lb.get().id()).reals().isEmpty());

        // Application is removed, nodes are deleted and load balancer is deactivated
        tester.remove(app1);
        tester.nodeRepository().database().removeNodes(tester.nodeRepository().nodes().list().nodeType(NodeType.tenant).asList());
        assertTrue("Nodes are deleted", tester.nodeRepository().nodes().list().nodeType(NodeType.tenant).isEmpty());
        assertSame("Load balancer is deactivated", LoadBalancer.State.inactive, lb.get().state());

        // Application is redeployed
        nodes = tester.prepare(app1, clusterRequest(ClusterSpec.Type.container, ClusterSpec.Id.from("qrs")), 2 , 1, resources);
        assertTrue("Load balancer is reconfigured with empty reals", tester.loadBalancerService().instances().get(lb.get().id()).reals().isEmpty());
        assignIps(tester.nodeRepository().nodes().list().owner(app1));
        tester.activate(app1, nodes);
        assertFalse("Load balancer is reconfigured with reals", tester.loadBalancerService().instances().get(lb.get().id()).reals().isEmpty());
    }

    @Test
    public void does_not_provision_load_balancers_for_non_tenant_node_type() {
        tester.activate(infraApp1, prepare(infraApp1, Capacity.fromRequiredNodeType(NodeType.host),
                                           clusterRequest(ClusterSpec.Type.container,
                                                          ClusterSpec.Id.from("tenant-host"))));
        assertTrue("No load balancer provisioned", tester.loadBalancerService().instances().isEmpty());
        assertEquals(List.of(), tester.nodeRepository().loadBalancers().list(infraApp1).asList());
    }

    @Test
    public void does_not_provision_load_balancers_for_non_container_cluster() {
        tester.activate(app1, prepare(app1, clusterRequest(ClusterSpec.Type.content,
                                                           ClusterSpec.Id.from("tenant-host"))));
        assertTrue("No load balancer provisioned", tester.loadBalancerService().instances().isEmpty());
        assertEquals(List.of(), tester.nodeRepository().loadBalancers().list(app1).asList());
    }

    @Test
    public void provision_load_balancer_combined_cluster() {
        Supplier<List<LoadBalancer>> lbs = () -> tester.nodeRepository().loadBalancers().list(app1).asList();
        var combinedId = ClusterSpec.Id.from("container1");
        var nodes = prepare(app1, clusterRequest(ClusterSpec.Type.combined, ClusterSpec.Id.from("content1"), Optional.of(combinedId)));
        assertEquals(1, lbs.get().size());
        assertEquals("Prepare provisions load balancer with reserved nodes", 2, lbs.get().get(0).instance().reals().size());
        tester.activate(app1, nodes);
        assertSame(LoadBalancer.State.active, lbs.get().get(0).state());
        assertEquals(combinedId, lbs.get().get(0).id().cluster());
    }

    @Test
    public void provision_load_balancer_config_server_cluster() {
        ApplicationId configServerApp = ApplicationId.from("hosted-vespa", "zone-config-servers", "default");
        Supplier<List<LoadBalancer>> lbs = () -> tester.nodeRepository().loadBalancers().list(configServerApp).asList();
        var cluster = ClusterSpec.Id.from("zone-config-servers");
        var nodes = prepare(configServerApp, Capacity.fromRequiredNodeType(NodeType.config),
                            clusterRequest(ClusterSpec.Type.admin, cluster));
        assertEquals(1, lbs.get().size());
        assertEquals("Prepare provisions load balancer with reserved nodes", 2, lbs.get().get(0).instance().reals().size());
        tester.activate(configServerApp, nodes);
        assertSame(LoadBalancer.State.active, lbs.get().get(0).state());
        assertEquals(cluster, lbs.get().get(0).id().cluster());
    }

    @Test
    public void provision_load_balancer_controller_cluster() {
        ApplicationId controllerApp = ApplicationId.from("hosted-vespa", "controller", "default");
        Supplier<List<LoadBalancer>> lbs = () -> tester.nodeRepository().loadBalancers().list(controllerApp).asList();
        var cluster = ClusterSpec.Id.from("zone-config-servers");
        var nodes = prepare(controllerApp, Capacity.fromRequiredNodeType(NodeType.controller),
                            clusterRequest(ClusterSpec.Type.container, cluster));
        assertEquals(1, lbs.get().size());
        assertEquals("Prepare provisions load balancer with reserved nodes", 2, lbs.get().get(0).instance().reals().size());
        tester.activate(controllerApp, nodes);
        assertSame(LoadBalancer.State.active, lbs.get().get(0).state());
        assertEquals(cluster, lbs.get().get(0).id().cluster());
    }

    @Test
    public void reject_load_balancers_with_clashing_names() {
        ApplicationId instance1 = ApplicationId.from("t1", "a1", "default");
        ApplicationId instance2 = ApplicationId.from("t1", "a1", "dev");
        ApplicationId instance3 = ApplicationId.from("t1", "a1", "qrs");
        ClusterSpec.Id devCluster = ClusterSpec.Id.from("dev");
        ClusterSpec.Id defaultCluster = ClusterSpec.Id.from("default");

        // instance1 is deployed
        tester.activate(instance1, prepare(instance1, clusterRequest(ClusterSpec.Type.container, devCluster)));

        // instance2 clashes because cluster name matches instance1
        try {
            prepare(instance2, clusterRequest(ClusterSpec.Type.container, defaultCluster));
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {
        }

        // instance2 changes cluster name and does not clash
        tester.activate(instance2, prepare(instance2, clusterRequest(ClusterSpec.Type.container, ClusterSpec.Id.from("qrs"))));

        // instance3 clashes because instance name matches instance2 cluster
        tester.activate(instance3, prepare(instance3, clusterRequest(ClusterSpec.Type.container, defaultCluster)));
    }

    private void dirtyNodesOf(ApplicationId application) {
        tester.nodeRepository().nodes().deallocate(tester.nodeRepository().nodes().list().owner(application).asList(), Agent.system, this.getClass().getSimpleName());
    }

    private Set<HostSpec> prepare(ApplicationId application, ClusterSpec... specs) {
        return prepare(application, Capacity.from(new ClusterResources(2, 1, new NodeResources(1, 4, 10, 0.3)), false, true), specs);
    }

    private Set<HostSpec> prepare(ApplicationId application, Capacity capacity, ClusterSpec... specs) {
        tester.makeReadyNodes(specs.length * 2, new NodeResources(1, 4, 10, 0.3), capacity.type());
        Set<HostSpec> allNodes = new LinkedHashSet<>();
        for (ClusterSpec spec : specs) {
            allNodes.addAll(tester.prepare(application, spec, capacity));
        }
        return allNodes;
    }

    private void assignIps(NodeList nodes) {
        try (var lock = tester.nodeRepository().nodes().lockUnallocated()) {
            for (int i = 0; i < nodes.size(); i++) {
                tester.nodeRepository().nodes().write(nodes.asList().get(i).with(IP.Config.EMPTY.withPrimary(Set.of("127.0.0." + i))), lock);
            }
        }
    }

    private static ClusterSpec clusterRequest(ClusterSpec.Type type, ClusterSpec.Id id) {
        return clusterRequest(type,  id, Optional.empty());
    }

    private static ClusterSpec clusterRequest(ClusterSpec.Type type, ClusterSpec.Id id, Optional<ClusterSpec.Id> combinedId) {
        return ClusterSpec.request(type, id).vespaVersion("6.42").combinedId(combinedId).build();
    }

    private static <T> T get(Set<T> set, int position) {
        return Iterators.get(set.iterator(), position, null);
    }

}
