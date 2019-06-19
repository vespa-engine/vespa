// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.common.collect.Iterators;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.Real;
import com.yahoo.vespa.hosted.provision.node.Agent;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class LoadBalancerProvisionerTest {

    private final ApplicationId app1 = ApplicationId.from("tenant1", "application1", "default");
    private final ApplicationId app2 = ApplicationId.from("tenant2", "application2", "default");

    private ProvisioningTester tester = new ProvisioningTester.Builder().build();

    @Test
    public void provision_load_balancer() {
        Supplier<List<LoadBalancer>> lbApp1 = () -> tester.nodeRepository().loadBalancers().owner(app1).asList();
        Supplier<List<LoadBalancer>> lbApp2 = () -> tester.nodeRepository().loadBalancers().owner(app2).asList();
        ClusterSpec.Id containerCluster1 = ClusterSpec.Id.from("qrs1");
        ClusterSpec.Id contentCluster = ClusterSpec.Id.from("content");

        // Provision a load balancer for each application
        var nodes = prepare(app1,
                clusterRequest(ClusterSpec.Type.container, containerCluster1),
                clusterRequest(ClusterSpec.Type.content, contentCluster));
        assertEquals(1, lbApp1.get().size());
        assertEquals("Prepare provisions load balancer with 0 reals", Set.of(), lbApp1.get().get(0).instance().reals());
        tester.activate(app1, nodes);
        tester.activate(app2, prepare(app2, clusterRequest(ClusterSpec.Type.container, ClusterSpec.Id.from("qrs"))));
        assertEquals(1, lbApp2.get().size());

        // Reals are configured after activation
        assertEquals(app1, lbApp1.get().get(0).id().application());
        assertEquals(containerCluster1, lbApp1.get().get(0).id().cluster());
        assertEquals(Collections.singleton(4443), lbApp1.get().get(0).instance().ports());
        assertEquals("127.0.0.1", get(lbApp1.get().get(0).instance().reals(), 0).ipAddress());
        assertEquals(4080, get(lbApp1.get().get(0).instance().reals(), 0).port());
        assertEquals("127.0.0.2", get(lbApp1.get().get(0).instance().reals(), 1).ipAddress());
        assertEquals(4080, get(lbApp1.get().get(0).instance().reals(), 1).port());

        // A container is failed
        Supplier<List<Node>> containers = () -> tester.getNodes(app1).type(ClusterSpec.Type.container).asList();
        Node toFail = containers.get().get(0);
        tester.nodeRepository().fail(toFail.hostname(), Agent.system, this.getClass().getSimpleName());

        // Redeploying replaces failed node and removes it from load balancer
        tester.activate(app1, prepare(app1,
                                      clusterRequest(ClusterSpec.Type.container, containerCluster1),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));
        LoadBalancer loadBalancer = tester.nodeRepository().loadBalancers().owner(app1).asList().get(0);
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

        // Application is removed and load balancer is deactivated
        NestedTransaction removeTransaction = new NestedTransaction();
        tester.provisioner().remove(removeTransaction, app1);
        removeTransaction.commit();

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

    private Set<HostSpec> prepare(ApplicationId application, ClusterSpec... specs) {
        tester.makeReadyNodes(specs.length * 2, "d-1-1-1");
        Set<HostSpec> allNodes = new LinkedHashSet<>();
        for (ClusterSpec spec : specs) {
            allNodes.addAll(tester.prepare(application, spec, Capacity.fromCount(2, new NodeResources(1, 1, 1), false, true), 1, false));
        }
        return allNodes;
    }

    private static ClusterSpec clusterRequest(ClusterSpec.Type type, ClusterSpec.Id id) {
        return ClusterSpec.request(type, id, Version.fromString("6.42"), false);
    }

    private static <T> T get(Set<T> set, int position) {
        return Iterators.get(set.iterator(), position, null);
    }

}
