// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class BootstrapDaemon implements Daemon {

    private static final Logger log = Logger.getLogger(BootstrapDaemon.class.getName());
    private final BootstrapLoader loader;
    private final boolean privileged;
    private String bundleLocation;

    static {
        // force load slf4j to avoid other logging frameworks from initializing before it
        org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    public BootstrapDaemon() {
        this(new ApplicationLoader(Main.newOsgiFramework(), Main.newConfigModule()),
             Boolean.valueOf(System.getProperty("jdisc.privileged")));
    }

    BootstrapDaemon(BootstrapLoader loader, boolean privileged) {
        this.loader = loader;
        this.privileged = privileged;
    }

    BootstrapLoader loader() {
        return loader;
    }

    @Override
    public void init(DaemonContext context) throws Exception {
        String[] args = context.getArguments();
        if (args == null || args.length != 1 || args[0] == null) {
            throw new IllegalArgumentException("Expected 1 argument, got " + Arrays.toString(args) + ".");
        }
        bundleLocation = args[0];
        if (privileged) {
            log.finer("Initializing application with privileges.");
            loader.init(bundleLocation, true);
        }
    }

    @Override
    public void start() throws Exception {
        try {
            if (!privileged) {
                log.finer("Initializing application without privileges.");
                loader.init(bundleLocation, false);
            }
            loader.start();
        } catch (Exception e) {
            try {
                log.log(Level.SEVERE, "Failed starting container", e);
            }
            finally {
                Runtime.getRuntime().halt(1);
            }
        }
    }

    @Override
    public void stop() throws Exception {
        loader.stop();
    }

    @Override
    public void destroy() {
        loader.destroy();
    }

}
