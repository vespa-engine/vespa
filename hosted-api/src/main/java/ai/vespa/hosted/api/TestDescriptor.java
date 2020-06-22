// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeStream;
import com.yahoo.slime.SlimeUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author mortent
 */
public class TestDescriptor {
    public static final String DEFAULT_FILENAME = "META-INF/ai.vespa/testDescriptor.json";

    private final Map<TestCategory, List<String>> configuredTestClasses;
    private final String version;

    private TestDescriptor(String version, Map<TestCategory, List<String>> configuredTestClasses) {
        this.version = version;
        this.configuredTestClasses = configuredTestClasses;
    }

    public static TestDescriptor fromJsonString(String testDescriptor) {
        var slime = SlimeUtils.jsonToSlime(testDescriptor);
        var root = slime.get();
        var version = root.field("version").asString();
        var testRoot = root.field("configuredTests");
        var systemTests = getJsonArray(testRoot, "systemTests");
        var stagingTests = getJsonArray(testRoot, "stagingTests");
        var productionTests = getJsonArray(testRoot, "productionTests");
        return new TestDescriptor(version, Map.of(
                TestCategory.systemtest, systemTests,
                TestCategory.stagingtest, stagingTests,
                TestCategory.productiontest, productionTests
        ));
    }

    private static List<String> getJsonArray(Cursor cursor, String field) {
        return SlimeStream.fromArray(cursor.field(field), Inspector::asString).collect(Collectors.toList());
    }

    public String version() {
        return version;
    }

    public List<String> getConfiguredTests(TestCategory category) {
        return List.copyOf(configuredTestClasses.get(category));
    }

    @Override
    public String toString() {
        return "TestClassDescriptor{" +
               "configuredTestClasses=" + configuredTestClasses +
               '}';
    }

    public enum TestCategory {systemtest, stagingtest, productiontest}
}
