// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.Module;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.OsgiFramework;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author Simon Thoresen
 */
public class Main {

    static OsgiFramework newOsgiFramework() {
        String cachePath = System.getProperty("jdisc.cache.path");
        if (cachePath == null) {
            throw new IllegalStateException("System property 'jdisc.cache.path' not set.");
        }
        FelixParams params = new FelixParams()
                .setCachePath(cachePath)
                .setLoggerEnabled(Boolean.valueOf(System.getProperty("jdisc.logger.enabled", "true")));
        for (String str : ContainerBuilder.safeStringSplit(System.getProperty("jdisc.export.packages"), ",")) {
            params.exportPackage(str);
        }
        return new FelixFramework(params);
    }

    static Iterable<Module> newConfigModule() {
        String configFile = System.getProperty("jdisc.config.file");
        if (configFile == null) {
            return Collections.emptyList();
        }
        Module configModule;
        try {
            configModule = ApplicationConfigModule.newInstanceFromFile(configFile);
        } catch (IOException e) {
            throw new IllegalStateException("Exception thrown while reading config file '" + configFile + "'.", e);
        }
        return Arrays.asList(configModule);
    }

}
