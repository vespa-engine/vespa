// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import com.yahoo.config.provision.ProvisionLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * A logger which remembers all messages logged in addition to writing them to standard out.
 *
 * @author bratseth
 */
public class InMemoryProvisionLogger implements ProvisionLogger {

    private final List<String> systemLog = new ArrayList<>();
    private final List<String> applicationLog = new ArrayList<>();

    @Override
    public void log(Level level, String message) {
        System.out.println("ProvisionLogger system " + level + ": " + message);
        systemLog.add(level + ": " + message);
    }

    @Override
    public void logApplicationPackage(Level level, String message) {
        System.out.println("ProvisionLogger application " + level + ": " + message);
        applicationLog.add(level + ": " + message);
    }

    public List<String> systemLog() { return systemLog; }
    public List<String> applicationLog() { return applicationLog; }

}
