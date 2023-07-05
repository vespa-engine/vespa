// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.config.provision.ProvisionLogger;

import java.util.logging.Level;

/**
 * @author bratseth
 */
public class SystemOutProvisionLogger implements ProvisionLogger {

    @Override
    public void log(Level level, String message) {
        System.out.println("ProvisionLogger system " + level + ": " + message);
    }

    @Override
    public void logApplicationPackage(Level level, String message) {
        System.out.println("ProvisionLogger application " + level + ": " + message);
    }

}
