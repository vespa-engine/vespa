// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.config.FileReference;
import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;

import java.util.List;

/**
 * @author gjoranv
 */
class TestBundleInstaller implements BundleInstaller {

    @Override
    public List<Bundle> installBundles(FileReference reference, Osgi osgi) {
        return osgi.install(reference.value());
    }

}
