// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.SystemName;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;

/**
 * @author freva
 */
public class IntegerationStepRunnerTest {

    @Test
    public void generates_correct_services_xml_test() {
        assertFile("test_runner_services.xml-cd", new String(InternalStepRunner.servicesXml(SystemName.cd)));
    }

    private void assertFile(String resourceName, String actualContent) {
        try {
            Path path = Paths.get(getClass().getClassLoader().getResource(resourceName).getPath());
            String expectedContent = new String(Files.readAllBytes(path));
            assertEquals(expectedContent, actualContent);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
