// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.routing;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.routing.RoutingTable.Endpoint;
import com.yahoo.vespa.hosted.routing.RoutingTable.Real;
import com.yahoo.vespa.hosted.routing.RoutingTable.Target;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class RoutingTableTest {

    @Test
    public void translate_from_lb_services_config() {
        RoutingTable expected = new RoutingTable(Map.of(
                new Endpoint("beta.music.vespa.us-north-1.vespa.oath.cloud", RoutingMethod.sharedLayer4),
                Target.create(ApplicationId.from("vespa", "music", "beta"),
                              ClusterSpec.Id.from("default"), ZoneId.from("prod.us-north-1"),
                              List.of(new Real("host3-beta", 4443, 1, true),
                                      new Real("host4-beta", 4443, 1, true))),

                new Endpoint("music.vespa.global.vespa.oath.cloud", RoutingMethod.sharedLayer4),
                Target.create(ApplicationId.from("vespa", "music", "default"),
                              ClusterSpec.Id.from("default"), ZoneId.from("prod.us-north-1"),
                              List.of(new Real("host1-default", 4443, 1, true),
                                      new Real("host2-default", 4443, 1, true))),

                new Endpoint("music.vespa.us-north-1.vespa.oath.cloud", RoutingMethod.sharedLayer4),
                Target.create(ApplicationId.from("vespa", "music", "default"),
                              ClusterSpec.Id.from("default"), ZoneId.from("prod.us-north-1"),
                              List.of(new Real("host1-default", 4443, 1, true),
                                      new Real("host2-default", 4443, 1, true))),

                new Endpoint("rotation-02.vespa.global.routing", RoutingMethod.sharedLayer4),
                Target.create(ApplicationId.from("vespa", "music", "default"),
                              ClusterSpec.Id.from("default"), ZoneId.from("prod.us-north-1"),
                              List.of(new Real("host1-default", 4443, 1, true),
                                      new Real("host2-default", 4443, 1, true))),

                new Endpoint("use-weighted.music.vespa.us-north-1-r.vespa.oath.cloud", RoutingMethod.sharedLayer4),
                Target.create("use-weighted.music.vespa.us-north-1-r.vespa.oath.cloud", TenantName.from("vespa"), ApplicationName.from("music"),
                              ClusterSpec.Id.from("default"), ZoneId.from("prod.us-north-1"),
                              List.of(new Real("host3-beta", 4443, 1, true),
                                      new Real("host4-beta", 4443, 1, true),
                                      new Real("host1-default", 4443, 0, true),
                                      new Real("host2-default", 4443, 0, true)))
        ), 42);

        RoutingTable actual = TestUtil.readRoutingTable("lbservices-config");
        assertEquals(expected, actual);
    }

}
