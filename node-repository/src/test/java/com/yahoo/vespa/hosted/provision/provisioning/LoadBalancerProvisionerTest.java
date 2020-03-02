// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.common.collect.Iterators;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.Real;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import org.junit.Test;

import java.util.ArrayList;
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

/**
 * @author mpolden
 */
public class LoadBalancerProvisionerTest {

    private final ApplicationId app1 = ApplicationId.from("tenant1", "application1", "default");
    private final ApplicationId app2 = ApplicationId.from("tenant2", "application2", "default");
    private final ApplicationId infraApp1 = ApplicationId.from("vespa", "tenant-host", "default");

    private final ProvisioningTester tester = new ProvisioningTester.Builder().build();

    @Test
    public void provision_load_balancer() {
        Supplier<List<LoadBalancer>> lbApp1 = () -> tester.nodeRepository().loadBalancers(app1).asList();
        Supplier<List<LoadBalancer>> lbApp2 = () -> tester.nodeRepository().loadBalancers(app2).asList();
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
        Supplier<List<Node>> containers = () -> tester.getNodes(app1).type(ClusterSpec.Type.container).asList();
        Node toFail = containers.get().get(0);
        tester.nodeRepository().fail(toFail.hostname(), Agent.system, this.getClass().getSimpleName());

        // Redeploying replaces failed node and removes it from load balancer
        tester.activate(app1, prepare(app1,
                                      clusterRequest(ClusterSpec.Type.container, containerCluster1),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));
        LoadBalancer loadBalancer = tester.nodeRepository().loadBalancers(app1).asList().get(0);
        assertEquals(2, loadBalancer.instance().reals().size());
        assertTrue("Failed node is removed", loadBalancer.instance().reals().stream()
                                                         .map(Real::hostname)
                                                         .map(HostName::value)
                                                         .noneMatch(hostname -> hostname.equals(toFail.hostname())));
        assertEquals(containers.get().get(0).hostname(), get(loadBalancer.instance().reals(), 0).hostname().value());
        assertEquals(containers.get().get(1).hostname(), get(loadBalancer.instance().reals(), 1).hostname().value());
        assertSame("State is unchanged", LoadBalancer.State.active, loadBalancer.state());

        // Add another container cluster
        ClusterSpec.Id containerCluster2 = ClusterSpec.Id.from("qrs2");
        tester.activate(app1, prepare(app1,
                                      clusterRequest(ClusterSpec.Type.container, containerCluster1),
                                      clusterRequest(ClusterSpec.Type.container, containerCluster2),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));

        // Load balancer is provisioned for second container cluster
        assertEquals(2, lbApp1.get().size());
        List<HostName> activeContainers = tester.getNodes(app1, Node.State.active)
                                                .type(ClusterSpec.Type.container).asList()
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

        // Application is removed, nodes and load balancer are deactivated
        NestedTransaction removeTransaction = new NestedTransaction();
        tester.provisioner().remove(removeTransaction, app1);
        removeTransaction.commit();
        dirtyNodesOf(app1);
        assertTrue("No nodes are allocated to " + app1, tester.nodeRepository().getNodes(app1, Node.State.reserved, Node.State.active).isEmpty());
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
    }

    @Test
    public void provision_load_balancers_with_dynamic_node_provisioning() {
        var nodes = prepare(app1, Capacity.fromCount(2, new NodeResources(1, 4, 10, 0.3), false, true),
                                           true,
                                           clusterRequest(ClusterSpec.Type.container, ClusterSpec.Id.from("qrs")));
        Supplier<LoadBalancer> lb = () -> tester.nodeRepository().loadBalancers(app1).asList().get(0);
        assertTrue("Load balancer provisioned with empty reals", tester.loadBalancerService().instances().get(lb.get().id()).reals().isEmpty());
        assignIps(tester.nodeRepository().getNodes(app1));
        tester.activate(app1, nodes);
        assertFalse("Load balancer is reconfigured with reals", tester.loadBalancerService().instances().get(lb.get().id()).reals().isEmpty());

        // Application is removed, nodes are deleted and load balancer is deactivated
        NestedTransaction removeTransaction = new NestedTransaction();
        tester.provisioner().remove(removeTransaction, app1);
        removeTransaction.commit();
        tester.nodeRepository().database().removeNodes(tester.nodeRepository().getNodes());
        assertTrue("Nodes are deleted", tester.nodeRepository().getNodes().isEmpty());
        assertSame("Load balancer is deactivated", LoadBalancer.State.inactive, lb.get().state());

        // Application is redeployed
        nodes = prepare(app1, Capacity.fromCount(2, new NodeResources(1, 4, 10, 0.3), false, true),
                        true,
                        clusterRequest(ClusterSpec.Type.container, ClusterSpec.Id.from("qrs")));
        assertTrue("Load balancer is reconfigured with empty reals", tester.loadBalancerService().instances().get(lb.get().id()).reals().isEmpty());
        assignIps(tester.nodeRepository().getNodes(app1));
        tester.activate(app1, nodes);
        assertFalse("Load balancer is reconfigured with reals", tester.loadBalancerService().instances().get(lb.get().id()).reals().isEmpty());
    }

    @Test
    public void does_not_provision_load_balancers_for_non_tenant_node_type() {
        tester.activate(infraApp1, prepare(infraApp1, Capacity.fromRequiredNodeType(NodeType.host),
                                           false,
                                           clusterRequest(ClusterSpec.Type.container,
                                                          ClusterSpec.Id.from("tenant-host"))));
        assertTrue("No load balancer provisioned", tester.loadBalancerService().instances().isEmpty());
        assertEquals(List.of(), tester.nodeRepository().loadBalancers(infraApp1).asList());
    }

    @Test
    public void does_not_provision_load_balancers_for_non_container_cluster() {
        tester.activate(app1, prepare(app1, clusterRequest(ClusterSpec.Type.content,
                                                           ClusterSpec.Id.from("tenant-host"))));
        assertTrue("No load balancer provisioned", tester.loadBalancerService().instances().isEmpty());
        assertEquals(List.of(), tester.nodeRepository().loadBalancers(app1).asList());
    }

    @Test
    public void provision_load_balancer_combined_cluster() {
        Supplier<List<LoadBalancer>> lbs = () -> tester.nodeRepository().loadBalancers(app1).asList();
        ClusterSpec.Id cluster = ClusterSpec.Id.from("foo");

        var nodes = prepare(app1, clusterRequest(ClusterSpec.Type.combined, cluster));
        assertEquals(1, lbs.get().size());
        assertEquals("Prepare provisions load balancer with reserved nodes", 2, lbs.get().get(0).instance().reals().size());
        tester.activate(app1, nodes);
        assertSame(LoadBalancer.State.active, lbs.get().get(0).state());
    }

    private void dirtyNodesOf(ApplicationId application) {
        tester.nodeRepository().setDirty(tester.nodeRepository().getNodes(application), Agent.system, this.getClass().getSimpleName());
    }

    private Set<HostSpec> prepare(ApplicationId application, ClusterSpec... specs) {
        return prepare(application, Capacity.fromCount(2, new NodeResources(1, 4, 10, 0.3), false, true), false, specs);
    }

    private Set<HostSpec> prepare(ApplicationId application, Capacity capacity, boolean dynamicDockerNodes, ClusterSpec... specs) {
        if (dynamicDockerNodes) {
            makeDynamicDockerNodes(specs.length * 2, capacity.type());
        } else {
            tester.makeReadyNodes(specs.length * 2, new NodeResources(1, 4, 10, 0.3), capacity.type());
        }
        Set<HostSpec> allNodes = new LinkedHashSet<>();
        for (ClusterSpec spec : specs) {
            allNodes.addAll(tester.prepare(application, spec, capacity, 1, false));
        }
        return allNodes;
    }

    private void makeDynamicDockerNodes(int n, NodeType nodeType) {
        List<Node> nodes = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            var node = Node.createDockerNode(Set.of(), "node" + i, "parent" + i,
                                             new NodeResources(1, 4, 10, 0.3),
                                             nodeType);
            nodes.add(node);
        }
        nodes = tester.nodeRepository().database().addNodesInState(nodes, Node.State.reserved);
        nodes = tester.nodeRepository().setDirty(nodes, Agent.system, getClass().getSimpleName());
        tester.nodeRepository().setReady(nodes, Agent.system, getClass().getSimpleName());
    }

    private void assignIps(List<Node> nodes) {
        try (var lock = tester.nodeRepository().lockUnallocated()) {
            for (int i = 0; i < nodes.size(); i++) {
                tester.nodeRepository().write(nodes.get(i).with(IP.Config.EMPTY.with(Set.of("127.0.0." + i))), lock);
            }
        }
    }

    private static ClusterSpec clusterRequest(ClusterSpec.Type type, ClusterSpec.Id id) {
        return ClusterSpec.request(type, id, Version.fromString("6.42"), false, Optional.empty());
    }

    private static <T> T get(Set<T> set, int position) {
        return Iterators.get(set.iterator(), position, null);
    }

}
