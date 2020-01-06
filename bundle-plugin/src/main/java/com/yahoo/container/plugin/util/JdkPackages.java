// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util;

import java.net.URL;

/**
 * @author gjoranv
 */
public class JdkPackages {

    /**
     * Returns a boolean indicating (via best effort) if the given package is part of the JDK.
     */
    public static boolean isJdkPackage(String pkg) {
        return hasJdkExclusivePrefix(pkg)
                || isResourceInPlatformClassLoader(pkg); // TODO: must be a class, not a package, due to module encapsulation
    }

    private static boolean isResourceInPlatformClassLoader(String klass) {
        String klassAsPath = klass.replaceAll("\\.", "/") + ".class";
        URL resource = getPlatformClassLoader().getResource(klassAsPath);
        return !(resource == null);
    }

    private static ClassLoader getPlatformClassLoader() {
        ClassLoader platform = JdkPackages.class.getClassLoader().getParent();

        // Will fail upon changes in classloader hierarchy between JDK versions
        assert (platform.getName().equals("platform"));

        return platform;
    }

    private static boolean hasJdkExclusivePrefix(String pkg) {
        return pkg.startsWith("java.")
                || pkg.startsWith("sun.");
    }

}

