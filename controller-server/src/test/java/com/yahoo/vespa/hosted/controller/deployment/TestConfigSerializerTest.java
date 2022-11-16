// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.application.Endpoint;
import com.yahoo.vespa.hosted.controller.application.EndpointId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTester.instanceId;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
public class TestConfigSerializerTest {

    @Test
    void testConfig() throws IOException {
        ZoneId zone = DeploymentContext.systemTest.zone();
        byte[] json = new TestConfigSerializer(SystemName.PublicCd)
                .configJson(instanceId,
                            DeploymentContext.systemTest,
                            true,
                            Version.fromString("1.2.3"),
                            RevisionId.forProduction(321),
                            Instant.ofEpochMilli(222),
                            Map.of(zone, List.of(Endpoint.of(ApplicationId.defaultId())
                                                         .target(EndpointId.of("ai"), ClusterSpec.Id.from("qrs"),
                                                                                                     List.of(new DeploymentId(ApplicationId.defaultId(),
                                                                                                                              ZoneId.defaultId())))
                                                         .on(Endpoint.Port.tls())
                                                         .in(SystemName.main))),
                            Map.of(zone, List.of("facts")));
        byte[] expected = Files.readAllBytes(Paths.get("src/test/resources/testConfig.json"));
        assertEquals(new String(SlimeUtils.toJsonBytes(SlimeUtils.jsonToSlime(expected))),
                     new String(json));
    }

}
