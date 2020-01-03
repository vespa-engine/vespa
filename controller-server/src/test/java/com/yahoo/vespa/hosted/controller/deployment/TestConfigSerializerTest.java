// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static com.yahoo.vespa.hosted.controller.deployment.DeploymentTester.instanceId;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class TestConfigSerializerTest {

    @Test
    public void testConfig() throws IOException {
        ZoneId zone = JobType.systemTest.zone(SystemName.PublicCd);
        byte[] json = new TestConfigSerializer(SystemName.PublicCd).configJson(instanceId,
                                                                               JobType.systemTest,
                                                                               true,
                                                                               Map.of(zone, Map.of(ClusterSpec.Id.from("ai"),
                                                                                                   URI.create("https://server/"))),
                                                                               Map.of(zone, List.of("facts")));
        byte[] expected = Files.readAllBytes(Paths.get("src/test/resources/testConfig.json"));
        assertEquals(new String(SlimeUtils.toJsonBytes(SlimeUtils.jsonToSlime(expected))),
                     new String(json));
    }

}
