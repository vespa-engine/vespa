// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.common.collect.Iterators;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.Zone;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.flag.FlagId;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerService;
import com.yahoo.vespa.hosted.provision.lb.Real;
import com.yahoo.vespa.hosted.provision.node.Agent;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class LoadBalancerProvisionerTest {

    private final ApplicationId app1 = ApplicationId.from("tenant1", "application1", "default");
    private final ApplicationId app2 = ApplicationId.from("tenant2", "application2", "default");

    private ProvisioningTester tester;
    private LoadBalancerService service;
    private LoadBalancerProvisioner loadBalancerProvisioner;

    @Before
    public void before() {
        tester = new ProvisioningTester(Zone.defaultZone());
        service = tester.loadBalancerService();
        loadBalancerProvisioner = new LoadBalancerProvisioner(tester.nodeRepository(), service);
    }

    @Test
    public void provision_load_balancer() {
        ClusterSpec.Id containerCluster1 = ClusterSpec.Id.from("qrs1");
        ClusterSpec.Id contentCluster = ClusterSpec.Id.from("content");
        tester.nodeRepository().flags().setEnabled(FlagId.exclusiveLoadBalancer, true);
        tester.activate(app1, prepare(app1,
                                      clusterRequest(ClusterSpec.Type.container, containerCluster1),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));
        tester.activate(app2, prepare(app2,
                                      clusterRequest(ClusterSpec.Type.container, ClusterSpec.Id.from("qrs"))));

        // Provision a load balancer for each application
        List<LoadBalancer> loadBalancers = loadBalancerProvisioner.get(app1);
        assertEquals(1, loadBalancers.size());

        assertEquals(app1, loadBalancers.get(0).id().application());
        assertEquals(containerCluster1, loadBalancers.get(0).id().cluster());
        assertEquals(Collections.singleton(4443), loadBalancers.get(0).ports());
        assertEquals("127.0.0.1", get(loadBalancers.get(0).reals(), 0).ipAddress());
        assertEquals(4080, get(loadBalancers.get(0).reals(), 0).port());
        assertEquals("127.0.0.2", get(loadBalancers.get(0).reals(), 1).ipAddress());
        assertEquals(4080, get(loadBalancers.get(0).reals(), 1).port());

        // A container is failed
        List<Node> containers = tester.getNodes(app1).type(ClusterSpec.Type.container).asList();
        tester.nodeRepository().fail(containers.get(0).hostname(), Agent.system, this.getClass().getSimpleName());

        // Redeploying replaces failed node
        tester.activate(app1, prepare(app1,
                                      clusterRequest(ClusterSpec.Type.container, containerCluster1),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));

        // Redeploy removed replaced failed node in load balancer
        containers = tester.getNodes(app1).type(ClusterSpec.Type.container).asList();
        LoadBalancer loadBalancer = loadBalancerProvisioner.get(app1).get(0);
        assertEquals(2, loadBalancer.reals().size());
        assertEquals(containers.get(0).hostname(), get(loadBalancer.reals(), 0).hostname().value());
        assertEquals(containers.get(1).hostname(), get(loadBalancer.reals(), 1).hostname().value());

        // Add another container cluster
        ClusterSpec.Id containerCluster2 = ClusterSpec.Id.from("qrs2");
        tester.activate(app1, prepare(app1,
                                      clusterRequest(ClusterSpec.Type.container, containerCluster1),
                                      clusterRequest(ClusterSpec.Type.container, containerCluster2),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));

        // Load balancer is provisioned for second container cluster
        loadBalancers = loadBalancerProvisioner.get(app1);
        assertEquals(2, loadBalancers.size());
        List<HostName> activeContainers = tester.getNodes(app1, Node.State.active)
                                                .type(ClusterSpec.Type.container).asList()
                                                .stream()
                                                .map(Node::hostname)
                                                .map(HostName::from)
                                                .sorted()
                                                .collect(Collectors.toList());
        List<HostName> reals = loadBalancers.stream()
                                            .flatMap(lb -> lb.reals().stream())
                                            .map(Real::hostname)
                                            .sorted()
                                            .collect(Collectors.toList());
        assertEquals(activeContainers, reals);

        // Application is removed and load balancer is deactivated
        NestedTransaction removeTransaction = new NestedTransaction();
        tester.provisioner().remove(removeTransaction, app1);
        removeTransaction.commit();

        List<LoadBalancer> assignedLoadBalancer = tester.nodeRepository().database().readLoadBalancers(app1);
        assertEquals(2, loadBalancers.size());
        assertTrue("Deactivated load balancers", assignedLoadBalancer.stream().allMatch(LoadBalancer::inactive));
    }

    private ClusterSpec clusterRequest(ClusterSpec.Type type, ClusterSpec.Id id) {
        return ClusterSpec.request(type, id, Version.fromString("6.42"), false);
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
