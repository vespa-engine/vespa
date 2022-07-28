// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.zone.ZoneId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author jonmv
 */
public class JobTypeTest {

    @Test
    void test() {
        assertEquals(JobType.test("us-east-3"), JobType.ofSerialized("prod.us-east-3.test"));
        assertEquals(JobType.dev("aws-us-east-1c"), JobType.ofSerialized("dev.aws-us-east-1c"));

        assertEquals(JobType.fromJobName("production-my-zone", null), JobType.prod("my-zone"));
        assertEquals(JobType.fromJobName("test-my-zone", null), JobType.test("my-zone"));
        assertEquals(JobType.fromJobName("dev-my-zone", null), JobType.dev("my-zone"));
        assertEquals(JobType.fromJobName("perf-my-zone", null), JobType.perf("my-zone"));

        assertFalse(JobType.dev("snohetta").isTest());
        assertTrue(JobType.dev("snohetta").isDeployment());
        assertFalse(JobType.dev("snohetta").isProduction());

        assertFalse(JobType.perf("snohetta").isTest());
        assertTrue(JobType.perf("snohetta").isDeployment());
        assertFalse(JobType.perf("snohetta").isProduction());

        assertTrue(JobType.deploymentTo(ZoneId.from("test", "snohetta")).isTest());
        assertTrue(JobType.deploymentTo(ZoneId.from("test", "snohetta")).isDeployment());
        assertFalse(JobType.deploymentTo(ZoneId.from("test", "snohetta")).isProduction());

        assertTrue(JobType.deploymentTo(ZoneId.from("staging", "snohetta")).isTest());
        assertTrue(JobType.deploymentTo(ZoneId.from("staging", "snohetta")).isDeployment());
        assertFalse(JobType.deploymentTo(ZoneId.from("staging", "snohetta")).isProduction());

        assertFalse(JobType.prod("snohetta").isTest());
        assertTrue(JobType.prod("snohetta").isDeployment());
        assertTrue(JobType.prod("snohetta").isProduction());

        assertTrue(JobType.test("snohetta").isTest());
        assertFalse(JobType.test("snohetta").isDeployment());
        assertTrue(JobType.test("snohetta").isProduction());
    }

}