// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.util;

import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

/**
 * @author valerijf
 */
public class SecretAgentScheduleMakerTest {

    @Test
    public void generateFullSecretAgentScheduleTest() {
        SecretAgentScheduleMaker scheduleMaker = new SecretAgentScheduleMaker("system-checks", 60,
                Paths.get("/some/test"), "arg1", "arg2 with space")
                .withTag("tenantName", "vespa")
                .withTag("applicationName", "canary-docker")
                .withTag("instanceName", "default")
                .withTag("applicationId", "vespa.canary-docker.default")
                .withTag("app", "canary-docker.default")
                .withTag("clustertype", "container")
                .withTag("clusterid", "canary")
                .withTag("vespaVersion", "6.13.37")
                .withTag("role", "tenants")
                .withTag("flavor", "docker")
                .withTag("state", "active")
                .withTag("zone", "test.us-west-5");

        assertEquals(
                "- id: system-checks\n" +
                "  interval: 60\n" +
                "  user: nobody\n" +
                "  check: /some/test\n" +
                "  args: \n" +
                "    - arg1\n" +
                "    - arg2 with space\n" +
                "  tags:\n" +
                "    tenantName: vespa\n" +
                "    applicationName: canary-docker\n" +
                "    instanceName: default\n" +
                "    applicationId: vespa.canary-docker.default\n" +
                "    app: canary-docker.default\n" +
                "    clustertype: container\n" +
                "    clusterid: canary\n" +
                "    vespaVersion: 6.13.37\n" +
                "    role: tenants\n" +
                "    flavor: docker\n" +
                "    state: active\n" +
                "    zone: test.us-west-5\n", scheduleMaker.toString());
    }

    @Test
    public void generateMinimalSecretAgentScheduleTest() {
        SecretAgentScheduleMaker scheduleMaker = new SecretAgentScheduleMaker("system-checks", 60,
                Paths.get("/some/test"));

        assertEquals(
                "- id: system-checks\n" +
                "  interval: 60\n" +
                "  user: nobody\n" +
                "  check: /some/test\n", scheduleMaker.toString());
    }

    @Test
    public void generateSecretAgentScheduleWithDifferentUserTest() {
        SecretAgentScheduleMaker scheduleMaker = new SecretAgentScheduleMaker("system-checks", 60,
                Paths.get("/some/test")).withRunAsUser("yahoo");

        assertEquals(
                "- id: system-checks\n" +
                "  interval: 60\n" +
                "  user: yahoo\n" +
                "  check: /some/test\n", scheduleMaker.toString());
    }
}
