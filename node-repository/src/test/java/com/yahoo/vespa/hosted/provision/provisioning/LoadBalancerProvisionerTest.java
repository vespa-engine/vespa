// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.common.collect.Iterators;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.RotationName;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.flag.FlagId;
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
import static org.junit.Assert.assertFalse;
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
        ClusterSpec.Id containerCluster1 = ClusterSpec.Id.from("qrs1");
        ClusterSpec.Id contentCluster = ClusterSpec.Id.from("content");
        Set<RotationName> rotationsCluster1 = Set.of(RotationName.from("r1-1"), RotationName.from("r1-2"));
        tester.nodeRepository().flags().setEnabled(FlagId.exclusiveLoadBalancer, true);
        tester.activate(app1, prepare(app1,
                                      clusterRequest(ClusterSpec.Type.container, containerCluster1, rotationsCluster1),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));
        tester.activate(app2, prepare(app2,
                                      clusterRequest(ClusterSpec.Type.container, ClusterSpec.Id.from("qrs"))));

        // Provision a load balancer for each application
        Supplier<List<LoadBalancer>> loadBalancers = () -> tester.nodeRepository().loadBalancers().owner(app1).asList();
        assertEquals(1, loadBalancers.get().size());

        assertEquals(app1, loadBalancers.get().get(0).id().application());
        assertEquals(containerCluster1, loadBalancers.get().get(0).id().cluster());
        assertEquals(Collections.singleton(4443), loadBalancers.get().get(0).instance().ports());
        assertEquals("127.0.0.1", get(loadBalancers.get().get(0).instance().reals(), 0).ipAddress());
        assertEquals(4080, get(loadBalancers.get().get(0).instance().reals(), 0).port());
        assertEquals("127.0.0.2", get(loadBalancers.get().get(0).instance().reals(), 1).ipAddress());
        assertEquals(4080, get(loadBalancers.get().get(0).instance().reals(), 1).port());
        assertEquals(rotationsCluster1, loadBalancers.get().get(0).rotations());

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

        // Add another container cluster
        Set<RotationName> rotationsCluster2 = Set.of(RotationName.from("r2-1"), RotationName.from("r2-2"));
        ClusterSpec.Id containerCluster2 = ClusterSpec.Id.from("qrs2");
        tester.activate(app1, prepare(app1,
                                      clusterRequest(ClusterSpec.Type.container, containerCluster1, rotationsCluster1),
                                      clusterRequest(ClusterSpec.Type.container, containerCluster2, rotationsCluster2),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));

        // Load balancer is provisioned for second container cluster
        assertEquals(2, loadBalancers.get().size());
        List<HostName> activeContainers = tester.getNodes(app1, Node.State.active)
                                                .type(ClusterSpec.Type.container).asList()
                                                .stream()
                                                .map(Node::hostname)
                                                .map(HostName::from)
                                                .sorted()
                                                .collect(Collectors.toList());
        List<HostName> reals = loadBalancers.get().stream()
                                            .map(LoadBalancer::instance)
                                            .map(LoadBalancerInstance::reals)
                                            .flatMap(Collection::stream)
                                            .map(Real::hostname)
                                            .sorted()
                                            .collect(Collectors.toList());
        assertEquals(rotationsCluster2, loadBalancers.get().get(1).rotations());
        assertEquals(activeContainers, reals);

        // Application is removed and load balancer is deactivated
        NestedTransaction removeTransaction = new NestedTransaction();
        tester.provisioner().remove(removeTransaction, app1);
        removeTransaction.commit();

        assertEquals(2, loadBalancers.get().size());
        assertTrue("Deactivated load balancers", loadBalancers.get().stream().allMatch(LoadBalancer::inactive));

        // Application is redeployed with one cluster and load balancer is re-activated
        tester.activate(app1, prepare(app1,
                                      clusterRequest(ClusterSpec.Type.container, containerCluster1),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));
        assertFalse("Re-activated load balancer for " + containerCluster1,
                    loadBalancers.get().stream()
                                 .filter(lb -> lb.id().cluster().equals(containerCluster1))
                                 .findFirst()
                                 .orElseThrow()
                                 .inactive());
    }

    private ClusterSpec clusterRequest(ClusterSpec.Type type, ClusterSpec.Id id) {
        return clusterRequest(type, id, Collections.emptySet());
    }

    private ClusterSpec clusterRequest(ClusterSpec.Type type, ClusterSpec.Id id, Set<RotationName> rotations) {
        return ClusterSpec.request(type, id, Version.fromString("6.42"), false, rotations);
    }

    private Set<HostSpec> prepare(ApplicationId application, ClusterSpec... specs) {
        tester.makeReadyNodes(specs.length * 2, "default");
        Set<HostSpec> allNodes = new LinkedHashSet<>();
        for (ClusterSpec spec : specs) {
            allNodes.addAll(tester.prepare(application, spec, 2, 1, "default"));
        }
        return allNodes;
    }

    private static <T> T get(Set<T> set, int position) {
        return Iterators.get(set.iterator(), position, null);
    }

}
