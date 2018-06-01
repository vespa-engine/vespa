// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.impl;

import com.yahoo.text.Utf8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class StandaloneContainerRunner {
    public static Path createApplicationPackage(String servicesXml) {
        try {
            return createApplicationDirectory(servicesXml);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path createApplicationDirectory(String servicesXml) throws IOException {
        Path applicationDir = Files.createTempDirectory("application");
        Path servicesXmlFile = applicationDir.resolve("services.xml");
        String content = servicesXml;
        
        if (!servicesXml.startsWith("<?xml")) {
            content = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" + servicesXml;
        }
        Files.write(servicesXmlFile, Utf8.toBytes(content));
        return applicationDir;
    }
}
