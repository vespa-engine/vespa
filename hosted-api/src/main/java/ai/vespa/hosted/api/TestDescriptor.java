// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeStream;
import com.yahoo.slime.SlimeUtils;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * @author mortent
 */
public class TestDescriptor {
    public static final String DEFAULT_FILENAME = "META-INF/ai.vespa/testDescriptor.json";
    public static final String CURRENT_VERSION = "1.0";

    private static final String JSON_FIELD_VERSION = "version";
    private static final String JSON_FIELD_CONFIGURED_TESTS = "configuredTests";
    private static final String JSON_FIELD_SYSTEM_TESTS = "systemTests";
    private static final String JSON_FIELD_STAGING_TESTS = "stagingTests";
    private static final String JSON_FIELD_STAGING_SETUP_TESTS = "stagingSetupTests";
    private static final String JSON_FIELD_PRODUCTION_TESTS = "productionTests";

    private final Map<TestCategory, List<String>> configuredTestClasses;
    private final String version;

    private TestDescriptor(String version, Map<TestCategory, List<String>> configuredTestClasses) {
        this.version = version;
        this.configuredTestClasses = configuredTestClasses;
    }

    public static TestDescriptor fromJsonString(String testDescriptor) {
        var slime = SlimeUtils.jsonToSlime(testDescriptor);
        var root = slime.get();
        var version = root.field(JSON_FIELD_VERSION).asString();
        var testRoot = root.field(JSON_FIELD_CONFIGURED_TESTS);
        var systemTests = getJsonArray(testRoot, JSON_FIELD_SYSTEM_TESTS);
        var stagingTests = getJsonArray(testRoot, JSON_FIELD_STAGING_TESTS);
        var stagingSetupTests = getJsonArray(testRoot, JSON_FIELD_STAGING_SETUP_TESTS);
        var productionTests = getJsonArray(testRoot, JSON_FIELD_PRODUCTION_TESTS);
        return new TestDescriptor(version, toMap(systemTests, stagingTests, stagingSetupTests, productionTests));
    }

    public static TestDescriptor from(
            String version, List<String> systemTests, List<String> stagingTests, List<String> stagingSetupTests, List<String> productionTests) {
        return new TestDescriptor(version, toMap(systemTests, stagingTests, stagingSetupTests, productionTests));
    }

    private static Map<TestCategory, List<String>> toMap(
            List<String> systemTests, List<String> stagingTests, List<String> stagingSetupTests, List<String> productionTests) {
        return Map.of(
                TestCategory.systemtest, systemTests,
                TestCategory.stagingtest, stagingTests,
                TestCategory.stagingsetuptest, stagingSetupTests,
                TestCategory.productiontest, productionTests
        );
    }

    private static List<String> getJsonArray(Cursor cursor, String field) {
        return SlimeStream.fromArray(cursor.field(field), Inspector::asString).toList();
    }

    public String version() {
        return version;
    }

    public List<String> getConfiguredTests(TestCategory category) {
        return List.copyOf(configuredTestClasses.get(category));
    }

    public String toJson() {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString(JSON_FIELD_VERSION, this.version);
        Cursor tests = root.setObject(JSON_FIELD_CONFIGURED_TESTS);
        addJsonArrayForTests(tests, JSON_FIELD_SYSTEM_TESTS, TestCategory.systemtest);
        addJsonArrayForTests(tests, JSON_FIELD_STAGING_TESTS, TestCategory.stagingtest);
        addJsonArrayForTests(tests, JSON_FIELD_PRODUCTION_TESTS, TestCategory.productiontest);
        addJsonArrayForTests(tests, JSON_FIELD_STAGING_SETUP_TESTS, TestCategory.stagingsetuptest);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        uncheck(() -> new JsonFormat(/*compact*/false).encode(out, slime));
        return out.toString();
    }

    private void addJsonArrayForTests(Cursor testsRoot, String fieldName, TestCategory category) {
        List<String> tests = configuredTestClasses.get(category);
        if (tests.isEmpty()) return;
        Cursor cursor = testsRoot.setArray(fieldName);
        tests.forEach(cursor::addString);
    }

    @Override
    public String toString() {
        return "TestClassDescriptor{" +
               "configuredTestClasses=" + configuredTestClasses +
               '}';
    }

    public enum TestCategory {systemtest, stagingsetuptest, stagingtest, productiontest}
}
