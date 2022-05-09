package com.yahoo.log.impl;

import com.yahoo.log.LogSetup;

/**
 * Sets up Vespa logging. Call a setup method to set up this.
 *
 * @author baldersheim
 */
public class InitializeLog {
    static {
        LogSetup.initVespaLogging("unused-default");
    }

    /**
     * Do not delete this method even if it's empty.
     * Calling this methods forces this class to be loaded,
     * which runs the static block.
     */
    public static void init() { }
}
