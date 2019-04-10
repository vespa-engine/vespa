// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.RotationName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class EndpointTest {

    private static final ApplicationId app1 = ApplicationId.from("t1", "a1", "default");
    private static final ApplicationId app2 = ApplicationId.from("t2", "a2", "i2");

    @Test
    public void test_global_endpoints() {
        RotationName rotation = RotationName.from("default"); // Always default for non-direct routing

        Map<String, Endpoint> tests = Map.of(
                // Legacy endpoint
                "http://a1.t1.global.vespa.yahooapis.com:4080/",
                Endpoint.of(app1).target(rotation).on(Port.plain(4080)).legacy().in(SystemName.main),

                // Legacy endpoint with TLS
                "https://a1--t1.global.vespa.yahooapis.com:4443/",
                Endpoint.of(app1).target(rotation).on(Port.tls(4443)).legacy().in(SystemName.main),

                // Main endpoint
                "https://a1--t1.global.vespa.oath.cloud:4443/",
                Endpoint.of(app1).target(rotation).on(Port.tls(4443)).in(SystemName.main),

                // Main endpoint in CD
                "https://cd--a1--t1.global.vespa.oath.cloud:4443/",
                Endpoint.of(app1).target(rotation).on(Port.tls(4443)).in(SystemName.cd),

                // Main endpoint with direct routing and default TLS port
                "https://a1.t1.global.vespa.oath.cloud/",
                Endpoint.of(app1).target(rotation).on(Port.tls()).directRouting().in(SystemName.main),

                // Main endpoint with custom rotation name
                "https://r1.a1.t1.global.vespa.oath.cloud/",
                Endpoint.of(app1).target(RotationName.from("r1")).on(Port.tls()).directRouting().in(SystemName.main),

                // Main endpoint for custom instance in default rotation
                "https://a2.t2.global.vespa.oath.cloud/",
                Endpoint.of(app2).target(rotation).on(Port.tls()).directRouting().in(SystemName.main),

                // Main endpoint for custom instance with custom rotation name
                "https://r2.a2.t2.global.vespa.oath.cloud/",
                Endpoint.of(app2).target(RotationName.from("r2")).on(Port.tls()).directRouting().in(SystemName.main),

                // Main endpoint in public system
                "https://a1.t1.global.public.vespa.oath.cloud/",
                Endpoint.of(app1).target(rotation).on(Port.tls()).directRouting().in(SystemName.Public)
        );
        tests.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));
    }

    @Test
    public void test_zone_endpoints() {
        ClusterSpec.Id cluster = ClusterSpec.Id.from("default"); // Always default for non-direct routing
        ZoneId prodZone = ZoneId.from("prod", "us-north-1");
        ZoneId testZone = ZoneId.from("test", "us-north-2");

        Map<String, Endpoint> tests = Map.of(
                // Legacy endpoint (always contains environment)
                "http://a1.t1.us-north-1.prod.vespa.yahooapis.com:4080/",
                Endpoint.of(app1).target(cluster, prodZone).on(Port.plain(4080)).legacy().in(SystemName.main),

                // Secure legacy endpoint
                "https://a1--t1.us-north-1.prod.vespa.yahooapis.com:4443/",
                Endpoint.of(app1).target(cluster, prodZone).on(Port.tls(4443)).legacy().in(SystemName.main),

                // Prod endpoint in main
                "https://a1--t1.us-north-1.vespa.oath.cloud:4443/",
                Endpoint.of(app1).target(cluster, prodZone).on(Port.tls(4443)).in(SystemName.main),

                // Prod endpoint in CD
                "https://cd--a1--t1.us-north-1.vespa.oath.cloud:4443/",
                Endpoint.of(app1).target(cluster, prodZone).on(Port.tls(4443)).in(SystemName.cd),

                // Test endpoint in main
                "https://a1--t1.us-north-2.test.vespa.oath.cloud:4443/",
                Endpoint.of(app1).target(cluster, testZone).on(Port.tls(4443)).in(SystemName.main),

                // Non-default cluster in main
                "https://c1--a1--t1.us-north-1.vespa.oath.cloud/",
                Endpoint.of(app1).target(ClusterSpec.Id.from("c1"), prodZone).on(Port.tls()).in(SystemName.main),

                // Non-default instance in main
                "https://i2--a2--t2.us-north-1.vespa.oath.cloud:4443/",
                Endpoint.of(app2).target(cluster, prodZone).on(Port.tls(4443)).in(SystemName.main),

                // Non-default cluster in public
                "https://c1.a1.t1.us-north-1.public.vespa.oath.cloud/",
                Endpoint.of(app1).target(ClusterSpec.Id.from("c1"), prodZone).on(Port.tls()).directRouting().in(SystemName.Public),

                // Non-default cluster and instance in public
                "https://c2.i2.a2.t2.us-north-1.public.vespa.oath.cloud/",
                Endpoint.of(app2).target(ClusterSpec.Id.from("c2"), prodZone).on(Port.tls()).directRouting().in(SystemName.Public)
        );
        tests.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));
    }

}
