// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import java.util.jar.JarFile;

/**
 * Source specifying config as a jar file entry
 * @author Vegard Havdal
 */
public class JarSource implements ConfigSource {
    private final String path;
    private final JarFile jarFile;

    /**
     * Creates a new jar source
     * @param jarFile the jar file to use as a source
     * @param path the path within the jar file, or null to use the default config/
     */
    public JarSource(JarFile jarFile, String path) {
        this.path = path;
        this.jarFile = jarFile;
    }

    public JarFile getJarFile() {
        return jarFile;
    }

    public String getPath() {
        return path;
    }

}
