// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import com.yahoo.log.impl.LogUtils;
import java.util.logging.Level;

/**
 * a levelcontroller that just implements a simple default
 * (possibly controlled by a system property or environment)
 * Should only be used internally in the log library
 */
@SuppressWarnings("deprecation")
class DefaultLevelController implements LevelController {
    private String levelstring;
    private Level levelLimit = LogLevel.EVENT;

    DefaultLevelController(String env) {
        if (LogUtils.empty(env)) {
            env = "all -debug -spam";
        }

        //level string is: fatal, error, warning, config, info, event, debug, spam
        if (env.equals("all")) {
            levelLimit = LogLevel.ALL;
            levelstring = "  ON  ON  ON  ON  ON  ON  ON  ON";
        } else {
            StringBuilder sb = new StringBuilder();
            for (Level level : LogLevel.getLevels().values()) {
                String levelName = level.getName();
                if (hasNegWord(levelName, env) || (!hasWord("all", env) && !hasWord(levelName, env))) {
                    sb.append(" OFF");
                } else {
                    sb.append("  ON");
                    if ((level.intValue() < levelLimit.intValue())) {
                        levelLimit = level;
                    }
                }
            }
            levelstring = sb.toString();
        }
        // System.err.println("default level controller levelstring: "+levelstring);
    }

    private boolean hasWord(String levelName, String inputLevels) {
        return inputLevels.contains(levelName.toLowerCase());
    }

    private boolean hasNegWord(String levelName, String inputLevels) {
        int pos = inputLevels.indexOf(levelName.toLowerCase());
        if (pos > 0) {
            String c = inputLevels.substring(pos - 1, pos);
            return (c != null && c.equals("-"));
        } else {
            return false;
        }
    }

    public String getOnOffString() {
        return levelstring;
    }

    public Level getLevelLimit() {
        return levelLimit;
    }
    public boolean shouldLog(Level level) {
        return (level.intValue() >= levelLimit.intValue());
    }
    public void checkBack() { }
}
