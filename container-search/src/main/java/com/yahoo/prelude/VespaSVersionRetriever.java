// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude;

import java.io.IOException;
import java.util.jar.Manifest;

/**
 * Retrieves Vespa-Version from the manifest file.
 *
 * @author tonytv
 */
public class VespaSVersionRetriever {

    public static String getVersion() {
        return version;
    }

    private static String version = retrieveVersion();

    private static String retrieveVersion() {
        try {
            Manifest manifest = new Manifest(VespaSVersionRetriever.class.getResourceAsStream("/META-INF/MANIFEST.MF"));
            manifest.getMainAttributes().entrySet();
            return manifest.getMainAttributes().getValue("Vespa-Version");
        } catch (IOException e) {
            return "not available.";
        }
    }
}
