// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import java.util.logging.Level;

import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.recipes.CuratorCounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * A counter that sets its initial value to the number of apps in zookeeper if no counter value is set. Subclass
 * this to get that behavior.
 *
 * @author Ulf Lilleengen
 */
public class InitializedCounter {

    private static final Logger log = java.util.logging.Logger.getLogger(InitializedCounter.class.getName());
    final CuratorCounter counter;
    private final Path sessionsDirPath;

    InitializedCounter(Curator curator, Path counterPath, Path sessionsDirPath) {
        this.sessionsDirPath = sessionsDirPath;
        this.counter = new CuratorCounter(curator, counterPath);
        initializeCounterValue(getLatestSessionId(curator, sessionsDirPath));
    }

    private void initializeCounterValue(Long latestSessionId) {
        log.log(Level.FINE, () -> "path=" + sessionsDirPath + ", current=" + latestSessionId);
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
    private static boolean applicationExists(Curator curator, Path appsPath) {
        return curator.exists(appsPath);
    }

    /**
     * Returns the application generation for the most recently deployed application from ZK,
     * or null if no application has been deployed yet
     *
     * @return generation of the latest deployed application
     */
    private static Long getLatestSessionId(Curator curator, Path appsPath) {
        if (!applicationExists(curator, appsPath)) return null;
        Long newestGeneration = null;
        try {
            if (!getDeployedApplicationGenerations(curator, appsPath).isEmpty()) {
                newestGeneration = Collections.max(getDeployedApplicationGenerations(curator, appsPath));
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not get newest application generation from Zookeeper");
        }
        return newestGeneration;
    }

    private static List<Long> getDeployedApplicationGenerations(Curator curator, Path appsPath) {
        ArrayList<Long> generations = new ArrayList<>();
        try {
            List<String> stringGenerations = curator.getChildren(appsPath);
            if (stringGenerations != null && !(stringGenerations.isEmpty())) {
                for (String s : stringGenerations) {
                    generations.add(Long.parseLong(s));
                }
            }
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Could not get application generations from Zookeeper");
        }
        return generations;
    }
}
