// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jonmv
 */
public class JobTypeTest {

    @Test
    public void test() {
        for (JobType type : JobType.values()) {
            if (type.isProduction()) {
                boolean match = false;
                for (JobType other : JobType.values())
                    match |=    type != other
                             && type.isTest() == other.isDeployment()
                             && type.zones.equals(other.zones);

                assertTrue(type + " should have matching job", match);
            }
        }

        assertEquals(JobType.testUsEast3, JobType.fromJobName("prod.us-east-3.test"));
        assertEquals(JobType.devAwsUsEast1c, JobType.fromJobName("dev.aws-us-east-1c"));

        assertFalse(JobType.dev("snøhetta").isTest());
        assertTrue(JobType.dev("snøhetta").isDeployment());
        assertFalse(JobType.dev("snøhetta").isProduction());

        assertFalse(JobType.perf("snøhetta").isTest());
        assertTrue(JobType.perf("snøhetta").isDeployment());
        assertFalse(JobType.perf("snøhetta").isProduction());

        assertTrue(JobType.deploymentTo(ZoneId.from("test", "snøhetta")).isTest());
        assertTrue(JobType.deploymentTo(ZoneId.from("test", "snøhetta")).isDeployment());
        assertFalse(JobType.deploymentTo(ZoneId.from("test", "snøhetta")).isProduction());

        assertTrue(JobType.deploymentTo(ZoneId.from("staging", "snøhetta")).isTest());
        assertTrue(JobType.deploymentTo(ZoneId.from("staging", "snøhetta")).isDeployment());
        assertFalse(JobType.deploymentTo(ZoneId.from("staging", "snøhetta")).isProduction());

        assertFalse(JobType.prod("snøhetta").isTest());
        assertTrue(JobType.prod("snøhetta").isDeployment());
        assertTrue(JobType.prod("snøhetta").isProduction());

        assertTrue(JobType.test("snøhetta").isTest());
        assertFalse(JobType.test("snøhetta").isDeployment());
        assertTrue(JobType.test("snøhetta").isProduction());
    }

}
