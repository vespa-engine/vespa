// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.apache.felix.framework.cache.BundleCache;
import org.osgi.framework.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public class FelixParams {

    private final StringBuilder exportPackages = new StringBuilder(ExportPackages.readExportProperty());
    private String cachePath = null;
    private boolean loggerEnabled = true;

    public FelixParams exportPackage(String pkg) {
        exportPackages.append(",").append(pkg);
        return this;
    }

    public FelixParams setCachePath(String cachePath) {
        this.cachePath = cachePath;
        return this;
    }

    public String getCachePath() {
        return cachePath;
    }

    public FelixParams setLoggerEnabled(boolean loggerEnabled) {
        this.loggerEnabled = loggerEnabled;
        return this;
    }

    public boolean isLoggerEnabled() {
        return loggerEnabled;
    }

    public Map<String, String> toConfig() {
        Map<String, String> ret = new HashMap<>();
        ret.put(BundleCache.CACHE_ROOTDIR_PROP, cachePath);
        ret.put(Constants.FRAMEWORK_SYSTEMPACKAGES, exportPackages.toString());
        ret.put(Constants.SUPPORTS_BOOTCLASSPATH_EXTENSION, "true");
        ret.put(Constants.FRAMEWORK_BOOTDELEGATION, "com.yourkit.runtime,com.yourkit.probes,com.yourkit.probes.builtin,com.singularity.*");
        return ret;
    }
}
