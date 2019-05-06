// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Vtag;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class LoadBalancerExpirerTest {

    private ProvisioningTester tester = new ProvisioningTester.Builder().build();

    @Test
    public void test_maintain() {
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
        assertTrue(loadBalancers.get().get(lb1).inactive());
        assertFalse(loadBalancers.get().get(lb2).inactive());

        // Expirer defers removal while nodes are still allocated to application
        expirer.maintain();
        assertEquals(2, tester.loadBalancerService().instances().size());

        // Expirer removes load balancers once nodes are deallocated
        dirtyNodesOf(app1);
        expirer.maintain();
        assertFalse("Inactive load balancer removed", tester.loadBalancerService().instances().containsKey(lb1));

        // Active load balancer is left alone
        assertFalse(loadBalancers.get().get(lb2).inactive());
        assertTrue("Active load balancer is not removed", tester.loadBalancerService().instances().containsKey(lb2));
    }

    private void dirtyNodesOf(ApplicationId application) {
        tester.nodeRepository().setDirty(tester.nodeRepository().getNodes(application), Agent.system, "unit-test");
    }

    private void removeApplication(ApplicationId application) {
        NestedTransaction transaction = new NestedTransaction();
        tester.provisioner().remove(transaction, application);
        transaction.commit();
    }

    private void deployApplication(ApplicationId application, ClusterSpec.Id cluster) {
        tester.makeReadyNodes(10, "default");
        List<HostSpec> hosts = tester.prepare(application, ClusterSpec.request(ClusterSpec.Type.container, cluster,
                                                                               Vtag.currentVersion, false, Collections.emptySet()),
                                              2, 1,
                                              "default");
        tester.activate(application, hosts);
    }

}
