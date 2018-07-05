// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.protect.Process;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Simon Thoresen Hult
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

    private static class WatchDog implements Runnable {
        final String name;
        final CountDownLatch complete;
        final long timeout;
        final TimeUnit timeUnit;
        WatchDog(String name, CountDownLatch complete, long timeout, TimeUnit timeUnit) {
            this.name = name;
            this.complete = complete;
            this.timeout = timeout;
            this.timeUnit = timeUnit;
        }
        @Override
        public void run() {
            boolean dumpStack;
            try {
                dumpStack = !complete.await(timeout, timeUnit);
            } catch (InterruptedException e) {
                return;
            }
            if (dumpStack) {
                log.warning("The watchdog for BootstrapDaemon." + name + " detected that it had not completed in "
                        + timeUnit.toMillis(timeout) + "ms. Dumping stack.");
                Process.dumpThreads();
            }
        }
    }
    private interface MyRunnable {
        void run() throws Exception;
    }
    private void startWithWatchDog(String name, long timeout, TimeUnit timeUnit, MyRunnable task) throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        Thread thread = new Thread(new WatchDog(name, complete, timeout, timeUnit), name);
        thread.setDaemon(true);
        thread.start();
        try {
            task.run();
        } catch (Exception e) {
            log.log(Level.WARNING, "Exception caught during BootstrapDaemon." + name, e);
            throw e;
        } catch (Error e) {
            log.log(Level.WARNING, "Error caught during BootstrapDaemon." + name, e);
            throw e;
        } catch (Throwable thrown) {
            log.log(Level.WARNING, "Throwable caught during BootstrapDaemon." + name, thrown);
        } finally {
            complete.countDown();
            thread.join();
        }
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
            startWithWatchDog("init", 60, TimeUnit.SECONDS, () -> loader.init(bundleLocation, true));
        }
    }

    @Override
    public void start() throws Exception {
        try {
            if (!privileged) {
                log.finer("Initializing application without privileges.");
                startWithWatchDog("init", 60, TimeUnit.SECONDS, () -> loader.init(bundleLocation, false));
            }
            startWithWatchDog("start", 60, TimeUnit.SECONDS, () -> loader.start());
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
        startWithWatchDog("stop", 60, TimeUnit.SECONDS, () -> loader.stop());
    }

    @Override
    public void destroy() {
        try {
            startWithWatchDog("destroy", 60, TimeUnit.SECONDS, () -> loader.destroy());
        } catch (Exception e) {
        }
    }

}
