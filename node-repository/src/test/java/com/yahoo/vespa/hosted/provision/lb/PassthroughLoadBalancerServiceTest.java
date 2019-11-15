// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.lb;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class PassthroughLoadBalancerServiceTest {

    @Test
    public void create() {
        var lbService = new PassthroughLoadBalancerService();
        var real = new Real(HostName.from("host1.example.com"), "192.0.2.10");
        var reals = Set.of(real, new Real(HostName.from("host2.example.com"), "192.0.2.11"));
        var instance = lbService.create(ApplicationId.from("tenant1", "app1", "default"),
                                        ClusterSpec.Id.from("c1"), reals, false);
        assertEquals(real.hostname(), instance.hostname());
        assertEquals(Set.of(real.port()), instance.ports());
        assertEquals(Set.of(real.ipAddress() + "/32"), instance.networks());
    }

}
