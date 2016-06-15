// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.filter;

import com.yahoo.log.LogMessage;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Level;
/**
 *
 * @author  <a href="mailto:borud@yahoo-inc.com">Bjorn Borud</a>
 */
public class LevelFilter implements LogFilter {
    private final Set<Level> levels = new HashSet<Level>();

    public void addLevel (Level level) {
        levels.add(level);
    }

    public void removeLevel (Level level) {
        levels.remove(level);
    }

    public boolean isLoggable (LogMessage msg) {
        return levels.contains(msg.getLevel());
    }

    public String description () {
        return "Match specific log levels";
    }
}
