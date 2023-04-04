// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.RoutingMethod;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.application.Endpoint.Port;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author mpolden
 */
public class EndpointTest {

    private static final ApplicationId instance1 = ApplicationId.from("t1", "a1", "default");
    private static final ApplicationId instance2 = ApplicationId.from("t2", "a2", "i2");
    private static final TenantAndApplicationId app1 = TenantAndApplicationId.from(instance1);
    private static final TenantAndApplicationId app2 = TenantAndApplicationId.from(instance2);

    @Test
    void global_endpoints() {
        DeploymentId deployment1 = new DeploymentId(instance1, ZoneId.from("prod", "us-north-1"));
        DeploymentId deployment2 = new DeploymentId(instance2, ZoneId.from("prod", "us-north-1"));
        ClusterSpec.Id cluster = ClusterSpec.Id.from("default");
        EndpointId endpointId = EndpointId.defaultId();

        Map<String, Endpoint> tests = Map.of(
                // Main endpoint with direct routing and default TLS port
                "https://a1.t1.global.vespa.oath.cloud/",
                Endpoint.of(instance1).target(endpointId, cluster, List.of(deployment1)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint with custom rotation name
                "https://r1.a1.t1.global.vespa.oath.cloud/",
                Endpoint.of(instance1).target(EndpointId.of("r1"), cluster, List.of(deployment1)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint for custom instance in default rotation
                "https://i2.a2.t2.global.vespa.oath.cloud/",
                Endpoint.of(instance2).target(endpointId, cluster, List.of(deployment2)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint for custom instance with custom rotation name
                "https://r2.i2.a2.t2.global.vespa.oath.cloud/",
                Endpoint.of(instance2).target(EndpointId.of("r2"), cluster, List.of(deployment2)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint in public system
                "https://a1.t1.g.vespa-app.cloud/",
                Endpoint.of(instance1).target(endpointId, cluster, List.of(deployment1)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.Public)
        );
        tests.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));

        Map<String, Endpoint> tests2 = Map.of(
                // Default endpoint in public system
                "https://a1.t1.g.vespa-app.cloud/",
                Endpoint.of(instance1).target(endpointId, cluster, List.of(deployment1)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.Public),

                // Default endpoint in public CD system
                "https://a1.t1.g.cd.vespa-app.cloud/",
                Endpoint.of(instance1).target(endpointId, cluster, List.of(deployment1)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.PublicCd),

                // Custom instance in public system
                "https://i2.a2.t2.g.vespa-app.cloud/",
                Endpoint.of(instance2).target(endpointId, cluster, List.of(deployment2)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.Public)
        );
        tests2.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));
    }

    @Test
    void global_endpoints_with_endpoint_id() {
        DeploymentId deployment1 = new DeploymentId(instance1, ZoneId.from("prod", "us-north-1"));
        DeploymentId deployment2 = new DeploymentId(instance2, ZoneId.from("prod", "us-north-1"));
        ClusterSpec.Id cluster = ClusterSpec.Id.from("default");
        EndpointId endpointId = EndpointId.defaultId();

        Map<String, Endpoint> tests = Map.of(
                // Main endpoint with direct routing and default TLS port
                "https://a1.t1.global.vespa.oath.cloud/",
                Endpoint.of(instance1).target(endpointId, cluster, List.of(deployment1)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint with custom rotation name
                "https://r1.a1.t1.global.vespa.oath.cloud/",
                Endpoint.of(instance1).target(EndpointId.of("r1"), cluster, List.of(deployment1)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint for custom instance in default rotation
                "https://i2.a2.t2.global.vespa.oath.cloud/",
                Endpoint.of(instance2).target(endpointId, cluster, List.of(deployment2)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint for custom instance with custom rotation name
                "https://r2.i2.a2.t2.global.vespa.oath.cloud/",
                Endpoint.of(instance2).target(EndpointId.of("r2"), cluster, List.of(deployment2)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.main),

                // Main endpoint in public system
                "https://a1.t1.g.vespa-app.cloud/",
                Endpoint.of(instance1).target(endpointId, cluster, List.of(deployment1)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.Public)
        );
        tests.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));

        Map<String, Endpoint> tests2 = Map.of(
                // Custom endpoint and instance in public CD system)
                "https://foo.i2.a2.t2.g.cd.vespa-app.cloud/",
                Endpoint.of(instance2).target(EndpointId.of("foo"), cluster, List.of(deployment2)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.PublicCd),

                // Custom endpoint and instance in public system
                "https://foo.i2.a2.t2.g.vespa-app.cloud/",
                Endpoint.of(instance2).target(EndpointId.of("foo"), cluster, List.of(deployment2)).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.Public)
        );
        tests2.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));
    }

    @Test
    void zone_endpoints() {
        var cluster = ClusterSpec.Id.from("default"); // Always default for non-direct routing
        var prodZone = new DeploymentId(instance1, ZoneId.from("prod", "us-north-1"));
        var prodZone2 = new DeploymentId(instance2, ZoneId.from("prod", "us-north-1"));
        var testZone = new DeploymentId(instance1, ZoneId.from("test", "us-north-2"));

        Map<String, Endpoint> tests = Map.of(
                // Prod endpoint in main
                "https://a1.t1.us-north-1.vespa.oath.cloud/",
                Endpoint.of(instance1).target(cluster, prodZone).on(Port.tls()).in(SystemName.main),

                // Prod endpoint in CD
                "https://cd.a1.t1.us-north-1.cd.vespa.oath.cloud/",
                Endpoint.of(instance1).target(cluster, prodZone).on(Port.tls()).in(SystemName.cd),

                // Test endpoint in main
                "https://a1.t1.us-north-2.test.vespa.oath.cloud/",
                Endpoint.of(instance1).target(cluster, testZone).on(Port.tls()).in(SystemName.main),

                // Non-default cluster in main
                "https://c1.a1.t1.us-north-1.vespa.oath.cloud/",
                Endpoint.of(instance1).target(ClusterSpec.Id.from("c1"), prodZone).on(Port.tls()).in(SystemName.main),

                // Non-default instance in main
                "https://i2.a2.t2.us-north-1.vespa.oath.cloud/",
                Endpoint.of(instance2).target(cluster, prodZone2).on(Port.tls()).in(SystemName.main),

                // Non-default cluster in public
                "https://c1.a1.t1.us-north-1.z.vespa-app.cloud/",
                Endpoint.of(instance1).target(ClusterSpec.Id.from("c1"), prodZone).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.Public),

                // Non-default cluster and instance in public
                "https://c2.i2.a2.t2.us-north-1.z.vespa-app.cloud/",
                Endpoint.of(instance2).target(ClusterSpec.Id.from("c2"), prodZone2).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.Public)
        );
        tests.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));

        Map<String, Endpoint> tests2 = Map.of(
                // Non-default cluster and instance in public CD (legacy)
                "https://c2.i2.a2.t2.us-north-1.z.cd.vespa-app.cloud/",
                Endpoint.of(instance2).target(ClusterSpec.Id.from("c2"), prodZone2).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.PublicCd),

                // Custom cluster name in public
                "https://c1.a1.t1.us-north-1.z.vespa-app.cloud/",
                Endpoint.of(instance1).target(ClusterSpec.Id.from("c1"), prodZone).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.Public),

                // Default cluster name in non-production zone in public
                "https://a1.t1.us-north-2.test.z.vespa-app.cloud/",
                Endpoint.of(instance1).target(ClusterSpec.Id.from("default"), testZone).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.Public),

                // Default cluster name in public CD
                "https://a1.t1.us-north-1.z.cd.vespa-app.cloud/",
                Endpoint.of(instance1).target(ClusterSpec.Id.from("default"), prodZone).on(Port.tls()).routingMethod(RoutingMethod.exclusive).in(SystemName.PublicCd)
        );
        tests2.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));
    }

    @Test
    void certificate_endpoints() {
        var defaultCluster = ClusterSpec.Id.from("default");
        var prodZone = new DeploymentId(instance1, ZoneId.from("prod", "us-north-1"));
        var testZone = new DeploymentId(instance1, ZoneId.from("test", "us-north-2"));

        var tests = Map.of(
                // Default rotation
                "https://a1.t1.g.vespa-app.cloud/",
                Endpoint.of(instance1)
                        .target(EndpointId.defaultId())
                        .certificateName()
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.Public),

                // Wildcard to match other rotations
                "https://*.a1.t1.g.vespa-app.cloud/",
                Endpoint.of(instance1)
                        .wildcard()
                        .certificateName()
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.Public),

                // Default cluster in zone
                "https://a1.t1.us-north-1.z.vespa-app.cloud/",
                Endpoint.of(instance1)
                        .target(defaultCluster, prodZone)
                        .certificateName()
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.Public),

                // Default cluster in test zone
                "https://a1.t1.us-north-2.test.z.vespa-app.cloud/",
                Endpoint.of(instance1)
                        .target(defaultCluster, testZone)
                        .certificateName()
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.Public),

                // Wildcard to match other clusters in test zone
                "https://*.a1.t1.us-north-2.test.z.vespa-app.cloud/",
                Endpoint.of(instance1)
                        .wildcard(testZone)
                        .certificateName()
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.Public),

                // Wildcard to match other clusters in zone
                "https://*.a1.t1.us-north-1.z.vespa-app.cloud/",
                Endpoint.of(instance1)
                        .wildcard(prodZone)
                        .certificateName()
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.Public)
        );

        tests.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));
    }

    @Test
    void region_endpoints() {
        var cluster = ClusterSpec.Id.from("default");
        var prodZone = ZoneId.from("prod", "us-north-2");
        Map<String, Endpoint> tests = Map.of(
                "https://a1.t1.aws-us-north-1.w.vespa-app.cloud/",
                Endpoint.of(instance1)
                        .targetRegion(cluster, ZoneId.from("prod", "aws-us-north-1a"))
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.Public),
                "https://a1.t1.gcp-us-south1.w.vespa-app.cloud/",
                Endpoint.of(instance1)
                        .targetRegion(cluster, ZoneId.from("prod", "gcp-us-south1-c"))
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.Public),
                "https://a1.t1.us-north-2.w.vespa-app.cloud/",
                Endpoint.of(instance1)
                        .targetRegion(cluster, prodZone)
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.Public),
                "https://c1.a1.t1.us-north-2.w.vespa-app.cloud/",
                Endpoint.of(instance1)
                        .targetRegion(ClusterSpec.Id.from("c1"), prodZone)
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.Public)
        );
        tests.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));

        assertEquals("aws-us-north-1",
                tests.get("https://a1.t1.aws-us-north-1.w.vespa-app.cloud/").targets().get(0).deployment().zoneId().region().value(),
                "Availability zone is removed from region");
        assertEquals("gcp-us-south1",
                tests.get("https://a1.t1.gcp-us-south1.w.vespa-app.cloud/").targets().get(0).deployment().zoneId().region().value(),
                "Availability zone is removed from region");
    }

    @Test
    void application_endpoints_legacy_dns_names() {
        Map<String, Endpoint> tests = Map.of(
                "weighted.a1.t1.us-west-1.r.vespa-app.cloud",
                Endpoint.of(app1)
                        .targetApplication(EndpointId.of("weighted"), ClusterSpec.Id.from("qrs"),
                                           Map.of(new DeploymentId(app1.instance("i1"), ZoneId.from("prod", "us-west-1")), 1))
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.Public),
                "weighted.a1.t1.us-west-1.r.cd.vespa-app.cloud",
                Endpoint.of(app1)
                        .targetApplication(EndpointId.of("weighted"), ClusterSpec.Id.from("qrs"),
                                           Map.of(new DeploymentId(app1.instance("i1"), ZoneId.from("prod", "us-west-1")), 1))
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.PublicCd),
                "a2.t2.us-east-3-r.vespa.oath.cloud",
                Endpoint.of(app2)
                        .targetApplication(EndpointId.defaultId(), ClusterSpec.Id.from("qrs"),
                                           Map.of(new DeploymentId(app2.instance("i1"), ZoneId.from("prod", "us-east-3")), 1))
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.main),
                "cd.a2.t2.us-east-3-r.cd.vespa.oath.cloud",
                Endpoint.of(app2)
                        .targetApplication(EndpointId.defaultId(), ClusterSpec.Id.from("qrs"),
                                           Map.of(new DeploymentId(app2.instance("i1"), ZoneId.from("prod", "us-east-3")), 1))
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.cd)
        );
        tests.forEach((expected, endpoint) -> assertEquals(expected, endpoint.legacyRegionalDnsName()));
    }

    @Test
    void application_endpoints() {
        Map<String, Endpoint> tests = Map.of(
                "https://weighted.a1.t1.a.vespa-app.cloud/",
                Endpoint.of(app1)
                        .targetApplication(EndpointId.of("weighted"), ClusterSpec.Id.from("qrs"),
                                Map.of(new DeploymentId(app1.instance("i1"), ZoneId.from("prod", "us-west-1")), 1))
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.Public),
                "https://weighted.a1.t1.a.cd.vespa-app.cloud/",
                Endpoint.of(app1)
                        .targetApplication(EndpointId.of("weighted"), ClusterSpec.Id.from("qrs"),
                                Map.of(new DeploymentId(app1.instance("i1"), ZoneId.from("prod", "us-west-1")), 1))
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.PublicCd),
                "https://a2.t2.a.vespa.oath.cloud/",
                Endpoint.of(app2)
                        .targetApplication(EndpointId.defaultId(), ClusterSpec.Id.from("qrs"),
                                Map.of(new DeploymentId(app2.instance("i1"), ZoneId.from("prod", "us-east-3")), 1))
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.main),
                "https://cd.a2.t2.a.cd.vespa.oath.cloud/",
                Endpoint.of(app2)
                        .targetApplication(EndpointId.defaultId(), ClusterSpec.Id.from("qrs"),
                                Map.of(new DeploymentId(app2.instance("i1"), ZoneId.from("prod", "us-east-3")), 1))
                        .routingMethod(RoutingMethod.exclusive)
                        .on(Port.tls())
                        .in(SystemName.cd)
        );
        tests.forEach((expected, endpoint) -> assertEquals(expected, endpoint.url().toString()));
    }

    @Test
    void upstream_name() {
        var zone = new DeploymentId(instance1, ZoneId.from("prod", "us-north-1"));
        var zone2 = new DeploymentId(instance2, ZoneId.from("prod", "us-north-1"));
        var tests1 = Map.of(
                // With default cluster
                "a1.t1.us-north-1.prod",
                Endpoint.of(instance1).target(EndpointId.defaultId(), ClusterSpec.Id.from("default"), List.of(zone)).on(Port.tls()).in(SystemName.main),

                // With non-default cluster
                "c1.a1.t1.us-north-1.prod",
                Endpoint.of(instance1).target(EndpointId.of("ignored1"), ClusterSpec.Id.from("c1"), List.of(zone)).on(Port.tls()).in(SystemName.main),

                // With application endpoint
                "c2.a1.t1.us-north-1.prod",
                Endpoint.of(app1).targetApplication(EndpointId.defaultId(), ClusterSpec.Id.from("c2"), Map.of(new DeploymentId(app1.instance("i1"), zone.zoneId()), 1))
                        .routingMethod(RoutingMethod.sharedLayer4)
                        .on(Port.tls())
                        .in(SystemName.main)
        );
        var tests2 = Map.of(
                // With non-default instance and default cluster
                "i2.a2.t2.us-north-1.prod",
                Endpoint.of(instance2).target(EndpointId.defaultId(), ClusterSpec.Id.from("default"), List.of(zone2)).on(Port.tls()).in(SystemName.main),

                // With non-default instance and cluster
                "c2.i2.a2.t2.us-north-1.prod",
                Endpoint.of(instance2).target(EndpointId.of("ignored2"), ClusterSpec.Id.from("c2"), List.of(zone2)).on(Port.tls()).in(SystemName.main)
        );
        tests1.forEach((expected, endpoint) -> assertEquals(expected, endpoint.upstreamName(zone)));
        tests2.forEach((expected, endpoint) -> assertEquals(expected, endpoint.upstreamName(zone2)));
    }

}
