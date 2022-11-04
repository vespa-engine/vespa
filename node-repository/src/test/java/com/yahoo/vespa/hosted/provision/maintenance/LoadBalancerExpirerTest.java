// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Vtag;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class LoadBalancerExpirerTest {

    private final ProvisioningTester tester = new ProvisioningTester.Builder().build();

    @Test
    public void expire_inactive() {
        LoadBalancerExpirer expirer = new LoadBalancerExpirer(tester.nodeRepository(),
                                                              Duration.ofDays(1),
                                                              tester.loadBalancerService(),
                                                              new TestMetric());
        Supplier<Map<LoadBalancerId, LoadBalancer>> loadBalancers = () -> tester.nodeRepository().database().readLoadBalancers((ignored) -> true);

        // Deploy two applications with a total of three load balancers
        ClusterSpec.Id cluster1 = ClusterSpec.Id.from("qrs");
        ClusterSpec.Id cluster2 = ClusterSpec.Id.from("qrs2");
        ApplicationId app1 = ProvisioningTester.applicationId();
        ApplicationId app2 = ProvisioningTester.applicationId();
        LoadBalancerId lb1 = new LoadBalancerId(app1, cluster1);
        LoadBalancerId lb2 = new LoadBalancerId(app2, cluster1);
        LoadBalancerId lb3 = new LoadBalancerId(app2, cluster2);
        deployApplication(app1, cluster1);
        deployApplication(app2, cluster1, cluster2);
        assertEquals(3, loadBalancers.get().size());

        // Remove one application deactivates load balancers for that application
        tester.remove(app1);
        assertSame(LoadBalancer.State.inactive, loadBalancers.get().get(lb1).state());
        assertNotSame(LoadBalancer.State.inactive, loadBalancers.get().get(lb2).state());

        // Expirer defers removal while nodes are still allocated to application
        tester.move(Node.State.ready, tester.nodeRepository().nodes().list(Node.State.dirty).asList());
        expirer.maintain();
        assertEquals(Set.of(), tester.loadBalancerService().instances().get(lb1).reals());
        assertEquals(Set.of(), loadBalancers.get().get(lb1).instance().get().reals());

        // Expirer defers removal of load balancer until expiration time passes
        expirer.maintain();
        assertSame(LoadBalancer.State.inactive, loadBalancers.get().get(lb1).state());
        assertTrue("Inactive load balancer not removed", tester.loadBalancerService().instances().containsKey(lb1));

        // Expirer removes load balancers once expiration time passes
        tester.clock().advance(Duration.ofHours(1).plus(Duration.ofSeconds(1)));
        expirer.maintain();
        assertFalse("Inactive load balancer removed", tester.loadBalancerService().instances().containsKey(lb1));

        // Active load balancer is left alone
        assertSame(LoadBalancer.State.active, loadBalancers.get().get(lb2).state());
        assertTrue("Active load balancer is not removed", tester.loadBalancerService().instances().containsKey(lb2));

        // A single cluster is removed
        deployApplication(app2, cluster1);
        expirer.maintain();
        assertSame(LoadBalancer.State.inactive, loadBalancers.get().get(lb3).state());

        // Expirer defers removal while nodes are still allocated to cluster
        expirer.maintain();
        assertEquals(2, tester.loadBalancerService().instances().size());
        removeNodesOf(app2, cluster2);

        // Expirer removes load balancer for removed cluster
        tester.clock().advance(Duration.ofHours(1).plus(Duration.ofSeconds(1)));
        expirer.maintain();
        assertFalse("Inactive load balancer removed", tester.loadBalancerService().instances().containsKey(lb3));
    }

    @Test
    public void expire_reserved() {
        LoadBalancerExpirer expirer = new LoadBalancerExpirer(tester.nodeRepository(),
                                                              Duration.ofDays(1),
                                                              tester.loadBalancerService(),
                                                              new TestMetric());
        Supplier<Map<LoadBalancerId, LoadBalancer>> loadBalancers = () -> tester.nodeRepository().database().readLoadBalancers((ignored) -> true);


        // Prepare application
        ClusterSpec.Id cluster = ClusterSpec.Id.from("qrs");
        ApplicationId app = ProvisioningTester.applicationId();
        LoadBalancerId lb = new LoadBalancerId(app, cluster);
        deployApplication(app, false, cluster);

        // Provisions load balancer in reserved
        assertSame(LoadBalancer.State.reserved, loadBalancers.get().get(lb).state());

        // Expirer does nothing
        expirer.maintain();
        assertSame(LoadBalancer.State.reserved, loadBalancers.get().get(lb).state());

        // Application never activates and nodes are dirtied and readied. Expirer moves load balancer to inactive after timeout
        removeNodesOf(app, cluster);
        tester.clock().advance(Duration.ofHours(1).plus(Duration.ofSeconds(1)));
        expirer.maintain();
        assertSame(LoadBalancer.State.inactive, loadBalancers.get().get(lb).state());

        // Expirer does nothing as inactive expiration time has not yet passed
        expirer.maintain();
        assertSame(LoadBalancer.State.inactive, loadBalancers.get().get(lb).state());

        // Expirer removes inactive load balancer
        tester.clock().advance(Duration.ofHours(1).plus(Duration.ofSeconds(1)));
        expirer.maintain();
        assertFalse("Inactive load balancer removed", loadBalancers.get().containsKey(lb));
    }

    private void removeNodesOf(ApplicationId application, ClusterSpec.Id cluster) {
        var nodes = tester.nodeRepository().nodes().list()
                          .owner(application)
                          .cluster(cluster)
                          .asList();
        nodes = tester.nodeRepository().nodes().deallocate(nodes, Agent.system, getClass().getSimpleName());
        tester.move(Node.State.ready, nodes);
    }

    private void deployApplication(ApplicationId application, ClusterSpec.Id... clusters) {
        deployApplication(application, true, clusters);
    }

    private void deployApplication(ApplicationId application, boolean activate, ClusterSpec.Id... clusters) {
        tester.makeReadyNodes(10, new NodeResources(1, 4, 10, 0.3));
        List<HostSpec> hosts = new ArrayList<>();
        for (var cluster : clusters) {
            hosts.addAll(tester.prepare(application, ClusterSpec.request(ClusterSpec.Type.container, cluster).vespaVersion(Vtag.currentVersion).build(),
                                        2, 1,
                                        new NodeResources(1, 4, 10, 0.3)));
        }
        if (activate) {
            tester.activate(application, hosts);
        }
    }

}
