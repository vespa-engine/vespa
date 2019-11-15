// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author ogronnesby
 */
public class SharedLoadBalancerServiceTest {

    private final ProvisioningTester tester = new ProvisioningTester.Builder().build();
    private final SharedLoadBalancerService loadBalancerService = new SharedLoadBalancerService(tester.nodeRepository());
    private final ApplicationId applicationId = ApplicationId.from("tenant1", "application1", "default");
    private final ClusterSpec.Id clusterId = ClusterSpec.Id.from("qrs1");
    private final Set<Real> reals = Set.of(
            new Real(HostName.from("some.nice.host"), "10.23.56.102"),
            new Real(HostName.from("some.awful.host"), "10.23.56.103")
    );

    @Test
    public void test_create_lb() {
        tester.makeReadyNodes(2, "default", NodeType.proxy);
        var lb = loadBalancerService.create(applicationId, clusterId, reals, false);

        assertEquals(HostName.from("host-1.yahoo.com"), lb.hostname());
        assertEquals(Optional.empty(), lb.dnsZone());
        assertEquals(Set.of("127.0.0.1/32", "127.0.0.2/32", "::1/128", "::2/128"), lb.networks());
        assertEquals(Set.of(4080, 4443), lb.ports());
    }

    @Test(expected = IllegalStateException.class)
    public void test_exception_on_missing_proxies() {
        loadBalancerService.create(applicationId, clusterId, reals, false);
    }

    @Test
    public void test_protocol() {
        assertEquals(LoadBalancerService.Protocol.dualstack, loadBalancerService.protocol());
    }

}
