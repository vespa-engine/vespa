// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.config.FileReference;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Retrieves bundles with file distribution, and installs them to the OSGi framework.
 *
 * @author gjoranv
 */
public class FileAcquirerBundleInstaller {

    private static final Logger log = Logger.getLogger(FileAcquirerBundleInstaller.class.getName());

    private final FileAcquirer fileAcquirer;

    public FileAcquirerBundleInstaller(FileAcquirer fileAcquirer) {
        this.fileAcquirer = fileAcquirer;
    }

    public List<Bundle> installBundles(FileReference reference, Osgi osgi) throws InterruptedException {
        File file = fileAcquirer.waitFor(reference, 7, TimeUnit.DAYS);

        if (notReadable(file)) {
            // Wait a few sec in case FileAcquirer returns right before the file is actually ready.
            // This happened on rare occasions due to a (fixed) bug in file distribution.
            log.warning("Unable to open bundle file with reference '" + reference + "'. Waiting for up to 5 sec.");
            int retries = 0;
            while (notReadable(file) && retries < 10) {
                Thread.sleep(500);
                retries++;
            }
            if (notReadable(file)) {
                com.yahoo.protect.Process.logAndDie("Shutting down - unable to read bundle file with reference '" +
                                                    reference + "' and path " + file.getAbsolutePath());
            }
        }

        return osgi.install(file.getAbsolutePath());
    }

    public boolean hasFileDistribution() {
        return fileAcquirer != null;
    }

    private static boolean notReadable(File file) {
        return ! Files.isReadable(file.toPath());
    }

}
