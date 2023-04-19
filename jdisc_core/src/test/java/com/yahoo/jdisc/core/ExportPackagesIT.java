package com.yahoo.jdisc.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for {@link ExportPackages}.
 *
 * @author gjoranv
 */
public class ExportPackagesIT {


    private static final File expectedExportPackages = new File("src/test/resources" + ExportPackages.PROPERTIES_FILE);

    private static final String JAR_PATH = "target/dependency/";

    // This list must be kept in sync with the list in the export-packages execution in pom.xml.
    private static final List<String> RE_EXPORTED_BUNDLES = Stream.of(
            "guava.jar",
            "guice-no_aop.jar",
            "slf4j-api.jar",
            "slf4j-jdk14.jar",
            "jcl-over-slf4j.jar",
            "log4j-over-slf4j.jar",
            "annotations.jar",
            "config-lib.jar",
            "defaults.jar",
            "vespajlib.jar",
            "vespalog.jar",
            "jaxb-api.jar",
            "jaxb-core.jar",
            "jaxb-impl.jar",
            "javax.activation.jar"
    ).map(f -> JAR_PATH + f).toList();

    @TempDir
    public static File tempFolder;

    @Test
    void exported_packages_are_not_changed_unintentionally() throws Exception {
        Properties actualProperties = getPropertiesFromFile(createPropertiesFile());
        String actualValue = actualProperties.getProperty(ExportPackages.EXPORT_PACKAGES);
        assertNotNull(actualValue, "Missing exportPackages property in file.");

        Properties expectedProperties = getPropertiesFromFile(expectedExportPackages);
        String expectedValue = expectedProperties.getProperty(ExportPackages.EXPORT_PACKAGES);
        assertNotNull(expectedValue, "Missing exportPackages property in file.");

        Set<String> actualPackages = removeNewPackageOnJava20(removeJavaVersion(getPackages(actualValue)));
        Set<String> expectedPackages = removeNewPackageOnJava20(removeJavaVersion(getPackages(expectedValue)));
        if (!actualPackages.equals(expectedPackages)) {
            StringBuilder message = getDiff(actualPackages, expectedPackages);
            message.append("\n\nIf this test fails due to an intentional change in exported packages, run the following command:\n")
                    .append("$ cp jdisc_core/target/classes/exportPackages.properties jdisc_core/src/test/resources/")
                    .append("\n\nNote that removing exported packages usually requires a new major version of Vespa.\n");
            fail(message.toString());
        }
    }

    private static Set<String> removeJavaVersion(Set<String> packages) {
        return packages.stream().map(p -> p.replaceAll(".JavaSE_\\d+", "")).collect(Collectors.toSet());
    }

    private static Set<String> removeNewPackageOnJava20(Set<String> packages) {
        return packages.stream().filter(p -> ! p.contains("java.lang.foreign")).collect(Collectors.toSet());
    }

    private static StringBuilder getDiff(Set<String> actual, Set<String> expected) {
        StringBuilder sb = new StringBuilder();
        Set<String> onlyInActual = onlyInSet1(actual, expected);
        if (! onlyInActual.isEmpty()) {
            sb.append("\nexportPackages.properties contained ")
                    .append(onlyInActual.size())
                    .append(" unexpected packages:\n")
                    .append(onlyInActual.stream().collect(Collectors.joining(",\n  ", " [", "]")));
        }

        Set<String> onlyInExpected = onlyInSet1(expected, actual);
        if (! onlyInExpected.isEmpty()) {
            sb.append("\nexportPackages.properties did not contain ")
                    .append(onlyInExpected.size())
                    .append(" expected packages:\n")
                    .append(onlyInExpected.stream().collect(Collectors.joining(",\n  ", " [", "]")));
        }
        return sb;
    }

    // Returns a sorted set for readability.
    private static Set<String> onlyInSet1(Set<String> set1, Set<String> set2) {
        return set1.stream()
                .filter(s -> ! set2.contains(s))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> getPackages(String propertyValue) {
        return Arrays.stream(propertyValue.split(","))
                .map(String::trim)
                .filter(s -> ! s.isEmpty())
                .collect(Collectors.toSet());
    }

    private static Properties getPropertiesFromFile(File file) throws IOException {
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(file)) {
            properties.load(reader);
        }
        return properties;
    }

    private static File createPropertiesFile() throws IOException {
        File file = Paths.get(tempFolder.toString(), ExportPackages.PROPERTIES_FILE).toFile();
        String[] args = Stream.concat(Stream.of(file.getAbsolutePath()),
                                      RE_EXPORTED_BUNDLES.stream()).toArray(String[]::new);
        ExportPackages.main(args);
        return file;
    }

}
