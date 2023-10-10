// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import java.io.File;
import java.io.IOException;

/**
 * @author Simon Thoresen Hult
 */
class BundleLocationResolver {

    static final String BUNDLE_PATH = System.getProperty("jdisc.bundle.path", ".") + "/";

    public static String resolve(String bundleLocation) {
        bundleLocation = expandSystemProperties(bundleLocation);
        bundleLocation = bundleLocation.trim();
        String scheme = getLocationScheme(bundleLocation);
        if (scheme == null) {
            bundleLocation = "file:" + getCanonicalPath(BUNDLE_PATH + bundleLocation);
        } else if (scheme.equalsIgnoreCase("file")) {
            bundleLocation = "file:" + getCanonicalPath(bundleLocation.substring(5));
        }
        return bundleLocation;
    }

    private static String expandSystemProperties(String str) {
        StringBuilder ret = new StringBuilder();
        int prev = 0;
        while (true) {
            int from = str.indexOf("${", prev);
            if (from < 0) {
                break;
            }
            ret.append(str.substring(prev, from));
            prev = from;

            int to = str.indexOf("}", from);
            if (to < 0) {
                break;
            }
            ret.append(System.getProperty(str.substring(from + 2, to), ""));
            prev = to + 1;
        }
        if (prev >= 0) {
            ret.append(str.substring(prev));
        }
        return ret.toString();
    }

    private static String getCanonicalPath(String path) {
        try {
            return new File(path).getCanonicalPath();
        } catch (IOException e) {
            return path;
        }
    }

    private static String getLocationScheme(String bundleLocation) {
        char[] arr = bundleLocation.toCharArray();
        for (int i = 0; i < arr.length; ++i) {
            if (arr[i] == ':' && i > 0) {
                return bundleLocation.substring(0, i);
            }
            if (!Character.isLetterOrDigit(arr[i])) {
                return null;
            }
        }
        return null;
    }
}
