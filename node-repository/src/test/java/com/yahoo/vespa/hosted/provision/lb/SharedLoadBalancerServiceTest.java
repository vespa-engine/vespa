// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.ZoneEndpoint;
import org.junit.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author ogronnesby
 */
public class SharedLoadBalancerServiceTest {

    private final SharedLoadBalancerService loadBalancerService = new SharedLoadBalancerService("vip.example.com");
    private final ApplicationId applicationId = ApplicationId.from("tenant1", "application1", "default");
    private final ClusterSpec.Id clusterId = ClusterSpec.Id.from("qrs1");
    private final Set<Real> reals = Set.of(
            new Real(HostName.of("some.nice.host"), "10.23.56.102"),
            new Real(HostName.of("some.awful.host"), "10.23.56.103")
    );

    @Test
    public void test_create_lb() {
        LoadBalancerSpec spec = new LoadBalancerSpec(applicationId, clusterId, reals,
                                                     ZoneEndpoint.defaultEndpoint, CloudAccount.empty);
        loadBalancerService.provision(spec);
        var lb = loadBalancerService.configure(spec, false);

        assertEquals(Optional.of(HostName.of("vip.example.com")), lb.hostname());
        assertEquals(Optional.empty(), lb.dnsZone());
        assertEquals(Set.of(), lb.networks());
        assertEquals(Set.of(4443), lb.ports());
    }

    @Test
    public void test_protocol() {
        assertEquals(LoadBalancerService.Protocol.dualstack, loadBalancerService.protocol());
    }

}
