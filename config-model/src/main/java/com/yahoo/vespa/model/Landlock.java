// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import java.util.Map;

/**
 * Creates the environment variables needed to enable landlock sandboxing of an external binary,
 * restricting it to read-only access to a fixed set of system paths plus the given paths.
 *
 * @author hmusum
 */
public class Landlock {

    private Landlock() {}

    public static Map<String, String> createEnv(boolean enableLandlock, String... readOnlyPaths) {
        if ( ! enableLandlock) {
            return Map.of();
        }

        StringBuilder paths = new StringBuilder("/dev,ro:/sys,ro:/proc/self,ro:/proc/cpuinfo,ro");
        for (String path : readOnlyPaths) {
            paths.append(":").append(path).append(",ro");
        }

        return Map.of("VESPA_ENABLE_LANDLOCK", "true",
                      "VESPA_LANDLOCK_PATHS", paths.toString());
    }

}
