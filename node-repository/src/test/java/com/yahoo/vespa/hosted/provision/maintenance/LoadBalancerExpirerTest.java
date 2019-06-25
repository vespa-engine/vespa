// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Vtag;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
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

    private ProvisioningTester tester = new ProvisioningTester.Builder().build();

    @Test
    public void test_remove_inactive() {
        LoadBalancerExpirer expirer = new LoadBalancerExpirer(tester.nodeRepository(),
                                                              Duration.ofDays(1),
                                                              tester.loadBalancerService());
        Supplier<Map<LoadBalancerId, LoadBalancer>> loadBalancers = () -> tester.nodeRepository().database().readLoadBalancers();

        // Deploy two applications with load balancers
        ClusterSpec.Id cluster = ClusterSpec.Id.from("qrs");
        ApplicationId app1 = tester.makeApplicationId();
        ApplicationId app2 = tester.makeApplicationId();
        LoadBalancerId lb1 = new LoadBalancerId(app1, cluster);
        LoadBalancerId lb2 = new LoadBalancerId(app2, cluster);
        deployApplication(app1, cluster);
        deployApplication(app2, cluster);
        assertEquals(2, loadBalancers.get().size());

        // Remove one application deactivates load balancers for that application
        removeApplication(app1);
        assertSame(LoadBalancer.State.inactive, loadBalancers.get().get(lb1).state());
        assertNotSame(LoadBalancer.State.inactive, loadBalancers.get().get(lb2).state());

        // Expirer defers removal while nodes are still allocated to application
        expirer.maintain();
        assertEquals(2, tester.loadBalancerService().instances().size());
        dirtyNodesOf(app1);

        // Expirer defers removal until expiration time passes
        expirer.maintain();
        assertTrue("Inactive load balancer not removed", tester.loadBalancerService().instances().containsKey(lb1));

        // Expirer removes load balancers once expiration time passes
        tester.clock().advance(Duration.ofHours(1));
        expirer.maintain();
        assertFalse("Inactive load balancer removed", tester.loadBalancerService().instances().containsKey(lb1));

        // Active load balancer is left alone
        assertSame(LoadBalancer.State.active, loadBalancers.get().get(lb2).state());
        assertTrue("Active load balancer is not removed", tester.loadBalancerService().instances().containsKey(lb2));
    }

    @Test
    public void test_expire_reserved() {
        LoadBalancerExpirer expirer = new LoadBalancerExpirer(tester.nodeRepository(),
                                                              Duration.ofDays(1),
                                                              tester.loadBalancerService());
        Supplier<Map<LoadBalancerId, LoadBalancer>> loadBalancers = () -> tester.nodeRepository().database().readLoadBalancers();


        // Prepare application
        ClusterSpec.Id cluster = ClusterSpec.Id.from("qrs");
        ApplicationId app = tester.makeApplicationId();
        LoadBalancerId lb = new LoadBalancerId(app, cluster);
        deployApplication(app, cluster, false);

        // Provisions load balancer in reserved
        assertSame(LoadBalancer.State.reserved, loadBalancers.get().get(lb).state());

        // Expirer does nothing
        expirer.maintain();
        assertSame(LoadBalancer.State.reserved, loadBalancers.get().get(lb).state());

        // Application never activates and nodes are dirtied. Expirer moves load balancer to inactive after timeout
        dirtyNodesOf(app);
        tester.clock().advance(Duration.ofHours(1));
        expirer.maintain();
        assertSame(LoadBalancer.State.inactive, loadBalancers.get().get(lb).state());

        // Expirer does nothing as inactive expiration time has not yet passed
        expirer.maintain();
        assertSame(LoadBalancer.State.inactive, loadBalancers.get().get(lb).state());

        // Expirer removes inactive load balancer
        tester.clock().advance(Duration.ofHours(1));
        expirer.maintain();
        assertFalse("Inactive load balancer removed", loadBalancers.get().containsKey(lb));
    }

    private void dirtyNodesOf(ApplicationId application) {
        tester.nodeRepository().setDirty(tester.nodeRepository().getNodes(application), Agent.system, this.getClass().getSimpleName());
    }

    private void removeApplication(ApplicationId application) {
        NestedTransaction transaction = new NestedTransaction();
        tester.provisioner().remove(transaction, application);
        transaction.commit();
    }

    private void deployApplication(ApplicationId application, ClusterSpec.Id cluster) {
        deployApplication(application, cluster, true);
    }

    private void deployApplication(ApplicationId application, ClusterSpec.Id cluster, boolean activate) {
        tester.makeReadyNodes(10, "d-1-1-1");
        List<HostSpec> hosts = tester.prepare(application, ClusterSpec.request(ClusterSpec.Type.container, cluster,
                                                                               Vtag.currentVersion, false),
                                              2, 1,
                                              new NodeResources(1, 1, 1));
        if (activate) {
            tester.activate(application, hosts);
        }
    }

}
