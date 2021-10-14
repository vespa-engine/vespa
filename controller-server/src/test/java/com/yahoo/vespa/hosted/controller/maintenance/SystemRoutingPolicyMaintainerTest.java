// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.dns.Record;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.integration.ZoneApiMock;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * @author mpolden
 */
public class SystemRoutingPolicyMaintainerTest {

    @Test
    public void maintain() {
        var tester = new ControllerTester();
        var updater = new SystemRoutingPolicyMaintainer(tester.controller(), Duration.ofDays(1));
        var dispatcher = new NameServiceDispatcher(tester.controller(), Duration.ofDays(1), Integer.MAX_VALUE);

        var zone = ZoneId.from("prod", "us-west-1");
        tester.zoneRegistry().exclusiveRoutingIn(ZoneApiMock.from(zone));
        tester.configServer().putLoadBalancers(zone, List.of(new LoadBalancer("lb1",
                                                                              SystemApplication.configServer.id(),
                                                                              ClusterSpec.Id.from("config"),
                                                                              Optional.of(HostName.from("lb1.example.com")),
                                                                              LoadBalancer.State.active,
                                                                              Optional.of("dns-zone-1"))));

        // Record is created
        updater.run();
        dispatcher.run();
        Set<Record> records = tester.nameService().records();
        assertEquals(1, records.size());
        Record record = records.iterator().next();
        assertSame(Record.Type.CNAME, record.type());
        assertEquals("cfg.prod.us-west-1.test.vip", record.name().asString());
        assertEquals("lb1.example.com.", record.data().asString());
    }

}
