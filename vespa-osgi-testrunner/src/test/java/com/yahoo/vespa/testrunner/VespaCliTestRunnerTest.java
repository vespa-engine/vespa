// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author jonmv
 */
class VespaCliTestRunnerTest {

    @Test
    void testEndpointsConfig() throws IOException {
        byte[] testConfig = ("{\n" +
                             "  \"application\": \"t:a:i\",\n" +
                             "  \"zone\": \"dev.aws-us-east-1c\",\n" +
                             "  \"system\": \"publiccd\",\n" +
                             "  \"isCI\": true,\n" +
                             "  \"zoneEndpoints\": {\n" +
                             "    \"dev.aws-us-east-1c\": {\n" +
                             "      \"default\": \"https://dev.endpoint:443/\"\n" +
                             "    },\n" +
                             "    \"prod.aws-us-east-1a\": {\n" +
                             "      \"default\": \"https://prod.endpoint:443/\"\n" +
                             "    }\n" +
                             "  },\n" +
                             "  \"clusters\": {\n" +
                             "    \"prod.aws-us-east-1c\": [\n" +
                             "      \"documents\"\n" +
                             "    ]\n" +
                             "  }\n" +
                             "}\n").getBytes(StandardCharsets.UTF_8);

        assertEquals("{\"endpoints\":[{\"cluster\":\"default\",\"url\":\"https://dev.endpoint:443/\"}]}",
                     VespaCliTestRunner.toEndpointsConfig(testConfig));
    }

    @Test
    void testSuitePathDiscovery() throws IOException {
        Path temp = Files.createTempDirectory("vespa-cli-test-runner-test-");
        temp.toFile().deleteOnExit();
        VespaCliTestRunner runner = new VespaCliTestRunner(temp);
        assertFalse(runner.isSupported());

        Path tests = Files.createDirectory(temp.resolve("tests"));
        assertTrue(runner.isSupported());
        IllegalStateException expected = assertThrows(IllegalStateException.class,
                                                      () -> runner.testRunProcessBuilder(TestRunner.Suite.SYSTEM_TEST, ""));
        assertEquals("No tests found, for suite 'SYSTEM_TEST'", expected.getMessage());

        Path systemTests = Files.createDirectory(tests.resolve("system-test"));
        ProcessBuilder builder = runner.testRunProcessBuilder(TestRunner.Suite.SYSTEM_TEST, "config");
        assertEquals(systemTests.toFile(), builder.directory());
        assertEquals(List.of("vespa", "test", "--endpoints", "config"), builder.command());
    }

}
