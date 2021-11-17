// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.testrunner;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author valerijf
 */
public class PomXmlGeneratorTest {

    @Test
    public void write_system_tests_pom_xml() throws IOException {
        List<Path> artifacts = Arrays.asList(
                Paths.get("components/my-comp.jar"),
                Paths.get("main.jar"));

        String actual = PomXmlGenerator.generatePomXml(TestProfile.SYSTEM_TEST, artifacts, artifacts.get(1));
        assertFile("/pom.xml_system_tests", actual);
    }

    private void assertFile(String resourceFile, String actual) throws IOException {
        String expected = new String(this.getClass().getResourceAsStream(resourceFile).readAllBytes());
        assertEquals(resourceFile, expected, actual);
    }

}
