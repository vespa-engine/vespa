// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * This is the interface for controlling the log level of a
 * component logger.  This hides the actual controlling
 * mechanism.
 *
 * @author arnej27959
 *
 */

/**
 * @author arnej27959
 **/
package com.yahoo.log;

import java.util.logging.Level;

public interface LevelController {

    /**
     * should we actually publish a log message with the given Level now?
     **/
    public boolean shouldLog(Level level);

    /**
     * return a string suitable for printing in a logctl file.
     * the string must be be 4 * 8 characters, where each group
     * of 4 characters is either "  ON" or " OFF".
     **/
    public String getOnOffString();

    /**
     * check the current state of logging and reflect it into the
     * associated Logger instance, if available.
     **/
    public void checkBack();
    public Level getLevelLimit();
}
