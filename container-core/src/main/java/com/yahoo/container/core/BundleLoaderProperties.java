// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core;

/**
 * @author gjoranv
 * @since 5.46
 */
public interface BundleLoaderProperties {
    // TODO: This should be removed. The prefix is used to separate the bundles in BundlesConfig
    // into those that are transferred with filedistribution and those that are preinstalled
    // on disk. Instead, the model should have put them in two different configs. I.e. create a new
    // config 'preinstalled-bundles.def'.
    public static final String DISK_BUNDLE_PREFIX = "file:";
}
