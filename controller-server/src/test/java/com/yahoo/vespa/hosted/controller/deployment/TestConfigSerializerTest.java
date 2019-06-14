package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static com.yahoo.vespa.hosted.controller.deployment.InternalDeploymentTester.appId;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class TestConfigSerializerTest {

    @Test
    public void testConfig() throws IOException {
        ZoneId zone = JobType.systemTest.zone(SystemName.PublicCd);
        byte[] json = new TestConfigSerializer(SystemName.PublicCd).configJson(appId,
                                                                               JobType.systemTest,
                                                                               Map.of(zone, Map.of(ClusterSpec.Id.from("ai"),
                                                                                                   URI.create("https://server/"))),
                                                                               Map.of(zone, List.of("facts")));
        byte[] expected = InternalStepRunnerTest.class.getResourceAsStream("/testConfig.json").readAllBytes();
        assertEquals(new String(SlimeUtils.toJsonBytes(SlimeUtils.jsonToSlime(expected))),
                     new String(json));
    }

}
