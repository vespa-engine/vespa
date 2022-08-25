// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.yolean.system.CatchSignals;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author arnej27959
 */
public class StandaloneMain {

    private static final Logger log = Logger.getLogger(StandaloneMain.class.getName());
    private final BootstrapLoader loader;

    static {
        // force load slf4j to avoid other logging frameworks from initializing before it
        org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    public StandaloneMain() {
        this.loader = new ApplicationLoader(Main.newOsgiFramework(), Main.newConfigModule());
    }

    public static void main(String [] args) {
        if (args == null || args.length != 1 || args[0] == null) {
            throw new IllegalArgumentException("Expected 1 argument, got " + Arrays.toString(args) + ".");
        }
        StandaloneMain me = new StandaloneMain();
        me.run(args[0]);
    }

    void run(String bundleLocation) {
        try {
            log.log(Level.FINE, "Initializing application without privileges.");
            setupSignalHandlers();
            loader.init(bundleLocation, false);
            loader.start();
            waitForShutdown();
            log.log(Level.INFO, "JDisc shutting down");
            loader.stop();
            log.log(Level.FINE, "Trying to clean up in a controlled manner.");
            loader.destroy();
            log.log(Level.FINE, "Stopped ok.");
            System.exit(0);
        } catch (Throwable e) {
            log.log(Level.SEVERE, "JDisc exiting: Throwable caught: ", e);
            System.exit(6);
        }
    }

    private final AtomicBoolean signalCaught = new AtomicBoolean(false);
    private void setupSignalHandlers() {
        CatchSignals.setup(signalCaught);
    }
    private void waitForShutdown() {
        synchronized (signalCaught) {
            while (!signalCaught.get()) {
                try {
                    signalCaught.wait();
                } catch (InterruptedException e) {
                    // empty
                }
            }
        }
    }

}
