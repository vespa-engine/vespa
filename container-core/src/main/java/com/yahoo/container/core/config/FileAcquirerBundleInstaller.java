// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.config.FileReference;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author gjoranv
 */
public class FileAcquirerBundleInstaller implements BundleInstaller {

    private final FileAcquirer fileAcquirer;

    public FileAcquirerBundleInstaller(FileAcquirer fileAcquirer) {
        this.fileAcquirer = fileAcquirer;
    }

    @Override
    public List<Bundle> installBundles(FileReference reference, Osgi osgi) throws InterruptedException {
        File file = fileAcquirer.waitFor(reference, 7, TimeUnit.DAYS);
        return osgi.install(file.getAbsolutePath());
    }

}
