// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import java.nio.file.Path;
import java.util.Map;

/**
 * Creates the environment variables needed to enable landlock sandboxing of an external binary,
 * restricting it to read-only access to a fixed set of system paths plus the given paths.
 *
 * @author hmusum
 */
public class Landlock {

    private Landlock() {}

    /**
     * Returns the environment needed to enable landlock with read-only access to the given paths.
     *
     * @throws IllegalArgumentException if landlock is enabled and a path is not absolute, is not normalized,
     *                                  or contains ':' or ',' (which are separators in VESPA_LANDLOCK_PATHS)
     */
    public static Map<String, String> createEnv(boolean enableLandlock, String... readOnlyPaths) {
        if ( ! enableLandlock) {
            return Map.of();
        }

        StringBuilder paths = new StringBuilder("/dev,ro:/sys,ro:/proc/self,ro:/proc/cpuinfo,ro");
        for (String path : readOnlyPaths) {
            validate(path);
            paths.append(":").append(path).append(",ro");
        }

        return Map.of("VESPA_ENABLE_LANDLOCK", "true",
                      "VESPA_LANDLOCK_PATHS", paths.toString());
    }

    private static void validate(String path) {
        // ':' separates entries and ',' separates a path from its access flags, disallow both in paths
        if (path.contains(":") || path.contains(","))
            throw new IllegalArgumentException("Landlock path must not contain ':' or ',': '" + path + "'");
        Path p = Path.of(path);
        if ( ! p.isAbsolute())
            throw new IllegalArgumentException("Landlock path must be absolute: '" + path + "'");
        if ( ! p.equals(p.normalize()))
            throw new IllegalArgumentException("Landlock path must not contain '.' or '..' elements: '" + path + "'");
    }

}
