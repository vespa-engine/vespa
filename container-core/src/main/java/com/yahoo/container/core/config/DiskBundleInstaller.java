// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;

import java.io.File;
import java.util.List;

/**
 * @author gjoranv
 */
public class DiskBundleInstaller {

    public List<Bundle> installBundles(String bundlePath, Osgi osgi) {
        File file = new File(bundlePath);
        if ( ! file.exists()) {
            throw new IllegalArgumentException("Bundle file '" + bundlePath + "' not found on disk.");
        }

        return osgi.install(file.getAbsolutePath());
    }

}
