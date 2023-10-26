// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import com.yahoo.test.json.JsonTestHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

/**
 * @author mortent
 */
public class TestDescriptorTest {

    @Test
    public void parses_system_test_only () {
        String testDescriptor = "{\n" +
                                "  \"version\": \"1.0\",\n" +
                                "  \"configuredTests\": {\n" +
                                "    \"systemTests\": [\n" +
                                "      \"ai.vespa.test.SystemTest1\",\n" +
                                "      \"ai.vespa.test.SystemTest2\"\n" +
                                "    ]\n" +
                                "  " +
                                "}\n" +
                                "}";
        var testClassDescriptor = TestDescriptor.fromJsonString(testDescriptor);

        var systemTests = testClassDescriptor.getConfiguredTests(TestDescriptor.TestCategory.systemtest);
        Assertions.assertIterableEquals(List.of("ai.vespa.test.SystemTest1", "ai.vespa.test.SystemTest2"), systemTests);

        var stagingTests = testClassDescriptor.getConfiguredTests(TestDescriptor.TestCategory.stagingtest);
        Assertions.assertIterableEquals(Collections.emptyList(), stagingTests);

        var stagingSetupTests = testClassDescriptor.getConfiguredTests(TestDescriptor.TestCategory.stagingtest);
        Assertions.assertIterableEquals(Collections.emptyList(), stagingSetupTests);

        var productionTests = testClassDescriptor.getConfiguredTests(TestDescriptor.TestCategory.productiontest);
        Assertions.assertIterableEquals(Collections.emptyList(), productionTests);
    }

    @Test
    public void parsesDescriptorFile() {
        String testDescriptor = "{\n" +
                                "  \"" +
                                "version\": \"1.0\",\n" +
                                "  \"configuredTests\": {\n" +
                                "    \"systemTests\": [\n" +
                                "      \"ai.vespa.test.SystemTest1\",\n" +
                                "      \"ai.vespa.test.SystemTest2\"\n" +
                                "    ],\n" +
                                "    \"stagingTests\": [\n" +
                                "      \"ai.vespa.test.StagingTest1\",\n" +
                                "      \"ai.vespa.test.StagingTest2\"\n" +
                                "    ],\n" +
                                "    \"stagingSetupTests\": [\n" +
                                "      \"ai.vespa.test.StagingSetupTest1\",\n" +
                                "      \"ai.vespa.test.StagingSetupTest2\"\n" +
                                "    ],\n" +
                                "    \"productionTests\": [\n" +
                                "      \"ai.vespa.test.ProductionTest1\",\n" +
                                "      \"ai.vespa.test.ProductionTest2\"\n" +
                                "    ]\n" +
                                "  " +
                                "}\n" +
                                "}";
        var testClassDescriptor = TestDescriptor.fromJsonString(testDescriptor);

        var systemTests = testClassDescriptor.getConfiguredTests(TestDescriptor.TestCategory.systemtest);
        Assertions.assertIterableEquals(List.of("ai.vespa.test.SystemTest1", "ai.vespa.test.SystemTest2"), systemTests);

        var stagingTests = testClassDescriptor.getConfiguredTests(TestDescriptor.TestCategory.stagingtest);
        Assertions.assertIterableEquals(List.of("ai.vespa.test.StagingTest1", "ai.vespa.test.StagingTest2"), stagingTests);

        var stagingSetupTests = testClassDescriptor.getConfiguredTests(TestDescriptor.TestCategory.stagingsetuptest);
        Assertions.assertIterableEquals(List.of("ai.vespa.test.StagingSetupTest1", "ai.vespa.test.StagingSetupTest2"), stagingSetupTests);

        var productionTests = testClassDescriptor.getConfiguredTests(TestDescriptor.TestCategory.productiontest);
        Assertions.assertIterableEquals(List.of("ai.vespa.test.ProductionTest1", "ai.vespa.test.ProductionTest2"), productionTests);

        JsonTestHelper.assertJsonEquals(testClassDescriptor.toJson(), testDescriptor);
    }

    @Test
    public void generatesCorrectJson() {
        String json = "{\n" +
                "  \"version\": \"1.0\",\n" +
                "  \"configuredTests\": {\n" +
                "    \"systemTests\": [\n" +
                "      \"ai.vespa.test.SystemTest1\",\n" +
                "      \"ai.vespa.test.SystemTest2\"\n" +
                "    ]\n" +
                "  " +
                "  }\n" +
                "}\n";
        var descriptor = TestDescriptor.fromJsonString(json);
        JsonTestHelper.assertJsonEquals(json, descriptor.toJson());
    }
}
