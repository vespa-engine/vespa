package com.yahoo.log;

/**
 * Sets up Vespa logging. Call a setup method to set up this.
 *
 * @author baldersheim
 */
public class InitializeLog {
    static {
        LogSetup.initVespaLogging("Container");
    }

    /**
     * Do not delete this method even if it's empty.
     * Calling this methods forces this class to be loaded,
     * which runs the static block.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static void init() { }
}
