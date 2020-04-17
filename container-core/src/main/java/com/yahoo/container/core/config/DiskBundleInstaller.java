// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.config.FileReference;
import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;

import java.io.File;
import java.util.List;

import static com.yahoo.container.core.BundleLoaderProperties.DISK_BUNDLE_PREFIX;

/**
 * @author gjoranv
 */
public class DiskBundleInstaller implements BundleInstaller {

    @Override
    public List<Bundle> installBundles(FileReference reference, Osgi osgi) {
        assert(reference.value().startsWith(DISK_BUNDLE_PREFIX));
        String referenceFileName = reference.value().substring(DISK_BUNDLE_PREFIX.length());

        File file = new File(referenceFileName);
        if ( ! file.exists()) {
            throw new IllegalArgumentException("Reference '" + reference.value() + "' not found on disk.");
        }

        return osgi.install(file.getAbsolutePath());
    }

}
