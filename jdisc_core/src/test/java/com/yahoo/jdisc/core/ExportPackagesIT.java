package com.yahoo.jdisc.core;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private static final List<String> newPackagesInJava21 = List.of("java.lang.foreign");
    private static final List<String> removedPackagesInJava21 = List.of("com.sun.jarsigner");

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("([^;,]+);\\s*version=\"([^\"]*)\"(?:,\\s*([^;,]+);\\s*uses:=\"([^\"]*)\")?");

    record PackageInfo(String packageName, String version, List<String> clauses) implements Comparable<PackageInfo> {

        PackageInfo withoutVersion() {
            return new PackageInfo(packageName, "", clauses);
        }

        @Override
        public String toString() {
            return packageName + ":" + version;
        }

        @Override
        public int compareTo(PackageInfo o) {
            int pkg = packageName.compareTo(o.packageName);
            return (pkg != 0) ? pkg : version.compareTo(o.version);
        }
    }

    record PackageSet(List<PackageInfo> packages) {
        PackageSet removeJavaVersion() {
            return new PackageSet(packages.stream()
                                          .map(p -> p.version.contains(".JavaSE_") ? p.withoutVersion() : p)
                                          .toList());
        }

        PackageSet addPackages(List<String> packageNames) {
            var newPackages = packageNames.stream()
                    .map(p -> new PackageInfo(p, "", List.of()))
                    .toList();
            return new PackageSet(new ArrayList<>(packages) {{ addAll(newPackages); }});
        }

        PackageSet removePackages(List<String> packageNames) {
            return new PackageSet(packages.stream()
                                          .filter(p -> ! packageNames.contains(p.packageName))
                                          .toList());
        }

        boolean isEquivalentTo(PackageSet other) {
            return new HashSet<>(this.packages).equals(new HashSet<>(other.packages));
        }

        PackageSet minus(PackageSet other) {
            Set<PackageInfo> diff = Sets.difference(new HashSet<>(this.packages), new HashSet<>(other.packages));
            return new PackageSet(diff.stream().sorted().toList());
        }

        @Override
        public String toString() {
            return packages.stream().map(PackageInfo::toString)
                    .collect(Collectors.joining(",\n  ", " [", "]"));
        }
    }

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

        var expectedPackages = parsePackages(expectedValue).removeJavaVersion();
               // .removePackages(removedPackagesInJava21)
               // .addPackages(newPackagesInJava21);
        var actualPackages = parsePackages(actualValue).removeJavaVersion();

        if (!actualPackages.isEquivalentTo(expectedPackages)) {
            StringBuilder message = getDiff(actualPackages, expectedPackages);
            message.append("\n\nIf this test fails due to an intentional change in exported packages, run the following command:\n")
                    .append("$ cp jdisc_core/target/classes/exportPackages.properties jdisc_core/src/test/resources/")
                    .append("\n\nNote that removing exported packages usually requires a new major version of Vespa.\n");
            fail(message.toString());
        }
        // TODO: check that actualValue equals expectedValue. Problem is that exportPackages.properties is non-deterministic.
    }

    private static StringBuilder getDiff(PackageSet actual, PackageSet expected) {
        StringBuilder sb = new StringBuilder();

        var onlyInActual = actual.minus(expected);
        if (! onlyInActual.packages().isEmpty()) {
            sb.append("\nexportPackages.properties contained ")
                    .append(onlyInActual.packages.size())
                    .append(" unexpected packages:\n")
                    .append(onlyInActual);
        }

        var onlyInExpected = expected.minus(actual);
        if (! onlyInExpected.packages.isEmpty()) {
            sb.append("\nexportPackages.properties did not contain ")
                    .append(onlyInExpected.packages.size())
                    .append(" expected packages:\n")
                    .append(onlyInExpected);
        }
        return sb;
    }

    public static PackageSet parsePackages(String input) {
        List<PackageInfo> packages = new ArrayList<>();

        Matcher matcher = PACKAGE_PATTERN.matcher(input);
        while (matcher.find()) {
            String packageName = matcher.group(1).trim();
            String version = matcher.group(2).trim();
            String dependencyPackage = matcher.group(3);
            String dependencyClause = matcher.group(4);

            List<String> clauses = new ArrayList<>();
            if (dependencyPackage != null && dependencyClause != null) {
                clauses.add(dependencyPackage.trim() + ";" + dependencyClause.trim());
            }

            PackageInfo packageInfo = new PackageInfo(packageName, version, clauses);
            packages.add(packageInfo);
        }
        return new PackageSet(packages);
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
