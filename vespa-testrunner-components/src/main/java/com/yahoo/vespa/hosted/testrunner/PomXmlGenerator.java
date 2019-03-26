package com.yahoo.vespa.hosted.testrunner;

import com.yahoo.vespa.defaults.Defaults;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates a pom.xml file that sets up build profile to test against the provided
 * jar artifacts.
 *
 * @author valerijf
 */
public class PomXmlGenerator {
    private static final String PROPERTY_TEMPLATE =
            "        <%ARTIFACT_ID%.path>%JAR_PATH%</%ARTIFACT_ID%.path>\n";
    private static final String TEST_ARTIFACT_GROUP_ID = "com.yahoo.vespa.testrunner.test";
    private static final String DEPENDENCY_TEMPLATE =
            "        <dependency>\n" +
            "            <groupId>" + TEST_ARTIFACT_GROUP_ID + "</groupId>\n" +
            "            <artifactId>%ARTIFACT_ID%</artifactId>\n" +
            "            <scope>system</scope>\n" +
            "            <type>test-jar</type>\n" +
            "            <version>test</version>\n" +
            "            <systemPath>${%ARTIFACT_ID%.path}</systemPath>\n" +
            "        </dependency>\n";
    private static final String DEPENDENCY_TO_SCAN_TEMPLATE =
            "                        <dependency>" + TEST_ARTIFACT_GROUP_ID + ":%ARTIFACT_ID%</dependency>\n";
    private static final String POM_XML_TEMPLATE =
            "<?xml version=\"1.0\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0                              http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>com.yahoo.vespa</groupId>\n" +
            "    <artifactId>tester-application</artifactId>\n" +
            "    <version>1.0.0</version>\n" +
            "\n" +
            "    <properties>\n" +
            "        <maven_version>4.12</maven_version>\n" +
            "        <surefire_version>2.22.0</surefire_version>\n" +
            "%PROPERTIES%" +
            "    </properties>\n" +
            "\n" +
            "    <dependencies>\n" +
            "        <dependency>\n" +
            "            <groupId>junit</groupId>\n" +
            "            <artifactId>junit</artifactId>\n" +
            "            <version>${maven_version}</version>\n" +
            "            <scope>test</scope>\n" +
            "        </dependency>\n" +
            "%DEPENDENCIES%" +
            "    </dependencies>\n" +
            "\n" +
            "    <build>\n" +
            "        <plugins>\n" +
            "            <plugin>\n" +
            "                <groupId>org.apache.maven.plugins</groupId>\n" +
            "                <artifactId>maven-surefire-plugin</artifactId>\n" +
            "                <version>${surefire_version}</version>\n" +
            "                <configuration>\n" +
            "                    <dependenciesToScan>\n" +
            "%DEPENDENCIES_TO_SCAN%" +
            "                    </dependenciesToScan>\n" +
            "                    <groups>%GROUPS%</groups>\n" +
            "                    <excludedGroups>com.yahoo.vespa.tenant.systemtest.base.impl.EmptyExcludeGroup.class</excludedGroups>\n" +
            "                    <excludes>\n" +
            "                        <exclude>%GROUPS%</exclude>\n" +
            "                    </excludes>\n" +
            "                    <reportsDirectory>${env.TEST_DIR}</reportsDirectory>\n" +
            "                    <redirectTestOutputToFile>false</redirectTestOutputToFile>\n" +
            "                    <environmentVariables>\n" +
            "                        <LD_LIBRARY_PATH>" + Defaults.getDefaults().underVespaHome("lib64") + "</LD_LIBRARY_PATH>\n" +
            "                    </environmentVariables>\n" +
            "                </configuration>\n" +
            "            </plugin>\n" +
            "            <plugin>\n" +
            "                <groupId>org.apache.maven.plugins</groupId>\n" +
            "                <artifactId>maven-surefire-report-plugin</artifactId>\n" +
            "                <version>${surefire_version}</version>\n" +
            "                <configuration>\n" +
            "                    <reportsDirectory>${env.TEST_DIR}</reportsDirectory>\n" +
            "                </configuration>\n" +
            "            </plugin>\n" +
            "        </plugins>\n" +
            "    </build>\n" +
            "</project>\n";

    static String generatePomXml(TestProfile testProfile, List<Path> artifacts, Path testArtifact) {
        String properties = artifacts.stream()
                .map(path -> PROPERTY_TEMPLATE
                        .replace("%ARTIFACT_ID%", path.getFileName().toString())
                        .replace("%JAR_PATH%", path.toString()))
                .collect(Collectors.joining());
        String dependencies = artifacts.stream()
                .map(path -> DEPENDENCY_TEMPLATE
                        .replace("%ARTIFACT_ID%", path.getFileName().toString()))
                .collect(Collectors.joining());
        String dependenciesToScan =
                DEPENDENCY_TO_SCAN_TEMPLATE
                        .replace("%ARTIFACT_ID%", testArtifact.getFileName().toString());

        return POM_XML_TEMPLATE
                .replace("%PROPERTIES%", properties)
                .replace("%DEPENDENCIES_TO_SCAN%", dependenciesToScan)
                .replace("%DEPENDENCIES%", dependencies)
                .replace("%GROUPS%", testProfile.group());
    }

    private PomXmlGenerator() {}
}
