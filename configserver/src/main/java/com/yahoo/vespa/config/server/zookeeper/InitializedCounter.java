// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.curator.recipes.CuratorCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A counter that sets its initial value to the number of apps in zookeeper if no counter value is set. Subclass
 * this to get that behavior.
 *
 * @author Ulf Lilleengen
 */
public class InitializedCounter {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(InitializedCounter.class.getName());
    protected final CuratorCounter counter;
    private final String sessionsDirPath;

    public InitializedCounter(ConfigCurator configCurator, String counterPath, String sessionsDirPath) {
        this.sessionsDirPath = sessionsDirPath;
        this.counter = new CuratorCounter(configCurator.curator(), counterPath);
        initializeCounterValue(getLatestSessionId(configCurator, sessionsDirPath));
    }

    private void initializeCounterValue(Long latestSessionId) {
        log.log(LogLevel.DEBUG, "path=" + sessionsDirPath + ", current=" + latestSessionId);
        if (latestSessionId != null) {
            counter.initialize(latestSessionId);
        } else {
            counter.initialize(1);
        }
    }

    /**
     * Checks if an application exists in Zookeeper.
     *
     * @return true, if an application exists, false otherwise
     */
    private static boolean applicationExists(ConfigCurator configCurator, String appsPath) {
        // TODO Need to try and catch now since interface should not expose Zookeeper exceptions
        try {
            return configCurator.exists(appsPath);
        } catch (Exception e) {
            log.log(LogLevel.WARNING, e.getMessage());
            return false;
        }
    }

    /**
     * Returns the application generation for the most recently deployed application from ZK,
     * or null if no application has been deployed yet
     *
     * @return generation of the latest deployed application
     */
    private static Long getLatestSessionId(ConfigCurator configCurator, String appsPath) {
        if (!applicationExists(configCurator, appsPath)) return null;
        Long newestGeneration = null;
        try {
            if (!getDeployedApplicationGenerations(configCurator, appsPath).isEmpty()) {
                newestGeneration = Collections.max(getDeployedApplicationGenerations(configCurator, appsPath));
            }
        } catch (Exception e) {
            log.log(LogLevel.WARNING, "Could not get newest application generation from Zookeeper");
        }
        return newestGeneration;
    }

    private static List<Long> getDeployedApplicationGenerations(ConfigCurator configCurator, String appsPath) {
        ArrayList<Long> generations = new ArrayList<>();
        try {
            List<String> stringGenerations = configCurator.getChildren(appsPath);
            if (stringGenerations != null && !(stringGenerations.isEmpty())) {
                for (String s : stringGenerations) {
                    generations.add(Long.parseLong(s));
                }
            }
        } catch (RuntimeException e) {
            log.log(LogLevel.WARNING, "Could not get application generations from Zookeeper");
        }
        return generations;
    }
}
