// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.application.api.DeployLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bratseth
 */
public class TestableDeployLogger implements DeployLogger {

    private static final Logger log = Logger.getLogger("DeployLogger");

    public List<String> warnings = new ArrayList<>();
    public List<String> info = new ArrayList<>();

    @Override
    public final void log(Level level, String message) {
        log.log(level, message);
        if (level.equals(Level.WARNING))
            warnings.add(message);
        if (level.equals(Level.INFO))
            info.add(message);
    }

}
