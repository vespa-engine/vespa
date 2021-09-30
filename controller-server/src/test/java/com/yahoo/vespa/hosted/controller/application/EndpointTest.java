// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class EndpointTest {

    private static final ApplicationId app1 = ApplicationId.from("t1", "a1", "default");
    private static final ApplicationId app2 = ApplicationId.from("t2", "a2", "i2");

    @Test
    public void global_endpoints() {
        EndpointId endpointId = EndpointId.defaultId();

        Map<String, Endpoint> tests = Map.of(
                // Legacy endpoint
                "http://a1.t1.global.vespa.yahooapis.com:4080/",
                Endpoint.of(app1).target(endpointId).on(Port.plain(4080)).legacy().in(SystemName.main),

                // Legacy endpoint with TLS
                "https://a1--t1.global.vespa.yahooapis.com:4443/",
                Endpoint.of(app1).target(endpointId).on(Port.tls(4443)).legacy().in(SystemName.main),

                // Main endpoint
                "https://a1--t1.global.vespa.oath.cloud:4443/",
                Endpoint.of(app1).target(endpointId).on(Port.tls(4443)).in(SystemName.main),

                // Main endpoint in CD
                "https://cd--a1--t1.global.vespa.oath.cloud:4443/",
                Endpoint.of(app1).target(endpointId).on(Port.tls(4443)).in(SystemName.cd),

                // Main endpoint in CD
                "https://cd--i2--a2--t2.global.vespa.oath.cloud:4443/",
                Endpoint.of(app2).target(endpointId).on(Port.tls(4443)).in(SystemName.cd),

                // Main endpoint with direct routing and default TLS port
                "https://a1.t1.global.vespa.oath.cloud/",
                Endpoint.of(app1).target(endpointId).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint with custom rotation name
                "https://r1.a1.t1.global.vespa.oath.cloud/",
                Endpoint.of(app1).target(EndpointId.of("r1")).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint for custom instance in default rotation
                "https://i2.a2.t2.global.vespa.oath.cloud/",
                Endpoint.of(app2).target(endpointId).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint for custom instance with custom rotation name
                "https://r2.i2.a2.t2.global.vespa.oath.cloud/",
                Endpoint.of(app2).target(EndpointId.of("r2")).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint in public system (legacy)
                "https://a1.t1.global.public.vespa.oath.cloud/",
                Endpoint.of(app1).target(endpointId).on(Port.tls()).routingMethod(RoutingMethod.exclusive).legacy().in(SystemName.Public)
        );
        tests.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));

        Map<String, Endpoint> tests2 = Map.of(
                // Main endpoint in public CD system (legacy)
                "https://publiccd.a1.t1.global.public-cd.vespa.oath.cloud/",
                Endpoint.of(app1).target(endpointId).on(Port.tls()).routingMethod(RoutingMethod.exclusive).legacy().in(SystemName.PublicCd),

                // Default endpoint in public system
                "https://a1.t1.g.vespa-app.cloud/",
                Endpoint.of(app1).target(endpointId).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.Public),

                // Default endpoint in public CD system
                "https://a1.t1.g.cd.vespa-app.cloud/",
                Endpoint.of(app1).target(endpointId).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.PublicCd),

                // Custom instance in public system
                "https://i2.a2.t2.g.vespa-app.cloud/",
                Endpoint.of(app2).target(endpointId).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.Public)
        );
        tests2.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));
    }

    @Test
    public void global_endpoints_with_endpoint_id() {
        var endpointId = EndpointId.defaultId();

        Map<String, Endpoint> tests = Map.of(
                // Legacy endpoint
                "http://a1.t1.global.vespa.yahooapis.com:4080/",
                Endpoint.of(app1).target(endpointId).on(Port.plain(4080)).legacy().in(SystemName.main),

                // Legacy endpoint with TLS
                "https://a1--t1.global.vespa.yahooapis.com:4443/",
                Endpoint.of(app1).target(endpointId).on(Port.tls(4443)).legacy().in(SystemName.main),

                // Main endpoint
                "https://a1--t1.global.vespa.oath.cloud:4443/",
                Endpoint.of(app1).target(endpointId).on(Port.tls(4443)).in(SystemName.main),

                // Main endpoint in CD
                "https://cd--i2--a2--t2.global.vespa.oath.cloud:4443/",
                Endpoint.of(app2).target(endpointId).on(Port.tls(4443)).in(SystemName.cd),

                // Main endpoint in CD
                "https://cd--a1--t1.global.vespa.oath.cloud:4443/",
                Endpoint.of(app1).target(endpointId).on(Port.tls(4443)).in(SystemName.cd),

                // Main endpoint with direct routing and default TLS port
                "https://a1.t1.global.vespa.oath.cloud/",
                Endpoint.of(app1).target(endpointId).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint with custom rotation name
                "https://r1.a1.t1.global.vespa.oath.cloud/",
                Endpoint.of(app1).target(EndpointId.of("r1")).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint for custom instance in default rotation
                "https://i2.a2.t2.global.vespa.oath.cloud/",
                Endpoint.of(app2).target(endpointId).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint for custom instance with custom rotation name
                "https://r2.i2.a2.t2.global.vespa.oath.cloud/",
                Endpoint.of(app2).target(EndpointId.of("r2")).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint in public system (legacy)
                "https://a1.t1.global.public.vespa.oath.cloud/",
                Endpoint.of(app1).target(endpointId).on(Port.tls()).routingMethod(RoutingMethod.exclusive).legacy().in(SystemName.Public)
        );
        tests.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));

        Map<String, Endpoint> tests2 = Map.of(
                // Custom endpoint and instance in public CD system (legacy)
                "https://foo.publiccd.i2.a2.t2.global.public-cd.vespa.oath.cloud/",
                Endpoint.of(app2).target(EndpointId.of("foo")).on(Port.tls()).routingMethod(RoutingMethod.exclusive).legacy().in(SystemName.PublicCd),

                // Custom endpoint and instance in public system
                "https://foo.i2.a2.t2.g.vespa-app.cloud/",
                Endpoint.of(app2).target(EndpointId.of("foo")).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.Public)
        );
        tests2.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));
    }

    @Test
    public void zone_endpoints() {
        var cluster = ClusterSpec.Id.from("default"); // Always default for non-direct routing
        var prodZone = ZoneId.from("prod", "us-north-1");
        var testZone = ZoneId.from("test", "us-north-2");

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

                // Non-default cluster in public (legacy)
                "https://c1.a1.t1.us-north-1.public.vespa.oath.cloud/",
                Endpoint.of(app1).target(ClusterSpec.Id.from("c1"), prodZone).on(Port.tls()).routingMethod(RoutingMethod.exclusive).legacy().in(SystemName.Public),

                // Non-default cluster and instance in public (legacy)
                "https://c2.i2.a2.t2.us-north-1.public.vespa.oath.cloud/",
                Endpoint.of(app2).target(ClusterSpec.Id.from("c2"), prodZone).on(Port.tls()).routingMethod(RoutingMethod.exclusive).legacy().in(SystemName.Public),

                // Endpoint in main using shared layer 4
                "https://a1.t1.us-north-1.vespa.oath.cloud/",
                Endpoint.of(app1).target(cluster, prodZone).on(Port.tls()).routingMethod(RoutingMethod.sharedLayer4).in(SystemName.main)
        );
        tests.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));

        Map<String, Endpoint> tests2 = Map.of(
                // Non-default cluster and instance in public CD (legacy)
                "https://c2.publiccd.i2.a2.t2.us-north-1.public-cd.vespa.oath.cloud/",
                Endpoint.of(app2).target(ClusterSpec.Id.from("c2"), prodZone).on(Port.tls()).routingMethod(RoutingMethod.exclusive).legacy().in(SystemName.PublicCd),

                // Custom cluster name in public
                "https://c1.a1.t1.us-north-1.z.vespa-app.cloud/",
                Endpoint.of(app1).target(ClusterSpec.Id.from("c1"), prodZone).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.Public),

                // Default cluster name in non-production zone in public
                "https://a1.t1.us-north-2.test.z.vespa-app.cloud/",
                Endpoint.of(app1).target(ClusterSpec.Id.from("default"), testZone).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.Public),

                // Default cluster name in public CD
                "https://a1.t1.us-north-1.z.cd.vespa-app.cloud/",
                Endpoint.of(app1).target(ClusterSpec.Id.from("default"), prodZone).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.PublicCd)
        );
        tests2.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));
    }

    @Test
    public void wildcard_endpoints() {
        var defaultCluster = ClusterSpec.Id.from("default");
        var prodZone = ZoneId.from("prod", "us-north-1");
        var testZone = ZoneId.from("test", "us-north-2");

        var tests = Map.of(
                // Default rotation
                "https://a1.t1.global.public.vespa.oath.cloud/",
                Endpoint.of(app1)
                        .target(EndpointId.defaultId())
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .legacy()
                        .in(SystemName.Public),

                // Wildcard to match other rotations
                "https://*.a1.t1.global.public.vespa.oath.cloud/",
                Endpoint.of(app1)
                        .wildcard()
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .legacy()
                        .in(SystemName.Public),

                // Default cluster in zone
                "https://a1.t1.us-north-1.public.vespa.oath.cloud/",
                Endpoint.of(app1)
                        .target(defaultCluster, prodZone)
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .legacy()
                        .in(SystemName.Public),

                // Wildcard to match other clusters in zone
                "https://*.a1.t1.us-north-1.public.vespa.oath.cloud/",
                Endpoint.of(app1)
                        .wildcard(prodZone)
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .legacy()
                        .in(SystemName.Public),

                // Default cluster in test zone
                "https://a1.t1.us-north-2.test.public.vespa.oath.cloud/",
                Endpoint.of(app1)
                        .target(defaultCluster, testZone)
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .legacy()
                        .in(SystemName.Public),

                // Wildcard to match other clusters in test zone
                "https://*.a1.t1.us-north-2.test.public.vespa.oath.cloud/",
                Endpoint.of(app1)
                        .wildcard(testZone)
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .legacy()
                        .in(SystemName.Public),

                // Wildcard to match other clusters in zone
                "https://*.a1.t1.us-north-1.z.vespa-app.cloud/",
                Endpoint.of(app1)
                        .wildcard(prodZone)
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.Public)
        );

        tests.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));
    }

    @Test
    public void region_endpoints() {
        var cluster = ClusterSpec.Id.from("default");
        var prodZone = ZoneId.from("prod", "us-north-2");
        Map<String, Endpoint> tests = Map.of(
                "https://a1.t1.us-north-1-w.public.vespa.oath.cloud/",
                Endpoint.of(app1)
                        .targetRegion(cluster, ZoneId.from("prod", "us-north-1a"))
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .legacy()
                        .in(SystemName.Public),
                "https://a1.t1.us-north-2-w.public.vespa.oath.cloud/",
                Endpoint.of(app1)
                        .targetRegion(cluster, prodZone)
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .legacy()
                        .in(SystemName.Public),
                "https://a1.t1.us-north-2-w.test.public.vespa.oath.cloud/",
                Endpoint.of(app1)
                        .targetRegion(cluster, ZoneId.from("test", "us-north-2"))
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .legacy()
                        .in(SystemName.Public),
                "https://c1.a1.t1.us-north-2.r.vespa-app.cloud/",
                Endpoint.of(app1)
                        .targetRegion(ClusterSpec.Id.from("c1"), prodZone)
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.Public)
        );
        tests.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));
        Endpoint endpoint = Endpoint.of(app1)
                                    .targetRegion(cluster, ZoneId.from("prod", "us-north-1a"))
                                    .routingMethod(RoutingMethod.exclusive)
                                    .on(Port.tls())
                                    .in(SystemName.main);
        assertEquals("Availability zone is removed from region",
                     "us-north-1",
                     endpoint.zones().get(0).region().value());
    }

    @Test
    public void upstream_name() {
        var zone = ZoneId.from("prod", "us-north-1");
        var tests1 = Map.of(
                // With default cluster
                "a1.t1.us-north-1.prod",
                Endpoint.of(app1).target(EndpointId.defaultId()).on(Port.tls(4443)).in(SystemName.main),

                // With non-default cluster
                "c1.a1.t1.us-north-1.prod",
                Endpoint.of(app1).target(EndpointId.of("ignored1"), ClusterSpec.Id.from("c1"), List.of(zone)).on(Port.tls(4443)).in(SystemName.main)
        );
        var tests2 = Map.of(
                // With non-default instance and default cluster
                "i2.a2.t2.us-north-1.prod",
                Endpoint.of(app2).target(EndpointId.defaultId(), ClusterSpec.Id.from("default"), List.of(zone)).on(Port.tls(4443)).in(SystemName.main),

                // With non-default instance and cluster
                "c2.i2.a2.t2.us-north-1.prod",
                Endpoint.of(app2).target(EndpointId.of("ignored2"), ClusterSpec.Id.from("c2"), List.of(zone)).on(Port.tls(4443)).in(SystemName.main)
        );
        tests1.forEach((expected, endpoint) -> assertEquals(expected, endpoint.upstreamIdOf(new DeploymentId(app1, zone))));
        tests2.forEach((expected, endpoint) -> assertEquals(expected, endpoint.upstreamIdOf(new DeploymentId(app2, zone))));
    }

}
